package com.coread.app.data
import android.util.JsonReader
import androidx.room.withTransaction
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject
@Serializable private data class Paragraph(val index:Int,val text:String,val wordCount:Int)
class BookImporter @Inject constructor(private val db:CoReadDatabase) {
 suspend fun import(book:String,input:InputStream,compressed:Boolean=true) {
  JsonReader(InputStreamReader(if(compressed) GZIPInputStream(input) else input,Charsets.UTF_8)).use { r ->
   db.withTransaction {
    var expected=0
    r.beginObject()
    while(r.hasNext()) when(r.nextName()) {
     "paragraphs" -> {
      r.beginArray();val batch=mutableListOf<BookParagraphEntity>()
      while(r.hasNext()) {
       r.beginObject();var index=-1;var text="";var words=0
       while(r.hasNext()) when(r.nextName()) { "index" -> index=r.nextInt();"text" -> text=r.nextString();"wordCount" -> words=r.nextInt();else -> r.skipValue() }
       r.endObject();require(index==expected++) { "Invalid paragraph order" }
       val obj=buildJsonObject { put("index",index);put("text",text);put("wordCount",words) }
       val p=Json.decodeFromJsonElement<Paragraph>(obj)
       batch.add(BookParagraphEntity(book,p.index,p.text,p.wordCount))
       if(batch.size==100) { db.reading().paragraphs(batch.toList());batch.clear() }
      }
      db.reading().paragraphs(batch);r.endArray()
     }
     "chapters" -> {
      r.beginArray();var order=0
      while(r.hasNext()) {
       r.beginObject();var title="";var start=0
       while(r.hasNext()) when(r.nextName()) { "title" -> title=r.nextString();"startParagraphIndex" -> start=r.nextInt();else -> r.skipValue() }
       r.endObject();db.reading().chapters(listOf(ChapterMarkerEntity(book,order++,title,start)))
      };r.endArray()
     }
     else -> r.skipValue()
    };r.endObject();require(expected>0) { "Empty book" }
   }
  }
 }
}
