package com.coread.app.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ReadingBadge(val days:Int,val name:String,val symbol:String)

data class StreakRewards(
 val currentStreak:Int,
 val bestStreak:Int,
 val earned:List<ReadingBadge>,
 val next:ReadingBadge?,
 val remainingDays:Int,
 val fraction:Float
)

object ReadingRewards {
 val badges:List<ReadingBadge> = listOf(
  ReadingBadge(3,"First spark","✦"),
  ReadingBadge(7,"Weekly rhythm","★"),
  ReadingBadge(14,"Steady reader","◆"),
  ReadingBadge(30,"Reading roots","✿")
 )

 fun activeStreak(storedStreak:Int,lastCompletedUtcDate:String,today:LocalDate):Int {
  val completed=try { LocalDate.parse(lastCompletedUtcDate) } catch(_:DateTimeParseException) { return 0 }
  return if(completed==today || completed==today.minusDays(1)) storedStreak.coerceAtLeast(0) else 0
 }

 fun progress(currentStreak:Int,bestStreak:Int):StreakRewards {
  val current=currentStreak.coerceAtLeast(0)
  val best=maxOf(current,bestStreak,0)
  val earned=badges.filter { it.days<=best }
  val next=badges.firstOrNull { it.days>best }
  return StreakRewards(
   current,best,earned,next,
   remainingDays=next?.let { (it.days-current).coerceAtLeast(0) } ?: 0,
   fraction=next?.let { (current.toFloat()/it.days).coerceIn(0f,1f) } ?: 1f
  )
 }
}
