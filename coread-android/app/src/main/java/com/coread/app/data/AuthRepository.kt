package com.coread.app.data
import android.app.Activity
import android.content.Context
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
class AuthRepository @Inject constructor(@ApplicationContext private val context:Context,private val settings:Settings) {
 val configured get()=FirebaseApp.getApps(context).isNotEmpty()
 val user get()=if(configured) FirebaseAuth.getInstance().currentUser else null
 suspend fun google(activity:Activity) {
  check(configured) { "Add Firebase configuration and rebuild to enable sign-in" }
  val id=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
  check(id!=0) { "Enable Google sign-in in Firebase and download updated configuration" }
  val option=GetGoogleIdOption.Builder().setServerClientId(context.getString(id)).setFilterByAuthorizedAccounts(false).build()
  val result=CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build())
  val credential=result.credential as? CustomCredential ?: error("Unsupported credential")
  val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
  FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await()
 }
 suspend fun email(email:String) {
  check(configured) { "Configure Firebase to enable sign-in" };settings.email(email)
  val action=ActionCodeSettings.newBuilder().setUrl("https://"+FirebaseApp.getInstance().options.projectId+".firebaseapp.com/invite").setHandleCodeInApp(true).setAndroidPackageName("com.coread.app",true,null).build()
  FirebaseAuth.getInstance().sendSignInLinkToEmail(email,action).await()
 }
 suspend fun complete(link:String):Boolean {
  if(!configured || !FirebaseAuth.getInstance().isSignInWithEmailLink(link)) return false
  val address=settings.email.first();require(address.isNotBlank()) { "Enter the email that received this link, then open it again" }
  FirebaseAuth.getInstance().signInWithEmailLink(address,link).await();return true
 }
 fun signOut() { if(configured) FirebaseAuth.getInstance().signOut() }
}
