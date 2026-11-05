package com.coread.app.domain

object ProgressSync {
 fun merge(expectedBook:String,activeBook:String?,localThrough:Int,remoteThrough:Int,paragraphCount:Int):Int {
  require(expectedBook.isNotBlank() && activeBook==expectedBook) { "The circle's book changed. Reopen the circle to load its current book." }
  require(paragraphCount>0 && localThrough>=-1 && localThrough<paragraphCount && remoteThrough>=-1 && remoteThrough<paragraphCount) { "The reading copy is out of date. Reopen the circle before syncing." }
  return maxOf(localThrough,remoteThrough)
 }
}
