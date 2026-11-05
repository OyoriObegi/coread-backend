package com.coread.app
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.work.*
import com.coread.app.ui.*
import com.coread.app.notifications.DailyReminderWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
@AndroidEntryPoint class MainActivity:ComponentActivity() {
 private val vm:ReaderViewModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);intent.data?.let { vm.link(it) };setContent { CoReadApp(vm) }
  val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
  if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
  WorkManager.getInstance(this).enqueueUniquePeriodicWork("reading-reminder",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<DailyReminderWorker>(1,TimeUnit.HOURS).build())
 }
 override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);intent.data?.let { vm.link(it) } }
 override fun onResume() { super.onResume();vm.foreground(true) }
 override fun onPause() { vm.foreground(false);super.onPause() }
}
