package com.coread.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadingRewardsTest {
 @Test fun aNewReaderSeesTheFirstReachableReward() {
  val rewards=ReadingRewards.progress(0,0)
  assertTrue(rewards.earned.isEmpty())
  assertEquals(3,rewards.next?.days)
  assertEquals(3,rewards.remainingDays)
  assertEquals(0f,rewards.fraction,0f)
 }

 @Test fun reachingAThresholdUnlocksItAndShowsTheNextGoal() {
  val rewards=ReadingRewards.progress(7,3)
  assertEquals(7,rewards.bestStreak)
  assertEquals(listOf(3,7),rewards.earned.map { it.days })
  assertEquals(14,rewards.next?.days)
  assertEquals(7,rewards.remainingDays)
  assertEquals(.5f,rewards.fraction,0f)
 }

 @Test fun restartingAStreakKeepsPreviouslyEarnedBadges() {
  val rewards=ReadingRewards.progress(2,14)
  assertEquals(listOf(3,7,14),rewards.earned.map { it.days })
  assertEquals(30,rewards.next?.days)
  assertEquals(28,rewards.remainingDays)
  assertEquals(2f/30,rewards.fraction,0.0001f)
 }

 @Test fun negativeValuesCannotCreateBadgesOrNegativeProgress() {
  val rewards=ReadingRewards.progress(Int.MIN_VALUE,-3)
  assertEquals(0,rewards.currentStreak)
  assertEquals(0,rewards.bestStreak)
  assertTrue(rewards.earned.isEmpty())
  assertEquals(3,rewards.remainingDays)
  assertEquals(0f,rewards.fraction,0f)
 }

 @Test fun finalRewardRemainsEarnedAfterAStreakEnds() {
  val rewards=ReadingRewards.progress(0,30)
  assertEquals(4,rewards.earned.size)
  assertNull(rewards.next)
  assertEquals(0,rewards.remainingDays)
  assertEquals(1f,rewards.fraction,0f)
 }

 @Test fun oneDayBeforeAMilestoneDoesNotUnlockItEarly() {
  val rewards=ReadingRewards.progress(13,13)
  assertEquals(listOf(3,7),rewards.earned.map { it.days })
  assertEquals(14,rewards.next?.days)
  assertEquals(1,rewards.remainingDays)
 }

 @Test fun todayAndYesterdayKeepTheStreakActive() {
  val today=LocalDate.of(2026,10,29)
  assertEquals(7,ReadingRewards.activeStreak(7,"2026-10-29",today))
  assertEquals(7,ReadingRewards.activeStreak(7,"2026-10-28",today))
 }

 @Test fun anOlderOrFutureDateCannotClaimAnActiveStreak() {
  val today=LocalDate.of(2026,10,29)
  assertEquals(0,ReadingRewards.activeStreak(7,"2026-10-27",today))
  assertEquals(0,ReadingRewards.activeStreak(7,"2026-10-30",today))
 }

 @Test fun missingOrInvalidDatesKeepEarnedRewardsButNotAnActiveStreak() {
  val today=LocalDate.of(2026,10,29)
  listOf("","unknown","2026-02-30").forEach { date ->
   val active=ReadingRewards.activeStreak(14,date,today)
   assertEquals(0,active)
   assertEquals(listOf(3,7,14),ReadingRewards.progress(active,14).earned.map { it.days })
  }
 }

 @Test fun activityAtTheYearBoundaryAndNegativeCountsAreHandled() {
  val today=LocalDate.of(2027,1,1)
  assertEquals(3,ReadingRewards.activeStreak(3,"2026-12-31",today))
  assertEquals(0,ReadingRewards.activeStreak(-3,"2027-01-01",today))
 }
}
