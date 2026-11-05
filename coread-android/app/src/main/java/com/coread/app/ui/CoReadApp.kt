package com.coread.app.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import java.time.*
import com.coread.app.domain.*
import com.coread.app.data.BookRecord
import androidx.compose.animation.core.animateFloatAsState
import java.time.format.DateTimeFormatter
private val Ink=Color(0xFF283D31)
private val Clay=Color(0xFFA44D31)
private val Cream=Color(0xFFFFF8F1)
private val Pale=Color(0xFFF1E8DC)
@Composable fun CoReadApp(vm:ReaderViewModel) {
 val s by vm.state.collectAsStateWithLifecycle()
 val importPreview by vm.importPreview.collectAsStateWithLifecycle()
 var tab by remember { mutableIntStateOf(0) }
 var moreSection by remember { mutableStateOf("more") }
 var onboardingStep by rememberSaveable { mutableIntStateOf(0) }
 val onboardingPending by vm.onboardingPending.collectAsStateWithLifecycle(initialValue=false)
 var onboardingDismissed by remember { mutableStateOf(false) }
 var showReadingHelp by rememberSaveable { mutableStateOf(false) }
 val closeReadingHelp = {
  showReadingHelp=false
  onboardingStep=0
  if(onboardingPending) {
   onboardingDismissed=true
   vm.finishOnboarding()
  }
 }
 val dark = isSystemInDarkTheme()
 val colors = if(dark) darkColorScheme(primary=Color(0xFFE59A78),secondary=Color(0xFFD8E8D0),background=Color(0xFF17211B),surface=Color(0xFF17211B),surfaceVariant=Color(0xFF2A382E)) else lightColorScheme(primary=Clay,secondary=Ink,background=Cream,surface=Cream,surfaceVariant=Pale)
 MaterialTheme(colorScheme=colors) {
  importPreview?.takeIf { it.group==s.group.id && it.owner==vm.uid && s.group.bookId.isBlank() }?.let { preview ->
   key(preview.book) { BookImportDialog(preview,s.busy,s.notice,vm::confirmImport,vm::dismissImport) }
  }
  if(showReadingHelp || (onboardingPending && !onboardingDismissed && s.group.id=="demo")) {
   val title=when(onboardingStep) { 0 -> "A small reading ritual";1 -> "Read at a realistic pace";else -> "Reflect and grow together" }
   val text=when(onboardingStep) {
    0 -> "CoRead helps two people read the same book, one daily range at a time."
    1 -> "Your progress is verified while passages remain visible. Long-press a passage when it gives you something to remember."
    else -> "Finish today's range, save a quote, and share a reflection. Your partner's response unlocks when you are both ready."
   }
   AlertDialog(onDismissRequest=closeReadingHelp,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(text);Text((onboardingStep+1).toString()+" of 3",style=MaterialTheme.typography.labelSmall,color=Clay)}},confirmButton={
    TextButton(onClick={if(onboardingStep==2) closeReadingHelp() else onboardingStep++}){Text(if(onboardingStep==2) "Done" else "Next")}
   },dismissButton={TextButton(onClick=closeReadingHelp){Text(if(showReadingHelp) "Close" else "Skip")}})
  }
  Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={ NavigationBar(containerColor=MaterialTheme.colorScheme.background) {
   val destinations=listOf(
    Triple("Home",Icons.Default.Home,"Home"),
    Triple("Read",Icons.Default.AutoStories,"Read"),
    Triple("Reflect",Icons.Default.Lightbulb,"Reflect"),
    Triple("Chat",Icons.Default.Chat,"Chat"),
    Triple("More",Icons.Default.MoreHoriz,"More")
   )
   destinations.forEachIndexed { i,(label,icon,description) ->
    NavigationBarItem(selected=tab==i,onClick={tab=i;vm.touch()},icon={Icon(icon,contentDescription=description)},label={Text(label,fontSize=11.sp)})
   }
  } }) { padding ->
   Column(Modifier.fillMaxSize().padding(padding).pointerInput(Unit) {
    awaitPointerEventScope { while(true) { val event=awaitPointerEvent(PointerEventPass.Initial);if(event.changes.any { it.pressed }) vm.touch() } }
   }) {
    Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
     Text("CoRead",fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=27.sp,color=MaterialTheme.colorScheme.onBackground)
     Text(if(s.group.id=="demo") "LOCAL DEMO" else "DAY "+s.group.day,style=MaterialTheme.typography.labelMedium,color=Clay)
    }
    s.reward?.let { days -> ReadingRewards.badges.firstOrNull { it.days==days }?.let { badge ->
     AlertDialog(onDismissRequest=vm::dismissReward,title={Text(badge.symbol+"  "+badge.name)},text={Text("You earned the "+badge.days+"-day streak badge together. Your badge stays with this circle, even when you start a new streak.")},confirmButton={TextButton(onClick=vm::dismissReward){Text("Keep reading")}})
    } }
    if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(s.notice.isNotBlank()) Surface(color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { Text(s.notice,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick=vm::dismiss){Text("Dismiss")} } }
    when(tab) { 0 -> Home(vm,s,onBookshelf={tab=4;moreSection="books"},onRead={tab=1},onHelp={showReadingHelp=true});1 -> key(vm.uid,s.group.id,s.group.bookId) { Reader(vm,s) };2 -> Reflections(vm,s);3 -> Chat(vm,s);else -> More(vm,s,moreSection,{moreSection=it}) }
   }
  }
 }
}
@Composable private fun BookImportDialog(
 preview:ImportPreview,busy:Boolean,notice:String,onConfirm:(Int)->Unit,onCancel:()->Unit
) {
 var paragraph by rememberSaveable { mutableStateOf((preview.suggestedStart+1).toString()) }
 val start=paragraph.toIntOrNull()?.takeIf { it in 1..preview.paragraphCount }?.minus(1)
 val selected=preview.choices.firstOrNull { it.index==start }
 AlertDialog(
  onDismissRequest={if(!busy) onCancel()},
  title={Text("Review book import")},
  text={
   Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Text(preview.title,style=MaterialTheme.typography.titleMedium)
    Text("Choose where daily reading starts. Earlier paragraphs stay available in the reader.")
    Field(paragraph,{paragraph=it},"Starting paragraph (1–${preview.paragraphCount})")
    selected?.sample?.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
    Text("Detected starting points",style=MaterialTheme.typography.titleSmall)
    preview.choices.forEach { choice ->
     OutlinedButton(enabled=!busy,onClick={paragraph=(choice.index+1).toString()}) {
      val suggested=if(choice.index==preview.suggestedStart) " · Suggested" else ""
      Text("${choice.index+1}. ${choice.title}$suggested")
     }
    }
    if(notice.isNotBlank()) Text(notice)
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
   }
  },
  confirmButton={TextButton(enabled=!busy && start!=null,onClick={start?.let(onConfirm)}){Text("Share book")}},
  dismissButton={TextButton(enabled=!busy,onClick=onCancel){Text("Cancel")}}
 )
}
@Composable private fun Page(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content) }
@Composable private fun Heading(title:String,subtitle:String="") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,fontFamily=FontFamily.Serif,fontSize=32.sp,color=MaterialTheme.colorScheme.onBackground,lineHeight=38.sp);if(subtitle.isNotBlank()) Text(subtitle,color=MaterialTheme.colorScheme.onBackground.copy(alpha=.7f),lineHeight=23.sp) } }
@Composable private fun Panel(content:@Composable ColumnScope.()->Unit) { Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) } }
@Composable private fun Field(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier) { OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),minLines=1) }
@Composable private fun Home(vm:ReaderViewModel,s:UiState,onBookshelf:()->Unit,onRead:()->Unit,onHelp:()->Unit) {
 var showSignIn by rememberSaveable { mutableStateOf(false) }
 LaunchedEffect(s.signedIn) { if(s.signedIn) showSignIn=false }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::upload) }
 var management by remember(s.group.id) { mutableStateOf("") };var circleName by remember(s.group.id,s.group.name) { mutableStateOf(s.group.name) }
 var name by remember { mutableStateOf("") };var email by remember { mutableStateOf("") };var groupId by remember { mutableStateOf("") };var pace by remember { mutableStateOf(false) }
 Page {
  Heading("Good books.\nBetter together.","Make room for a small, shared reading ritual.")
  if(s.group.id=="demo") Panel {
   Text("Start here",style=MaterialTheme.typography.labelLarge,color=Clay)
   Text("Try the sample book, then create or join a circle when you are ready to read together.")
   OutlinedButton(onClick=onHelp){Text("How it works")}
  }
  if(s.groups.isNotEmpty()) { Text("YOUR CIRCLES",style=MaterialTheme.typography.labelMedium,color=Clay);s.groups.forEach { g -> FilterChip(selected=s.group.id==g.id,onClick={vm.select(g)},label={Text(g.name)}) } }
  if(s.group.id!="demo") StreakCard(s)
  Panel {
   Text(if(s.group.id=="demo") "SAMPLE READING" else "TODAY'S READING",style=MaterialTheme.typography.labelMedium,color=Clay)
   Text(s.group.title,fontFamily=FontFamily.Serif,fontSize=26.sp,color=MaterialTheme.colorScheme.onBackground)
   Text(if(s.group.completed) "Book complete. Keep the conversation going." else "Paragraphs "+(s.group.start+1)+"–"+(s.group.end+1))
   val range=(s.group.end-s.group.start+1).coerceAtLeast(1);val done=(s.verifiedThrough-s.group.start+1).coerceIn(0,range)
   LinearProgressIndicator(progress={done.toFloat()/range},modifier=Modifier.fillMaxWidth())
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
    Text(done.toString()+" of "+range+" paragraphs verified",style=MaterialTheme.typography.bodySmall)
    Text(if(done==0) "Ready when you are" else "${(range-done).coerceAtLeast(0)} left",style=MaterialTheme.typography.bodySmall,color=Clay)
   }
   Button(onClick=onRead,modifier=Modifier.fillMaxWidth()){Text(if(done==0) "Start today's reading" else "Continue reading")}
   TextButton(onClick=onBookshelf){Text("View your bookshelf")}
  }
  if(!s.signedIn) Panel {
   Button(onClick={showSignIn=true},modifier=Modifier.fillMaxWidth()) {
    Text("Add a book and read with your partner")
   }
  }
  s.group.pending?.let { p -> Panel {
   Text(p["kind"].toString()+" request",fontWeight=FontWeight.Bold)
   Text(if(p["requestedBy"]==vm.uid) "Waiting for your partner." else "Your partner wants to adjust your reading ritual.")
   if(p["requestedBy"]==vm.uid) TextButton(enabled=!s.busy,onClick={vm.action("cancel")}){Text("Cancel request")}
   if(p["requestedBy"]!=vm.uid) Row { Button(onClick={vm.action("accept")}){Text("Approve")};TextButton(onClick={vm.action("reject")}){Text("Decline")} }
  } }
  if(s.group.id!="demo") {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={pace=true}){Text("Adjust pace")};OutlinedButton(onClick={vm.action("pause")}){Text("Pause a day")} }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { TextButton(onClick=vm::nudge){Text("Nudge partner")};TextButton(onClick={vm.action("evaluate")}){Text("Complete day")} }
   if(s.group.bookId.isBlank() && s.group.ownerId==vm.uid) Button(onClick={picker.launch(arrayOf("application/pdf","application/epub+zip"))}){Text("Import PDF or EPUB")}
  }
  if(s.group.id!="demo" && s.group.ownerId==vm.uid) Panel {
   Text("Manage your circle",fontFamily=FontFamily.Serif,fontSize=23.sp)
   TextButton(enabled=!s.busy,onClick={management="rename"}){Text("Edit circle name")}
   if(s.group.bookId.isNotBlank()) TextButton(enabled=!s.busy,onClick={management="remove_book"}){Text("Remove or replace book")}
   if(s.group.bookId.isNotBlank() && s.group.day==s.group.firstDay) TextButton(enabled=!s.busy,onClick={management="skip_preliminaries"}){Text("Start daily goals at Chapter 1")}
   TextButton(enabled=!s.busy,onClick={management="delete_circle"}){Text("Delete circle")}
   if(s.group.maintenance.isNotBlank()) Text("An earlier operation needs to finish. Retry removing the book or deleting the circle.",style=MaterialTheme.typography.bodySmall)
  }
  if(s.signedIn) Panel {
   Text("Start a reading circle",fontFamily=FontFamily.Serif,fontSize=23.sp)
   Field(name,{name=it},"Circle name");Field(email,{email=it},"Partner's email")
   Button(enabled=name.isNotBlank()&&email.contains('@')&&!s.busy,onClick={vm.create(name,email)}){Text("Create & invite")}
   if(s.invite.isNotBlank()) SelectionContainer { Text(s.invite,color=Clay) }
   Field(groupId,{groupId=it},"Have an invitation? Paste group ID")
   TextButton(onClick={vm.join(groupId)},enabled=groupId.isNotBlank()){Text("Join circle")}
  }
 }
 if(management.isNotBlank()) AlertDialog(
  onDismissRequest={management=""},
  title={Text(when(management) { "rename" -> "Edit circle";"skip_preliminaries" -> "Skip preliminary pages?";"remove_book" -> "Remove this book?";else -> "Delete this circle?" })},
  text={if(management=="rename") Field(circleName,{circleName=it},"Circle name") else Text(if(management=="skip_preliminaries") "Move the first daily goal to the first detected chapter for both readers. Preliminary pages stay available. This is available before either reader submits the first reflection." else if(management=="remove_book") "This removes the shared book and resets both readers' progress. Then you can import another PDF or EPUB. Earlier reflections are kept separately; chat stays in the circle." else "This permanently deletes the circle, book, reflections and messages for both partners.")},
  confirmButton={TextButton(enabled=!s.busy && (management!="rename" || circleName.isNotBlank()),onClick={vm.manage(management,circleName);management=""}){Text(if(management=="rename") "Save" else if(management=="skip_preliminaries") "Update goals" else "Delete")}},
  dismissButton={TextButton(onClick={management=""}){Text("Cancel")}}
 )
 if(pace) PaceDialog(vm){pace=false}
 if(showSignIn && !s.signedIn) AlertDialog(
  onDismissRequest={showSignIn=false},
  title={Text("Sign in to add a book")},
  text={
   Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("Sign in or create an account, then create or join a reading circle to share a book.")
    SignInOptions(vm,s)
   }
  },
  confirmButton={TextButton(onClick={showSignIn=false}){Text("Close")}}
 )
}
@Composable private fun More(vm:ReaderViewModel,s:UiState,section:String,onSection:(String)->Unit) {
 var showHelp by rememberSaveable { mutableStateOf(true) }
 if(section=="books") {
  Insights(vm,s,onBack={onSection("more")})
  return
 }
 if(section=="settings") {
  Settings(vm,s,onBack={onSection("more")})
  return
 }
 Page {
  Heading("More","Your books, settings, and help in one place.")
  Panel {
   Text("YOUR READING SPACE",style=MaterialTheme.typography.labelMedium,color=Clay)
   Text("Keep your circle, book history, and personal reading habits close at hand.")
   OutlinedButton(onClick={onSection("books")},modifier=Modifier.fillMaxWidth()){Text("Bookshelf & insights")}
  }
  if(showHelp) Panel {
   Text("NEED A HAND?",style=MaterialTheme.typography.labelMedium,color=Clay)
   Text("Verified reading rewards time spent with the book. Long-press a passage to save a quote, and your reflection unlocks when today's range is complete.")
   TextButton(onClick={showHelp=false},modifier=Modifier.fillMaxWidth()){Text("Got it")}
  }
  OutlinedButton(onClick={onSection("settings")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Settings,contentDescription=null);Spacer(Modifier.width(8.dp));Text("Settings")}
 }
}
@Composable private fun PaceDialog(vm:ReaderViewModel,onClose:()->Unit) {
 var mode by remember { mutableStateOf("words") };var value by remember { mutableFloatStateOf(500f) }
 LaunchedEffect(mode,value.toInt()) { kotlinx.coroutines.delay(180);vm.preview(mode,value.toInt()) }
 val s by vm.state.collectAsStateWithLifecycle()
 AlertDialog(onDismissRequest=onClose,title={Text("Find your pace")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row { listOf("chapters","words","custom").forEach { key -> FilterChip(selected=mode==key,onClick={mode=key;value=if(key=="words") 500f else 1f},label={Text(if(key=="custom") "Paragraphs" else key,fontSize=11.sp)}) } }
  Text(value.toInt().toString()+" "+if(mode=="custom") "paragraphs" else mode)
  Slider(value=value,onValueChange={value=it},valueRange=1f..if(mode=="words") 5000f else 50f)
  Text(s.preview,style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(onClick={vm.action("pace",value=value.toInt(),mode=mode);onClose()}){Text("Ask partner")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Reader(vm:ReaderViewModel,s:UiState) {
 val paragraphs=vm.paragraphs.collectAsLazyPagingItems();val list=rememberLazyListState()
 var initialized by remember(s.group.id,s.group.bookId) { mutableStateOf(false) }
 val readingKey=vm.readingKey(s.group)
 LaunchedEffect(paragraphs.itemCount,s.bookReady,paragraphs.itemSnapshotList.items.firstOrNull()?.bookId) {
  if(!initialized && s.bookReady && paragraphs.itemCount>s.group.start && paragraphs.itemSnapshotList.items.firstOrNull()?.bookId==readingKey) {
   val saved=vm.readingPosition(readingKey)
   list.scrollToItem((saved?.first ?: (s.verifiedThrough+1).coerceIn(s.group.start,s.group.end)).coerceIn(0,paragraphs.itemCount-1),saved?.second ?: 0)
   initialized=true
  }
 }
 LaunchedEffect(list,initialized) {
  if(initialized) snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.distinctUntilChanged().collectLatest {
   kotlinx.coroutines.delay(300)
   vm.saveReadingPosition(readingKey,it.first,it.second)
  }
 }
 LaunchedEffect(list,paragraphs,s.group.bookId,initialized) {
  if(initialized) snapshotFlow { list.layoutInfo.visibleItemsInfo.map { item ->
   val p=paragraphs.peek(item.index);val layout=list.layoutInfo
   val shown=(minOf(item.offset+item.size,layout.viewportEndOffset)-maxOf(item.offset,layout.viewportStartOffset)).coerceAtLeast(0)
   if(p!=null && shown>=minOf(item.size*.6f,(layout.viewportEndOffset-layout.viewportStartOffset)*.6f)) p.index to p.wordCount else null
  }.filterNotNull().toMap() }.distinctUntilChanged().collect(vm::visible)
 }
 DisposableEffect(readingKey) {
  vm.enteringReader()
  onDispose {
   if(initialized) vm.saveReadingPosition(readingKey,list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset)
   vm.leavingReader()
  }
 }
 Column {
  val range=(s.group.end-s.group.start+1).coerceAtLeast(1)
  val done=(s.verifiedThrough-s.group.start+1).coerceIn(0,range)
  Surface(color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
     Text("Today's reading",style=MaterialTheme.typography.labelLarge)
     Text("$done of $range verified",style=MaterialTheme.typography.labelMedium,color=Clay)
    }
    LinearProgressIndicator(progress={done.toFloat()/range},modifier=Modifier.fillMaxWidth())
    Text("Long-press a passage to save your quote.",style=MaterialTheme.typography.bodySmall)
   }
  }
  if(!s.bookReady) Text("Preparing your reading copy…",Modifier.padding(22.dp),style=MaterialTheme.typography.bodyMedium)
  LazyColumn(state=list,contentPadding=PaddingValues(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   items(paragraphs.itemCount,key=paragraphs.itemKey { it.bookId+":"+it.index }) { position -> paragraphs[position]?.let { p ->
    val skipped=p.index in s.skipped && !p.verified
    val border=if(p.highlighted) Clay else if(skipped) Color(0xFFD39534) else if(p.verified) Color(0xFF7D9B78) else Color.Transparent
    Column(Modifier.fillMaxWidth().border(1.dp,border,RoundedCornerShape(14.dp)).background(if(p.highlighted) Color(0xFFFFEACB) else Cream,RoundedCornerShape(14.dp)).combinedClickable(onClick=vm::touch,onLongClick={vm.highlight(p.index)}).padding(14.dp)) {
     Text((p.index+1).toString()+"  "+if(p.verified) "✓ Read" else if(skipped) "Return to unread passages" else "",fontSize=10.sp,color=Clay)
     Text(p.text,fontFamily=FontFamily.Serif,fontSize=20.sp,lineHeight=32.sp,color=Ink)
    }
   } }
  }
 }
}
@Composable private fun Reflections(vm:ReaderViewModel,s:UiState) {
 var text by remember(s.group.id,s.reflectionDay) { mutableStateOf("") };var critique by remember { mutableStateOf("") }
 val unlocked=if(s.reflectionDay<s.group.day) s.reflections.any { it["uid"]==vm.uid && it["status"]=="REVISION_REQUESTED" } else s.verifiedThrough>=s.group.end && s.quote>=s.group.start
 Page {
  Heading("Leave a thought.","A question, a connection, a passage that stayed with you.")
  Row(verticalAlignment=Alignment.CenterVertically) {
   TextButton(enabled=s.reflectionDay>s.group.firstDay,onClick={vm.reflectionDay(s.reflectionDay-1)}){Text("Previous")}
   Text("Day "+s.reflectionDay)
   TextButton(enabled=s.reflectionDay<s.group.day,onClick={vm.reflectionDay(s.reflectionDay+1)}){Text("Next")}
  }
  if(!unlocked) Panel { Text("Read today's range and long-press one quote to unlock your reflection.");Text("Partner reflection unlocks after reading.",Modifier.blur(5.dp)) }
  else {
   Text(if(s.reflectionDay==s.group.day) "Saved quote · paragraph "+(s.quote+1) else "Revising your earlier reflection",color=Clay)
   Text("What stayed with you? A connection, question, or moment is enough.",style=MaterialTheme.typography.bodySmall)
   Field(text,{text=it},"Your daily reflection",Modifier.heightIn(min=130.dp))
   Text(text.length.toString()+" characters · You can edit before your partner reviews it.",style=MaterialTheme.typography.labelSmall)
   Button(onClick={vm.action("commentary",text=text)},enabled=text.isNotBlank()&&!s.busy){Text("Share reflection")}
  }
  s.reflections.forEach { e -> Panel {
   Text(if(e["uid"]==vm.uid) "YOUR REFLECTION" else "YOUR PARTNER",style=MaterialTheme.typography.labelMedium,color=Clay)
   Text(e["text"].toString(),fontFamily=FontFamily.Serif,fontSize=20.sp,lineHeight=29.sp)
   Surface(color=if(e["status"]=="APPROVED") Color(0xFFDCEBD7) else Pale,shape=RoundedCornerShape(50.dp)) {
    Text(e["status"].toString().replace('_',' '),modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall)
   }
   if(e["critique"]!=null) Text("Review: "+e["critique"])
   if(e["uid"]==vm.uid && e["status"]=="PENDING_REVIEW" && e["reviewedBy"]==null) TextButton(enabled=!s.busy,onClick={vm.action("withdraw_reflection")}){Text("Recall reflection")}
   if(e["uid"]==vm.uid && e["status"]=="REVISION_REQUESTED") TextButton(onClick={text=e["text"].toString()}){Text("Edit your reflection")}
   if(e["uid"]!=vm.uid && e["status"]=="PENDING_REVIEW") {
    Field(critique,{critique=it},"Your response")
    Row { TextButton(onClick={vm.action("approve",text=critique,target=e["uid"].toString())}){Text("Approve")};TextButton(enabled=critique.isNotBlank(),onClick={vm.action("revise",text=critique,target=e["uid"].toString())}){Text("Request revision")} }
   }
  } }
  if(s.verifiedThrough>=s.group.end || s.reflectionDay<s.group.day) {
   Text("A little help thinking",fontFamily=FontFamily.Serif,fontSize=24.sp)
   if(s.verifiedThrough>=s.group.end) TextButton(onClick={vm.assistant("questions")}){Text("Get 3 discussion questions")}
   TextButton(onClick={vm.assistant("mediate")}){Text("Ask for neutral mediation")}
   if(s.aiText.isNotBlank()) Panel { Text(s.aiText) }
  }
 }
}
@Composable private fun Chat(vm:ReaderViewModel,s:UiState) {
 var text by remember { mutableStateOf("") };var reference by remember { mutableStateOf("") }
 Column(Modifier.fillMaxSize().padding(horizontal=22.dp)) {
  Heading("Between the lines.","Keep the conversation going.")
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=16.dp)) {
   item { TextButton(onClick=vm::moreMessages){Text("Load earlier messages")} }
   if(s.messages.isEmpty()) item { Panel { Text("No messages yet",fontFamily=FontFamily.Serif,fontSize=22.sp);Text("Share a thought about today's reading to start the conversation.") } }
   items(s.messages) { m -> Surface(color=if(m["uid"]==vm.uid) Pale else Color(0xFFE7ECDD),shape=RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
    Text(if(m["uid"]==vm.uid) "You" else "Partner",style=MaterialTheme.typography.labelSmall,color=Clay);Text(m["text"].toString())
    val p=m["paragraphIndex"] as? Long ?: -1;if(p>=0) Text("¶ "+(p+1),color=Clay)
   } } }
  }
  Field(text,{text=it},"Message")
  Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(value=reference,onValueChange={reference=it},label={Text("Paragraph # (optional)")},modifier=Modifier.weight(1f));TextButton(onClick={vm.send(text,reference);text=""},enabled=text.isNotBlank()){Text("Send")} }
  TextButton(onClick={vm.assistant("summary")}){Text("Summarize this week's conversation")}
  if(s.aiText.isNotBlank()) Text(s.aiText,Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)
 }
}
@Composable private fun StreakCard(s:UiState) {
 val current=ReadingRewards.activeStreak(s.group.streak,s.group.lastStreakDate,LocalDate.now(ZoneOffset.UTC))
 val rewards=ReadingRewards.progress(current,s.group.bestStreak)
 val progress by animateFloatAsState(rewards.fraction,label="Streak milestone")
 var info by remember { mutableStateOf(false) }
 Surface(color=Ink,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("YOUR SHARED STREAK",color=Color(0xFFEFC372),style=MaterialTheme.typography.labelMedium)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
    Column { Text("✦ "+current,fontSize=48.sp,fontFamily=FontFamily.Serif,color=Cream);Text(if(current==1) "day of reading together" else "days of reading together",color=Cream) }
    Column(horizontalAlignment=Alignment.End) { Text("CIRCLE BEST",color=Cream.copy(alpha=.65f),fontSize=10.sp);Text(rewards.bestStreak.toString()+" days",color=Color(0xFFEFC372),fontSize=20.sp) }
   }
   Text(rewards.next?.let { rewards.remainingDays.toString()+" more "+if(rewards.remainingDays==1) "day to "+it.name else "days to "+it.name } ?: "All four badges earned. Keep your reading ritual growing.",color=Cream)
   LinearProgressIndicator(progress={progress},color=Color(0xFFEFC372),trackColor=Cream.copy(alpha=.18f),modifier=Modifier.fillMaxWidth())
   ReadingRewards.badges.chunked(2).forEach { pair ->
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { pair.forEach { badge ->
     val earned=badge.days<=rewards.bestStreak
     Surface(color=if(earned) Color(0xFFEFC372) else Color(0xFF3B5043),shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f)) {
      Column(Modifier.padding(12.dp)) { Text(badge.symbol+" "+badge.name,color=if(earned) Ink else Cream);Text(badge.days.toString()+" days · "+if(earned) "Earned" else "To earn",fontSize=11.sp,color=if(earned) Ink else Cream.copy(alpha=.7f)) }
     }
    } }
   }
   TextButton(onClick={info=true}) { Text("How streaks work · "+s.group.tokens+" pause tokens",color=Cream) }
  }
 }
 if(info) AlertDialog(onDismissRequest={info=false},title={Text("Grow a reading ritual")},text={Text("Both readers finish the daily reading and reflections, then choose Complete day on Home. Consecutive completion dates grow your shared streak. Dates follow UTC. A pause extends the deadline; it does not add a streak day. Badges at 3, 7, 14 and 30 days stay earned when a streak restarts.")},confirmButton={TextButton(onClick={info=false}){Text("Got it")}})
}
@Composable private fun Insights(vm:ReaderViewModel,s:UiState,onBack:()->Unit) {
 val sessions by vm.sessions.collectAsStateWithLifecycle()
 val verifiedDemoParagraphs by vm.verifiedDemoParagraphs.collectAsStateWithLifecycle()
 val elapsedSeconds=sessions.sumOf { (it.endTime-it.startTime).coerceAtLeast(0) }/1000
 val readingTime=if(elapsedSeconds<60) "${elapsedSeconds}s" else "${elapsedSeconds/60}m ${elapsedSeconds%60}s"
 val active=s.history.firstOrNull { it.bookId==s.group.bookId }
 val books=(if(s.group.id!="demo" && s.group.bookId.isNotBlank()) listOf(active ?: BookRecord(s.group.bookId,s.group.title,s.group.paragraphCount,s.group.readingStart)) else emptyList())+s.history.filter { it.bookId!=s.group.bookId }
 Page {
  TextButton(onClick=onBack){Text("Back to More")}
  Heading("Your bookshelf.",if(s.group.id=="demo") "Join a circle to build your reading history." else "Books in "+s.group.name)
  if(s.historyLoading) Text("Loading your reading history…")
  if(s.historyError.isNotBlank()) Text(s.historyError,color=Clay)
  if(books.isEmpty() && !s.historyLoading) Text("Your current book and the books you move on from will appear here.")
  books.forEach { entry ->
   val isCurrent=entry.bookId==s.group.bookId
   val through=if(isCurrent && s.bookReady) maxOf(s.verifiedThrough,entry.verifiedThroughByUid[vm.uid] ?: -1) else entry.verifiedThroughByUid[vm.uid] ?: -1
   val read=BookProgress.calculate(through,entry.paragraphCount,entry.readingStart)
   Panel {
    Text(if(isCurrent) "CURRENT BOOK" else "PREVIOUS BOOK",style=MaterialTheme.typography.labelMedium,color=Clay)
    Text(entry.title,fontFamily=FontFamily.Serif,fontSize=25.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(when { read.completed -> "Finished reading";!isCurrent -> "Stopped before finishing";else -> "Reading in progress" },fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(progress={read.fraction},modifier=Modifier.fillMaxWidth())
    Text("You read "+read.percent+"% · "+read.readParagraphs+" of "+read.totalParagraphs+" reading paragraphs")
    if(entry.status=="completed" || (isCurrent && s.group.completed)) Text("Shared book goal completed",color=Clay)
    entry.removedAt?.let { Text("Moved on "+Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy")),style=MaterialTheme.typography.bodySmall) }
   }
  }
  if(s.group.id!="demo") StreakCard(s)
  Heading("Your reading habit.","Sessions recorded on this device.")
  Panel {
   val demo=s.group.id=="demo"
   Text((if(demo) verifiedDemoParagraphs else sessions.sumOf { it.paragraphsRead }).toString(),fontSize=42.sp,fontFamily=FontFamily.Serif,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text(if(demo) "sample paragraphs verified" else "paragraphs verified across "+sessions.size+" recorded sessions")
   Text("$readingTime spent reading on this device")
  }
 }
}
@Composable private fun SignInOptions(vm:ReaderViewModel,s:UiState) {
 val activity=LocalActivity.current
 var email by rememberSaveable { mutableStateOf("") }
 var showLink by rememberSaveable { mutableStateOf(false) }
 var link by remember { mutableStateOf("") }
 Button(onClick={activity?.let(vm::google)},enabled=!s.busy && activity!=null){Text("Continue with Google")}
 Field(email,{email=it},"Email address")
 OutlinedButton(onClick={vm.email(email)},enabled=email.contains('@')&&!s.busy){Text("Email a sign-in link")}
 if(s.notice.isNotBlank()) Text(s.notice,style=MaterialTheme.typography.bodySmall)
 if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
 if(showLink) {
  Field(link,{link=it},"Sign-in link from your email")
  OutlinedButton(onClick={vm.finishEmailSignIn(email,link)},enabled=email.contains('@')&&link.isNotBlank()&&!s.busy) {
   Text("Sign in with link")
  }
 } else {
  TextButton(onClick={showLink=true}){Text("Paste a sign-in link")}
 }
 if(!vm.auth.configured) Text("Local demo is ready. Shared reading needs your Firebase project configuration.",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun Settings(vm:ReaderViewModel,s:UiState,onBack:()->Unit) {
 Page {
  TextButton(onClick=onBack){Text("Back to More")}
  Heading("Make it yours.")
  Panel {
   if(s.signedIn) { Text("Signed in as "+s.email);OutlinedButton(onClick=vm::signOut){Text("Sign out")} }
   else {
    Text("Read with your partner",fontFamily=FontFamily.Serif,fontSize=24.sp)
    SignInOptions(vm,s)
   }
  }
  Panel { Text("Read. Reflect. Grow together.",fontFamily=FontFamily.Serif,fontSize=23.sp);Text("Read at your own pace, choose a passage that stays with you, and share a reflection with your partner. Progress is saved while you are actively reading.");Text("CoRead "+com.coread.app.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelSmall,color=Clay) }
 }
}
