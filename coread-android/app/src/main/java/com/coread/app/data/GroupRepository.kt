package com.coread.app.data
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
data class Group(val id:String="demo",val name:String="A little every day",val bookId:String="demo",val title:String="The Art of Reading Together",val day:Int=1,val start:Int=0,val end:Int=5,val streak:Int=0,val tokens:Int=3,val members:List<String> = emptyList(),val pending:Map<String,Any>?=null,val completed:Boolean=false,val ownerId:String="",val firstDay:Int=1,val maintenance:String="",val paragraphCount:Int=0,val readingStart:Int=0,val bestStreak:Int=0,val lastStreakDate:String="")
fun DocumentSnapshot.group():Group {
 val d=data ?: emptyMap()
 return Group(id,d["name"] as? String ?: "Reading circle",d["activeBookId"] as? String ?: "",d["bookTitle"] as? String ?: "Choose your first book",(d["currentDayNumber"] as? Long ?: 1).toInt(),(d["startParagraphIndex"] as? Long ?: 0).toInt(),(d["endParagraphIndex"] as? Long ?: 0).toInt(),(d["streakCount"] as? Long ?: 0).toInt(),(d["pauseTokensRemaining"] as? Long ?: 3).toInt(),(d["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),d["pendingRequest"] as? Map<String,Any>,d["completed"]==true,d["ownerId"] as? String ?: ((d["memberIds"] as? List<*>)?.firstOrNull() as? String).orEmpty(),(d["bookFirstDay"] as? Long ?: 1).toInt(),d["managementOperation"] as? String ?: "",(d["paragraphCount"] as? Long ?: 0).toInt(),(d["contentStartParagraphIndex"] as? Long ?: 0).toInt(),maxOf((d["bestStreak"] as? Long ?: 0).toInt(),(d["streakCount"] as? Long ?: 0).toInt()),d["lastStreakEvalDate"] as? String ?: "")
}
data class BookRecord(val bookId:String,val title:String,val paragraphCount:Int,val readingStart:Int=0,val status:String="active",val verifiedThroughByUid:Map<String,Int> = emptyMap(),val startedAt:Long?=null,val removedAt:Long?=null)
fun DocumentSnapshot.bookRecord():BookRecord {
 val progress=(get("verifiedThroughByUid") as? Map<*,*>)?.entries?.mapNotNull { (key,value) -> if(key is String && value is Number) key to value.toInt() else null }?.toMap() ?: emptyMap()
 return BookRecord(getString("bookId") ?: id,getString("title") ?: "Untitled book",(getLong("paragraphCount") ?: 0).toInt(),(getLong("readingStartParagraphIndex") ?: 0).toInt(),getString("status") ?: "active",progress,getTimestamp("startedAt")?.toDate()?.time,getTimestamp("removedAt")?.toDate()?.time)
}
class GroupRepository @Inject constructor() {
 private val db get()=FirebaseFirestore.getInstance()
 private val uid get()=FirebaseAuth.getInstance().currentUser!!.uid
 fun groups()=callbackFlow {
  val l=db.collection("groups").whereArrayContains("memberIds",uid).addSnapshotListener { value,error ->
   if(error!=null) close(error) else trySend(value?.documents?.map { it.group() } ?: emptyList())
  };awaitClose { l.remove() }
 }
 fun history(group:String)=callbackFlow {
  val listener=db.collection("groups").document(group).collection("readingHistory").addSnapshotListener { value,error ->
   if(error!=null) close(error) else trySend(value?.documents?.map { it.bookRecord() }?.sortedByDescending { it.removedAt ?: it.startedAt ?: 0L } ?: emptyList())
  };awaitClose { listener.remove() }
 }
 fun messages(group:String,limit:Long)=callbackFlow {
  val l=db.collection("groups").document(group).collection("messages").orderBy("createdAt",Query.Direction.DESCENDING).limit(limit).addSnapshotListener { value,error -> if(error!=null) close(error) else trySend(value?.documents?.map { it.data.orEmpty() }?.reversed() ?: emptyList()) };awaitClose { l.remove() }
 }
 fun reflections(group:String,day:Int,through:Int)=callbackFlow {
  var q:Query=db.collection("groups").document(group).collection("commentaries").whereEqualTo("dayNumber",day)
  if(through<0) q=q.whereEqualTo("uid",uid) else q=q.whereLessThanOrEqualTo("endParagraphIndex",through)
  val l=q.addSnapshotListener { value,error -> if(error!=null) close(error) else trySend(value?.documents?.map { it.data.orEmpty() } ?: emptyList()) };awaitClose { l.remove() }
 }
 suspend fun position(group:String):Int=(db.collection("groups").document(group).collection("members").document(uid).get().await().getLong("verifiedThrough") ?: -1).toInt()
 suspend fun sync(group:String,through:Int,book:String):Int {
  val circle=db.collection("groups").document(group);val member=circle.collection("members").document(uid)
  return db.runTransaction { tx ->
   val current=tx.get(circle);val saved=tx.get(member)
   check(saved.exists()) { "Reopen the circle to restore your membership." }
   val merged=com.coread.app.domain.ProgressSync.merge(book,current.getString("activeBookId"),through,(saved.getLong("verifiedThrough") ?: -1).toInt(),(current.getLong("paragraphCount") ?: 0).toInt())
   tx.update(member,mapOf("bookId" to book,"verifiedThrough" to merged,"lastActive" to FieldValue.serverTimestamp()))
   merged
  }.await()
 }
 suspend fun token(group:String,token:String) { db.collection("groups").document(group).collection("members").document(uid).update("fcmToken",token).await() }
 suspend fun message(group:String,text:String,paragraph:Int) { db.collection("groups").document(group).collection("messages").add(mapOf("uid" to uid,"text" to text.trim(),"paragraphIndex" to paragraph,"createdAt" to FieldValue.serverTimestamp())).await() }
}
