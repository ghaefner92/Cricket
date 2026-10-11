package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test

class JourneyPlaybackClockTest {
 @Test fun elapsedKeepsAdvancingWithoutAnyScreenTicks(){val p=JourneyPlaybackClock.create("s",1,1000,2);assertEquals(120.0,JourneyPlaybackClock.elapsed(p,121000,2),1e-8)}
 @Test fun pauseFreezesAndResumeExcludesPausedTime(){val p=JourneyPlaybackClock.create("s",1,1000,2);JourneyPlaybackClock.setRunning(p,false,11000,2);assertEquals(10.0,JourneyPlaybackClock.elapsed(p,91000,2),1e-8);JourneyPlaybackClock.setRunning(p,true,101000,2);assertEquals(20.0,JourneyPlaybackClock.elapsed(p,111000,2),1e-8)}
 @Test fun serializedStateRestoresAfterProcessRecreation(){val p=JourneyPlaybackClock.create("s",1,1000,2);val restored=org.json.JSONObject(p.toString());assertEquals(60.0,JourneyPlaybackClock.elapsed(restored,61000,2),1e-8)}
 @Test fun rebootDoesNotInventTimeFromAnotherMonotonicClock(){val p=JourneyPlaybackClock.create("s",1,1000,2);assertEquals(0.0,JourneyPlaybackClock.elapsed(p,91000,3),1e-8)}
 @Test fun previewStopsAtPlannedEndWithoutACompletedJourney(){val p=JourneyPlaybackClock.create("s",1,1000,2).put("duration_seconds",30);assertEquals(30.0,JourneyPlaybackClock.elapsed(p,91000,2),1e-8);assertEquals(1.0,JourneyPlaybackClock.progress(p,91000,2),1e-8);assertFalse(p.has("experience"));assertFalse(p.has("tracking"))}
}
