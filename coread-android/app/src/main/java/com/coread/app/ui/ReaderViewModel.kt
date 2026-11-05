package com.coread.app.ui
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.paging.*
import com.coread.app.data.*
import com.coread.app.domain.DwellTracker
import com.coread.app.network.*
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
data class UiState(val group:Group=Group(),val groups:List<Group> = emptyList(),val signedIn:Boolean=false,val email:String="",val busy:Boolean=false,val notice:String="",val verifiedThrough:Int=-1,val quote:Int=-1,val reflections:List<Map<String,Any>> = emptyList(),val messages:List<Map<String,Any>> = emptyList(),val aiText:String="",val preview:String="",val invite:String="",val skipped:Set<Int> = emptySet(),val reflectionDay:Int=1,val bookReady:Boolean=false,val history:List<BookRecord> = emptyList(),val historyLoading:Boolean=false,val historyError:String="",val reward:Int?=null)
data class ImportStart(val index:Int,val title:String,val sample:List<String>)
data class ImportPreview(
 val group:String,val owner:String,val book:String,val uri:Uri,val name:String,
 val title:String,val paragraphCount:Int,val suggestedStart:Int,val fingerprint:String,val choices:List<ImportStart>
)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel class ReaderViewModel @Inject constructor(
 @ApplicationContext private val context:Context,private val db:CoReadDatabase,private val importer:BookImporter,
 val auth:AuthRepository,private val repository:GroupRepository,private val backend:Backend,private val ai:AiAssistantRepository,private val settings:Settings
):ViewModel() {
 private val mutable=MutableStateFlow(UiState(signedIn=auth.user!=null,email=auth.user?.email.orEmpty()))
 val state=mutable.asStateFlow()
 private val pendingImport=MutableStateFlow<ImportPreview?>(null)
 val importPreview=pendingImport.asStateFlow()
 private val book=MutableStateFlow("demo")
 val paragraphs=book.flatMapLatest { id -> Pager(PagingConfig(pageSize=30,prefetchDistance=10,maxSize=120)){db.reading().paging(id)}.flow }.cachedIn(viewModelScope)
 val sessions=db.reading().sessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val verifiedDemoParagraphs=db.reading().verifiedDemoParagraphs().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0)
 val onboardingPending=settings.onboardingPending
 fun finishOnboarding() { viewModelScope.launch { settings.finishOnboarding() } }
 private var tracker=DwellTracker()
 private var visible=emptyMap<Int,Int>()
 private var resumed=false
 private var interaction=SystemClock.elapsedRealtime()
 private var sessionMillis=0L
 private var sessionWords=0;private var sessionParagraphs=0
 private val sessionLock=Mutex()
 private var selectionJob:Job?=null
 private val progressSync=kotlinx.coroutines.sync.Mutex()
 private var historyJob:Job?=null
 private var groupJob:Job?=null;private var messageJob:Job?=null;private var reflectionJob:Job?=null
 private var pendingInvite:String?=null;private var messageLimit=50L
 val uid get()=auth.user?.uid ?: "demo"
 init {
  viewModelScope.launch(Dispatchers.IO) {
   if(db.reading().count("demo")==0) context.assets.open("demo.json").use { importer.import("demo",it,false) }
   if(book.value=="demo" && state.value.group.id=="demo") {
    refreshProgress()
    if(book.value=="demo" && state.value.group.id=="demo") mutable.update { it.copy(bookReady=true) }
   }
  }
  if(auth.user!=null) observeGroups()
  viewModelScope.launch {
   var last=SystemClock.elapsedRealtime();var syncAt=last
   while(isActive) {
    delay(250);val now=SystemClock.elapsedRealtime()
    val visibleSnapshot=visible;val readingBook=book.value
    val elapsed=(now-last).takeIf { it<=1000 } ?: 0
    val active=resumed && now-interaction<90000
    val ready=tracker.tick(visibleSnapshot,elapsed,active);last=now
    if(active && visibleSnapshot.isNotEmpty()) sessionMillis+=elapsed
    if(ready.isNotEmpty()) {
     sessionLock.withLock {
      ready.forEach { index ->
       if(db.reading().verify(readingBook,index)>0) {
        sessionParagraphs++
        sessionWords+=visibleSnapshot[index] ?: 0
       }
      }
     }
     if(book.value==readingBook) mutable.update { it.copy(skipped=it.skipped-ready) }
     refreshProgress()
    }
    if(now-syncAt>=30000) { syncAt=now;syncQuietly() }
   }
  }
 }
 private fun run(block:suspend ()->Unit) { viewModelScope.launch { mutable.update { it.copy(busy=true,notice="") };try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { mutable.update { it.copy(notice=message(e)) } } finally { mutable.update { it.copy(busy=false) } } } }
 private fun message(e:Exception):String {
  if(e is retrofit2.HttpException) return try { org.json.JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("detail",e.message()) } catch(_:Exception) { "Service error "+e.code()+". Try again." }
  return e.localizedMessage ?: "Something went wrong. Try again."
 }
 fun dismiss() { mutable.update { it.copy(notice="") } }
 fun touch() { interaction=SystemClock.elapsedRealtime() }
 fun foreground(value:Boolean) { resumed=value;if(value) touch() else { viewModelScope.launch { saveSession();syncQuietly() } } }
 fun visible(items:Map<Int,Int>) {
  val left=visible.keys-items.keys
  if(left.isNotEmpty()) mutable.update { it.copy(skipped=it.skipped+left.filter { index -> index>it.verifiedThrough }) }
  visible=items
 }
 fun enteringReader() { touch() }
 fun leavingReader() { visible=emptyMap();viewModelScope.launch { saveSession();syncQuietly() } }
 private suspend fun refreshProgress(syncOnUnlock:Boolean=true) {
  val g=state.value.group;val currentBook=book.value
  val through=(db.reading().firstUnread(currentBook,g.start) ?: db.reading().count(currentBook))-1
  val quote=db.reading().quote(currentBook,g.start,g.end) ?: -1
  if(book.value!=currentBook || state.value.group!=g) return
  val wasUnlocked=state.value.verifiedThrough>=g.end
  mutable.update { it.copy(verifiedThrough=through,quote=quote) }
  if(g.id!="demo" && !wasUnlocked && through>=g.end) { if(syncOnUnlock) syncQuietly();observeReflections() }
 }
 private suspend fun syncQuietly(failOnError:Boolean=false) {
  val s=state.value
  if(s.group.id=="demo" || !s.signedIn || !s.bookReady || s.group.bookId.isBlank() || s.group.maintenance.isNotBlank()) return
  progressSync.lock()
  val readingBook=book.value
  try {
   val merged=repository.sync(s.group.id,s.verifiedThrough,s.group.bookId)
   if(book.value==readingBook && state.value.bookReady) {
    if(merged>s.verifiedThrough) { db.reading().restoreVerified(readingBook,merged);refreshProgress(false) }
    mutable.update { if(it.notice.startsWith("Progress saved locally. Sync will retry:")) it.copy(notice="") else it }
   }
  } catch(e:CancellationException) { throw e }
  catch(e:Exception) { if(failOnError) throw e; if(book.value==readingBook && state.value.bookReady) mutable.update { it.copy(notice="Progress saved locally. Sync will retry: "+message(e)) } }
  finally { progressSync.unlock() }
 }
 private suspend fun saveSession() = sessionLock.withLock {
  val end=System.currentTimeMillis();val elapsed=sessionMillis;val count=sessionParagraphs;val words=sessionWords
  sessionParagraphs=0;sessionWords=0;sessionMillis=0
  if(elapsed>0) db.reading().log(ReadingSessionLogEntity(startTime=end-elapsed,endTime=end,paragraphsRead=count,averageWpm=words*60000.0/elapsed))
 }
 fun highlight(index:Int)=run { withContext(Dispatchers.IO) { db.reading().toggleHighlight(book.value,index);refreshProgress() };touch() }
 fun google(activity:Activity)=run {
  try { auth.google(activity) } catch(_:GetCredentialCancellationException) { return@run }
  signedIn()
 }
 fun email(address:String)=run {
  auth.email(address.trim())
  mutable.update { it.copy(notice="Sign-in link sent. Check your inbox and spam folder, then open the link on this device. If it opens in a browser, copy the original link from the email and paste it here.") }
 }
 fun finishEmailSignIn(address:String,link:String)=run {
  require(auth.complete(link.trim(),address)) { "Copy the original sign-in link from the email and try again." }
  signedIn()
  mutable.update { it.copy(notice="Signed in.") }
 }
 fun link(uri:Uri)=run {
  uri.getQueryParameter("groupId")?.let { pendingInvite=it;settings.pendingGroup(it) }
  val nested=uri.getQueryParameter("link")
  if(auth.complete(uri.toString()) || (nested!=null && auth.complete(nested))) {
   signedIn()
   mutable.update { it.copy(notice="Signed in.") }
  } else if(auth.user!=null) {
   joinPending()
  } else {
   mutable.update { it.copy(notice="Sign in with the invited email to join this group.") }
  }
 }
 private suspend fun signedIn() { mutable.update { it.copy(signedIn=true,email=auth.user?.email.orEmpty()) };observeGroups();joinPending() }
 private suspend fun joinPending() { (pendingInvite ?: settings.pendingGroup.first().ifBlank { null })?.let { backend.join(it);pendingInvite=null;settings.pendingGroup("");mutable.update { s -> s.copy(notice="Invitation accepted.") } } }
 fun join(id:String)=run { backend.join(id.trim());mutable.update { it.copy(notice="Joined your reading group.") } }
 fun signOut() { historyJob?.cancel();selectionJob?.cancel();groupJob?.cancel();messageJob?.cancel();reflectionJob?.cancel();auth.signOut();book.value="demo";tracker=DwellTracker();mutable.value=UiState(bookReady=true);viewModelScope.launch { refreshProgress() } }
 private fun observeGroups() {
  groupJob?.cancel()
  groupJob=viewModelScope.launch {
   repository.groups().catch { e -> mutable.update { it.copy(notice=e.message.orEmpty()) } }.collect { groups ->
    mutable.update { it.copy(groups=groups) }
    val selected=groups.firstOrNull { it.id==state.value.group.id } ?: groups.firstOrNull()
    if(selected!=null && selected!=state.value.group) select(selected)
    else if(selected==null && state.value.group.id!="demo") {
     historyJob?.cancel();selectionJob?.cancel();messageJob?.cancel();reflectionJob?.cancel();book.value="demo";tracker=DwellTracker();visible=emptyMap()
     mutable.update { it.copy(group=Group(),reflectionDay=1,verifiedThrough=-1,reflections=emptyList(),messages=emptyList(),bookReady=true,history=emptyList(),historyLoading=false,historyError="",reward=null) }
     refreshProgress()
    }
   }
  }
 }
 fun select(g:Group) {
  selectionJob?.cancel()
  selectionJob=viewModelScope.launch {
   mutable.update { it.copy(busy=true,notice="") }
   try { selectGroup(g) } catch(e:CancellationException) { throw e } catch(e:Exception) { mutable.update { it.copy(notice=message(e)) } } finally { mutable.update { it.copy(busy=false) } }
  }
 }
 private suspend fun selectGroup(g:Group) {
  if(state.value.group.id==g.id && state.value.group.bookId==g.bookId && state.value.bookReady) {
   val old=state.value.group
   mutable.update { it.copy(group=g,reflectionDay=if(old.day!=g.day) g.day else it.reflectionDay) }
   refreshProgress();observeReflections();celebrate(g);return
  }
  saveSession();if(state.value.group.id!=g.id || state.value.group.bookId==g.bookId) syncQuietly();messageJob?.cancel();reflectionJob?.cancel()
  mutable.update { it.copy(history=if(it.group.id==g.id) it.history else emptyList(),reward=null,bookReady=false,group=g,reflectionDay=g.day,verifiedThrough=g.start-1,quote=-1,reflections=emptyList(),messages=emptyList(),skipped=emptySet()) }
  observeHistory(g.id)
  tracker=DwellTracker();visible=emptyMap();val selectedBook=uid+":"+g.id+":"+g.bookId;book.value=selectedBook
  if(g.bookId.isNotBlank() && db.reading().count(selectedBook)==0) withContext(Dispatchers.IO) { backend.book(g.id,g.bookId).use { importer.import(selectedBook,it.byteStream()) } }
  if(g.bookId.isNotBlank()) db.reading().restoreVerified(selectedBook,repository.position(g.id))
  refreshProgress();mutable.update { it.copy(bookReady=true) };syncQuietly();observeMessages();observeReflections();celebrate(g)
  try { repository.token(g.id,FirebaseMessaging.getInstance().token.await()) } catch(_:Exception) { }
 }
 private suspend fun celebrate(g:Group) {
  if(state.value.group.id!=g.id) return
  val badge=settings.celebrateBadge(uid+":"+g.id,g.bestStreak)
  if(badge!=null && state.value.group.id==g.id) mutable.update { it.copy(reward=badge) }
 }
 fun dismissReward() { mutable.update { it.copy(reward=null) } }
 private fun observeHistory(group:String) {
  historyJob?.cancel()
  if(group=="demo") return
  mutable.update { it.copy(historyLoading=true,historyError="") }
  historyJob=viewModelScope.launch {
   repository.history(group).catch { mutable.update { it.copy(historyLoading=false,historyError="Book history could not load. Reopen the circle to try again.") } }.collect { books ->
    if(state.value.group.id==group) mutable.update { it.copy(history=books,historyLoading=false,historyError="") }
   }
  }
 }
 private fun observeMessages() {
  messageJob?.cancel();val group=state.value.group.id;if(group=="demo") return
  messageJob=viewModelScope.launch { repository.messages(group,messageLimit).catch { e -> mutable.update { it.copy(notice=e.message.orEmpty()) } }.collect { m -> mutable.update { it.copy(messages=m) } } }
 }
 private fun observeReflections() {
  reflectionJob?.cancel();val s=state.value;if(s.group.id=="demo") return
  reflectionJob=viewModelScope.launch { repository.reflections(s.group.id,s.reflectionDay,if(s.verifiedThrough>=s.group.end || s.reflectionDay<s.group.day) s.verifiedThrough else -1).catch { e -> mutable.update { it.copy(notice=e.message.orEmpty()) } }.collect { entries -> mutable.update { it.copy(reflections=entries) } } }
 }
 fun reflectionDay(day:Int) { mutable.update { it.copy(reflectionDay=day.coerceIn(it.group.firstDay,it.group.day)) };observeReflections() }
 fun moreMessages() { messageLimit+=50;observeMessages() }
 fun create(name:String,email:String)=run {
  val r=backend.create(name,email)
  mutable.update { it.copy(invite=r["inviteUrl"]!!.jsonPrimitive.content,notice=if(r["emailSent"]?.jsonPrimitive?.content=="true") "Invitation emailed." else "Circle created. Share the invite link below.") }
 }
 fun readingKey(g:Group=state.value.group)=if(g.id=="demo") "demo" else uid+":"+g.id+":"+g.bookId
 suspend fun readingPosition(key:String)=settings.readingPosition(key)
 fun saveReadingPosition(key:String,index:Int,offset:Int) { viewModelScope.launch { settings.readingPosition(key,index,offset) } }
 fun manage(kind:String,name:String="")=run {
  val g=state.value.group
  require(g.ownerId==uid) { "Only the circle creator can manage this circle" }
  if(kind=="remove_book") { require(state.value.bookReady) { "Wait for your book to finish loading." };syncQuietly(true) }
  backend.manage(g.id,kind,name,g.bookId)
  mutable.update { it.copy(notice=when(kind) { "remove_book" -> "Book removed. You can import its replacement."; "delete_circle" -> "Circle deleted."; "skip_preliminaries" -> "Daily reading now starts at the first chapter.";else -> "Circle updated." }) }
 }
 fun upload(uri:Uri)=run {
  val g=state.value.group
  val owner=uid
  require(g.id!="demo" && g.ownerId==owner && g.bookId.isBlank()) { "Select a circle you own without an active book" }
  var name="book.pdf"
  context.contentResolver.query(uri,null,null,null,null)?.use { c -> if(c.moveToFirst()) { val i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0) name=c.getString(i) } }
  val bookId=UUID.randomUUID().toString()
  val preview=requireNotNull(context.contentResolver.openInputStream(uri)).use { backend.upload(g.id,bookId,name,it) }
  if(state.value.group.id==g.id && uid==owner) pendingImport.value=ImportPreview(
   group=g.id,owner=owner,book=bookId,uri=uri,name=name,
   title=preview.getValue("title").jsonPrimitive.content,
   paragraphCount=preview.getValue("paragraphCount").jsonPrimitive.int,
   suggestedStart=preview.getValue("suggestedStart").jsonPrimitive.int,
   fingerprint=preview.getValue("fingerprint").jsonPrimitive.content,
   choices=preview.getValue("choices").jsonArray.map {
    val choice=it.jsonObject
    ImportStart(choice.getValue("index").jsonPrimitive.int,choice.getValue("title").jsonPrimitive.content,
     choice.getValue("sample").jsonArray.map { text -> text.jsonPrimitive.content })
   }
  )
 }
 fun dismissImport() { pendingImport.value=null;dismiss() }
 fun confirmImport(start:Int)=run {
  val draft=pendingImport.value ?: return@run
  val g=state.value.group
  require(g.id==draft.group && uid==draft.owner && g.ownerId==uid && g.bookId.isBlank()) { "Return to the circle that started this import" }
  require(start in 0 until draft.paragraphCount) { "Choose a paragraph within this book" }
  requireNotNull(context.contentResolver.openInputStream(draft.uri)).use {
   backend.upload(draft.group,draft.book,draft.name,it,start,draft.fingerprint)
  }
  pendingImport.value=null
  mutable.update { it.copy(notice="Converted. Downloading your local reading copy…") }
 }
 fun action(kind:String,text:String="",target:String="",value:Int=0,mode:String="custom")=run {
  val s=state.value
  if(s.group.id=="demo") {
   if(kind=="commentary") {
    require(s.verifiedThrough>=s.group.end && s.quote>=0 && text.isNotBlank()) { "Finish the range and long-press a quote first" }
    mutable.update { it.copy(reflections=listOf(mapOf("uid" to "demo","text" to text,"status" to "PENDING_REVIEW","quoteIndex" to s.quote)),notice="Demo reflection saved for this session.") }
   } else mutable.update { it.copy(notice="Sign in and create a group to use partner features.") }
  } else {
   syncQuietly();backend.action(s.group.id,mapOf("kind" to kind,"text" to text,"target" to (if(kind in listOf("accept","reject","cancel")) s.group.pending?.get("id")?.toString().orEmpty() else target),"value" to value,"mode" to mode,"quoteIndex" to (if(s.reflectionDay<s.group.day) (s.reflections.firstOrNull { it["uid"]==uid }?.get("quoteIndex") as? Long)?.toInt() ?: -1 else s.quote),"dayNumber" to s.reflectionDay));mutable.update { it.copy(notice="Saved.") }
  }
 }
 fun preview(mode:String,value:Int)=run {
  val s=state.value;val dao=db.reading();val count=dao.count(book.value)
  val end=when(mode) {
   "words" -> { var lo=s.group.start;var hi=count-1;while(lo<hi) { val mid=(lo+hi)/2;if((dao.words(book.value,s.group.start,mid) ?: 0)>=value) hi=mid else lo=mid+1 };lo }
   "chapters" -> dao.chapters(book.value).filter { it.startParagraphIndex>s.group.start }.getOrNull(value-1)?.startParagraphIndex?.minus(1) ?: count-1
   else -> (s.group.start+value-1).coerceAtMost(count-1)
  }
  mutable.update { it.copy(preview="Ends at paragraph "+(end+1)+": "+dao.paragraph(book.value,end)?.text.orEmpty().take(220)) }
 }
 fun assistant(kind:String)=run {
  require(state.value.group.id!="demo") { "Connect a shared circle to use AI" };val id=state.value.group.id
  val result=when(kind) { "summary" -> ai.summary(id);"mediate" -> ai.mediate(id,state.value.reflectionDay);else -> ai.questions(id) };mutable.update { it.copy(aiText=result) }
 }
 fun nudge()=run { require(state.value.group.id!="demo");backend.nudge(state.value.group.id);mutable.update { it.copy(notice="Nudge sent.") } }
 fun send(text:String,paragraph:String)=run { require(state.value.group.id!="demo") { "Connect a circle to chat with your partner" };require(text.isNotBlank());repository.message(state.value.group.id,text,paragraph.toIntOrNull()?.minus(1) ?: -1) }
}
