package com.coread.app.domain
import org.junit.Assert.*
import org.junit.Test
class DwellTrackerTest {
 @Test fun ceilingUses350Wpm() { assertEquals(60000L,DwellTracker.requiredMs(350));assertEquals(172L,DwellTracker.requiredMs(1)) }
 @Test fun backgroundDoesNotCount() { val t=DwellTracker();repeat(100) { assertTrue(t.tick(mapOf(0 to 1),250,false).isEmpty()) } }
 @Test fun leavingResetsTime() { val t=DwellTracker();t.tick(mapOf(0 to 7),1000,true);t.tick(emptyMap(),250,true);assertTrue(t.tick(mapOf(0 to 7),1000,true).isEmpty()) }
 @Test fun verifiesOnlyOnce() { val t=DwellTracker();assertEquals(setOf(0),t.tick(mapOf(0 to 1),250,true));assertTrue(t.tick(mapOf(0 to 1),250,true).isEmpty()) }
}
