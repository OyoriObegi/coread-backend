package com.coread.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgressSyncTest {
 @Test fun reopeningBookCannotErasePreviouslySyncedReading() {
  assertEquals(66,ProgressSync.merge("book","book",-1,66,2954))
  assertEquals(66,ProgressSync.merge("book","book",9,66,2954))
 }
 @Test fun newlyReadPassagesCanAdvanceServerProgress() {
  assertEquals(80,ProgressSync.merge("book","book",80,66,2954))
 }
 @Test fun staleBookCannotSendProgressIntoReplacement() {
  assertThrows(IllegalArgumentException::class.java) { ProgressSync.merge("old","replacement",66,-1,2954) }
 }
 @Test fun replacementStartsWithItsOwnProgress() {
  assertEquals(-1,ProgressSync.merge("replacement","replacement",-1,-1,10))
 }
 @Test fun progressCannotExtendBeyondBookOrBelowUnreadSentinel() {
  assertThrows(IllegalArgumentException::class.java) { ProgressSync.merge("book","book",10,0,10) }
  assertThrows(IllegalArgumentException::class.java) { ProgressSync.merge("book","book",0,10,10) }
  assertThrows(IllegalArgumentException::class.java) { ProgressSync.merge("book","book",-2,0,10) }
 }
}
