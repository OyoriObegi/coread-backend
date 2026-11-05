package com.coread.app.data
import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.NoCredentialException
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
  val option=GetSignInWithGoogleOption.Builder(context.getString(id)).build()
  val result=try {
   CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build())
  } catch(e:NoCredentialException) {
   throw IllegalStateException("Google sign-in couldn’t find an account. Add a Google account in Android Settings or use an email link.",e)
  }
  val credential=result.credential as? CustomCredential ?: error("Unsupported credential")
  val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
  FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await()
 }
 suspend fun email(email:String) {
  check(configured) { "Configure Firebase to enable sign-in" }
  val action=ActionCodeSettings.newBuilder().setUrl("https://"+FirebaseApp.getInstance().options.projectId+".firebaseapp.com/invite").setHandleCodeInApp(true).setAndroidPackageName(context.packageName,true,null).build()
  FirebaseAuth.getInstance().sendSignInLinkToEmail(email,action).await()
  settings.email(email)
 }
 suspend fun complete(link:String,email:String?=null):Boolean {
  if(!configured || !FirebaseAuth.getInstance().isSignInWithEmailLink(link)) return false
  val address=email?.trim()?.takeIf { it.isNotEmpty() } ?: settings.email.first()
  require(address.isNotBlank()) { "Enter the email that received this link, then open it again" }
  FirebaseAuth.getInstance().signInWithEmailLink(address,link).await();return true
 }
 fun signOut() { if(configured) FirebaseAuth.getInstance().signOut() }
}
