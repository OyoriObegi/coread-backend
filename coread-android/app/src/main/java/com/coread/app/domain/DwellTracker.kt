package com.coread.app.domain
class DwellTracker {
 private val elapsed=mutableMapOf<Int,Long>()
 private val complete=mutableSetOf<Int>()
 fun tick(visible:Map<Int,Int>,deltaMs:Long,active:Boolean):Set<Int> {
  elapsed.keys.retainAll(visible.keys)
  if(!active) return emptySet()
  return visible.filter { (index,words) ->
   if(index in complete) false else {
    val time=(elapsed[index] ?: 0)+deltaMs.coerceIn(0,1000);elapsed[index]=time
    if(time>=requiredMs(words)) { complete.add(index);true } else false
   }
  }.keys
 }
 companion object { fun requiredMs(words:Int):Long=kotlin.math.ceil(words.coerceAtLeast(1)*60000.0/350.0).toLong() }
}
