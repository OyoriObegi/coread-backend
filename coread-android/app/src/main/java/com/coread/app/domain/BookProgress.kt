package com.coread.app.domain

data class ReadingProgress(
 val readParagraphs:Int,
 val totalParagraphs:Int,
 val percent:Int,
 val fraction:Float,
 val completed:Boolean
)

object BookProgress {
 fun calculate(verifiedThrough:Int,paragraphCount:Int,readingStart:Int=0):ReadingProgress {
  val count=paragraphCount.coerceAtLeast(0)
  val start=readingStart.coerceIn(0,count)
  val total=count-start
  if(total==0) return ReadingProgress(0,0,0,0f,false)
  // The stored position is inclusive. Use Long so extreme input cannot wrap around.
  val read=(verifiedThrough.toLong()-start+1).coerceIn(0L,total.toLong()).toInt()
  return ReadingProgress(
   read,total,
   percent=(read.toLong()*100/total).toInt(),
   fraction=(read.toFloat()/total).coerceIn(0f,1f),
   completed=read==total
  )
 }
}
