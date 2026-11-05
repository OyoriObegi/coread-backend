package com.coread.app.notifications
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.*
import com.coread.app.MainActivity
import com.coread.app.data.CoReadDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.*
fun show(context:Context,text:String) {
 if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
 context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("reading","Reading together",NotificationManager.IMPORTANCE_DEFAULT))
 val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
 NotificationManagerCompat.from(context).notify(1,NotificationCompat.Builder(context,"reading").setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle("CoRead").setContentText(text).setContentIntent(intent).setAutoCancel(true).build())
}
class CoReadMessagingService:FirebaseMessagingService() {
 override fun onMessageReceived(message:RemoteMessage) { show(this,message.notification?.body ?: "Your reading partner has an update.") }
 override fun onNewToken(token:String) {
  if(FirebaseApp.getApps(this).isEmpty()) return
  val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
  FirebaseFirestore.getInstance().collection("groups").whereArrayContains("memberIds",uid).get().addOnSuccessListener { groups -> groups.forEach { it.reference.collection("members").document(uid).update("fcmToken",token) } }
 }
}
class DailyReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result=withContext(Dispatchers.IO) {
  if(FirebaseApp.getApps(applicationContext).isEmpty()) return@withContext Result.success()
  val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.success()
  val db=Room.databaseBuilder(applicationContext,CoReadDatabase::class.java,"coread.db").build()
  try {
   val logs=db.reading().sessions().first()
   val hours=logs.map { Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).hour }.sorted()
   val targetHour=if(hours.isEmpty()) 19 else hours[hours.size/2]
   val targetMinute=((targetHour*60-15)+1440)%1440
   val now=LocalTime.now();val minute=now.hour*60+now.minute
   val prefs=applicationContext.getSharedPreferences("reminder",0);val today=LocalDate.now().toString()
   if((minute-targetMinute+1440)%1440>=60 || prefs.getString("sent",null)==today) return@withContext Result.success()
   val groups=FirebaseFirestore.getInstance().collection("groups").whereArrayContains("memberIds",uid).get().await()
   val incomplete=groups.any { g -> val book=g.getString("activeBookId");book!=null && (db.reading().firstUnread(uid+":"+g.id+":"+book,(g.getLong("startParagraphIndex") ?: 0).toInt()) ?: Int.MAX_VALUE)<=(g.getLong("endParagraphIndex") ?: 0).toInt() }
   if(incomplete) { show(applicationContext,"Your usual reading time is coming up. A few pages together?");prefs.edit().putString("sent",today).apply() }
   Result.success()
  } catch(_:Exception) { Result.retry() } finally { db.close() }
 }
}
