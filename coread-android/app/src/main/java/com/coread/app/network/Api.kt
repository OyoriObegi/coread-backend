package com.coread.app.network
import com.coread.app.data.Settings
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
interface Api {
 @GET("health") suspend fun health():ResponseBody
 @POST("groups") suspend fun create(@Header("Authorization") token:String,@Body body:RequestBody):ResponseBody
 @POST("groups/{id}/join") suspend fun join(@Header("Authorization") token:String,@Path("id") id:String,@Body body:RequestBody):ResponseBody
 @POST("groups/{id}/manage") suspend fun manage(@Header("Authorization") token:String,@Path("id") id:String,@Body body:RequestBody):ResponseBody
 @POST("groups/{id}/actions") suspend fun action(@Header("Authorization") token:String,@Path("id") id:String,@Body body:RequestBody):ResponseBody
 @POST("groups/{id}/nudge") suspend fun nudge(@Header("Authorization") token:String,@Path("id") id:String,@Body body:RequestBody):ResponseBody
 @POST("groups/{id}/ai") suspend fun ai(@Header("Authorization") token:String,@Path("id") id:String,@Body body:RequestBody):ResponseBody
 @Multipart @POST("convert/{stage}") suspend fun convert(@Path("stage") stage:String,@Header("Authorization") token:String,@Part("groupId") group:RequestBody,@Part("bookId") book:RequestBody,@Part file:MultipartBody.Part,@Part("startParagraphIndex") start:RequestBody?,@Part("fingerprint") fingerprint:RequestBody?):ResponseBody
 @Streaming @GET("groups/{group}/books/{book}") suspend fun book(@Header("Authorization") token:String,@Path("group") group:String,@Path("book") book:String):ResponseBody
}
@Singleton class Backend @Inject constructor(private val settings:Settings) {
 private val client=OkHttpClient.Builder().connectTimeout(60,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS).build()
 private suspend fun api():Api {
  val url=settings.url.first();require(url.startsWith("https://")) { "Set your HTTPS Render service URL in Settings" }
  return Retrofit.Builder().baseUrl(url+"/").client(client).build().create(Api::class.java)
 }
 private suspend fun token()="Bearer "+(FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: error("Sign in first"))
 private fun body(data:Map<String,Any>):RequestBody=buildJsonObject {
  data.forEach { (key,v) -> when(v) { is Int -> put(key,v);is Boolean -> put(key,v);else -> put(key,v.toString()) } }
 }.toString().toRequestBody("application/json".toMediaType())
 private fun parse(response:ResponseBody)=response.use { Json.parseToJsonElement(it.string()).jsonObject }
 suspend fun create(name:String,email:String)=parse(api().create(token(),body(mapOf("name" to name,"invitedEmail" to email))))
 suspend fun join(group:String) { api().join(token(),group,body(emptyMap())).close() }
 suspend fun manage(group:String,kind:String,name:String,book:String) { api().manage(token(),group,body(mapOf("kind" to kind,"name" to name,"bookId" to book))).close() }
 suspend fun action(group:String,data:Map<String,Any>) { api().action(token(),group,body(data)).close() }
 suspend fun nudge(group:String) { api().nudge(token(),group,body(emptyMap())).close() }
 suspend fun ai(group:String,kind:String,day:Int=0)=parse(api().ai(token(),group,body(mapOf("kind" to kind,"dayNumber" to day))))["text"]!!.jsonPrimitive.content
 suspend fun book(group:String,book:String)=api().book(token(),group,book)
 suspend fun upload(group:String,book:String,name:String,input:InputStream,start:Int?=null,fingerprint:String?=null):JsonObject=withContext(Dispatchers.IO) {
  val api=api();api.health().close()
  val streamBody=object:RequestBody() {
   override fun contentType()="application/octet-stream".toMediaType()
   override fun isOneShot()=true
   override fun writeTo(sink:okio.BufferedSink) { input.use { source -> val buffer=ByteArray(65536);var total=0;while(true) { val n=source.read(buffer);if(n<0) break;total+=n;require(total<=40*1024*1024) { "Maximum file size is 40 MB" };sink.write(buffer,0,n) } } }
  }
  val stage=if(start==null) "preview" else "confirm"
  parse(api.convert(stage,token(),group.toRequestBody(),book.toRequestBody(),MultipartBody.Part.createFormData("file",name,streamBody),start?.toString()?.toRequestBody(),fingerprint?.toRequestBody()))
 }
}
class AiAssistantRepository @Inject constructor(private val backend:Backend) {
 suspend fun questions(group:String)=backend.ai(group,"questions")
 suspend fun summary(group:String)=backend.ai(group,"summary")
 suspend fun mediate(group:String,day:Int)=backend.ai(group,"mediate",day)
}
