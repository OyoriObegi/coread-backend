package com.coread.app.domain

import org.junit.Assert.*
import org.junit.Test

class BookProgressTest {
 @Test fun skippedPreliminariesAreNotCountedAsReading() {
  val unread=BookProgress.calculate(9,110,10)
  assertEquals(100,unread.totalParagraphs)
  assertEquals(0,unread.readParagraphs)
  assertEquals(0,unread.percent)
  assertFalse(unread.completed)
  assertEquals(50,BookProgress.calculate(59,110,10).percent)
 }

 @Test fun readingTheFinalParagraphCompletesTheReadersBook() {
  val result=BookProgress.calculate(109,110,10)
  assertEquals(100,result.readParagraphs)
  assertEquals(100,result.percent)
  assertEquals(1f,result.fraction,0f)
  assertTrue(result.completed)
 }

 @Test fun aNearlyFinishedBookIsNotReportedAsCompleted() {
  val result=BookProgress.calculate(108,110,10)
  assertEquals(99,result.percent)
  assertFalse(result.completed)
 }

 @Test fun absentOrInvalidBookRangesAreEmptyRatherThanCompleted() {
  listOf(
   BookProgress.calculate(-1,0),
   BookProgress.calculate(30,-10),
   BookProgress.calculate(30,10,20)
  ).forEach {
   assertEquals(0,it.percent)
   assertEquals(0,it.totalParagraphs)
   assertFalse(it.completed)
  }
 }

 @Test fun positionsOutsideTheBookAreClamped() {
  assertEquals(0,BookProgress.calculate(Int.MIN_VALUE,10).percent)
  val beyond=BookProgress.calculate(Int.MAX_VALUE,10)
  assertEquals(100,beyond.percent)
  assertEquals(10,beyond.readParagraphs)
  assertTrue(beyond.completed)
  assertEquals(50,BookProgress.calculate(4,10,-3).percent)
 }

 @Test fun largeCountsCannotOverflowOrRoundUpToFalseCompletion() {
  val result=BookProgress.calculate(Int.MAX_VALUE-2,Int.MAX_VALUE)
  assertEquals(99,result.percent)
  assertFalse(result.completed)
 }
}
