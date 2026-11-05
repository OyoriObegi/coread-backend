package com.coread.app.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
val Context.settingsStore by preferencesDataStore("coread")
class Settings @Inject constructor(@ApplicationContext private val context:Context) {
 private val onboardingVersion=intPreferencesKey("onboardingVersion")
 val onboardingPending=context.settingsStore.data.map {
  it[onboardingVersion]!=com.coread.app.BuildConfig.VERSION_CODE
 }
 suspend fun finishOnboarding() {
  context.settingsStore.edit { it[onboardingVersion]=com.coread.app.BuildConfig.VERSION_CODE }
 }
 suspend fun readingPosition(book:String):Pair<Int,Int>? {
  val data=context.settingsStore.data.first()
  val index=data[intPreferencesKey("readerIndex:"+book)] ?: return null
  return index.coerceAtLeast(0) to (data[intPreferencesKey("readerOffset:"+book)] ?: 0).coerceAtLeast(0)
 }
 suspend fun readingPosition(book:String,index:Int,offset:Int) {
  context.settingsStore.edit { it[intPreferencesKey("readerIndex:"+book)]=index.coerceAtLeast(0);it[intPreferencesKey("readerOffset:"+book)]=offset.coerceAtLeast(0) }
 }
 val url=kotlinx.coroutines.flow.flowOf(com.coread.app.BuildConfig.COREAD_API_BASE_URL)
 suspend fun celebrateBadge(key:String,best:Int):Int? {
  var earned:Int?=null
  context.settingsStore.edit {
   val saved=it[intPreferencesKey("seenBadge:"+key)] ?: 0
   earned=listOf(3,7,14,30).lastOrNull { threshold -> threshold>saved && threshold<=best }
   it[intPreferencesKey("seenBadge:"+key)]=maxOf(saved,best)
  }
  return earned
 }
 val pendingGroup=context.settingsStore.data.map { it[stringPreferencesKey("pendingGroup")] ?: "" }
 suspend fun pendingGroup(value:String) { context.settingsStore.edit { it[stringPreferencesKey("pendingGroup")]=value } }
 val email=context.settingsStore.data.map { it[stringPreferencesKey("email")] ?: "" }
 suspend fun email(value:String) { context.settingsStore.edit { it[stringPreferencesKey("email")]=value.trim() } }
}
