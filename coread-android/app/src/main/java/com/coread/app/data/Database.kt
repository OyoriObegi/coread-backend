package com.coread.app.data
import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
@Entity(primaryKeys=["bookId","index"])
data class BookParagraphEntity(val bookId:String,val index:Int,val text:String,val wordCount:Int,val verified:Boolean=false,val highlighted:Boolean=false)
@Entity(primaryKeys=["bookId","ordinal"])
data class ChapterMarkerEntity(val bookId:String,val ordinal:Int,val title:String,val startParagraphIndex:Int)
@Entity
data class ReadingSessionLogEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val startTime:Long,val endTime:Long,val paragraphsRead:Int,val averageWpm:Double)
@Dao interface ReadingDao {
 @Query("SELECT * FROM BookParagraphEntity WHERE bookId=:book ORDER BY [index]") fun paging(book:String):PagingSource<Int,BookParagraphEntity>
 @Query("SELECT * FROM BookParagraphEntity WHERE bookId=:book AND [index]=:index") suspend fun paragraph(book:String,index:Int):BookParagraphEntity?
 @Query("SELECT * FROM ChapterMarkerEntity WHERE bookId=:book ORDER BY ordinal") suspend fun chapters(book:String):List<ChapterMarkerEntity>
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun paragraphs(items:List<BookParagraphEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun chapters(items:List<ChapterMarkerEntity>)
 @Query("UPDATE BookParagraphEntity SET verified=1 WHERE bookId=:book AND [index]=:index AND verified=0") suspend fun verify(book:String,index:Int):Int
 @Query("SELECT COUNT(*) FROM BookParagraphEntity WHERE bookId='demo' AND verified=1") fun verifiedDemoParagraphs():Flow<Int>
 @Query("UPDATE BookParagraphEntity SET highlighted=1-highlighted WHERE bookId=:book AND [index]=:index") suspend fun toggleHighlight(book:String,index:Int)
 @Query("SELECT MIN([index]) FROM BookParagraphEntity WHERE bookId=:book AND verified=0 AND [index]>=:start") suspend fun firstUnread(book:String,start:Int):Int?
 @Query("UPDATE BookParagraphEntity SET verified=1 WHERE bookId=:book AND [index]<=:through") suspend fun restoreVerified(book:String,through:Int)
 @Query("SELECT COUNT(*) FROM BookParagraphEntity WHERE bookId=:book") suspend fun count(book:String):Int
 @Query("SELECT MAX([index]) FROM BookParagraphEntity WHERE bookId=:book AND highlighted=1 AND [index] BETWEEN :start AND :end") suspend fun quote(book:String,start:Int,end:Int):Int?
 @Query("SELECT SUM(wordCount) FROM BookParagraphEntity WHERE bookId=:book AND [index] BETWEEN :start AND :end") suspend fun words(book:String,start:Int,end:Int):Int?
 @Insert suspend fun log(session:ReadingSessionLogEntity)
 @Query("SELECT * FROM ReadingSessionLogEntity ORDER BY startTime DESC LIMIT 200") fun sessions():Flow<List<ReadingSessionLogEntity>>
}
@Database(entities=[BookParagraphEntity::class,ChapterMarkerEntity::class,ReadingSessionLogEntity::class],version=1,exportSchema=false)
abstract class CoReadDatabase:RoomDatabase() { abstract fun reading():ReadingDao }
