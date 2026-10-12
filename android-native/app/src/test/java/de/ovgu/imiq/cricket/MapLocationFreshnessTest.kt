package de.ovgu.imiq.cricket

import org.junit.Assert.*
import org.junit.Test

class MapLocationFreshnessTest {
 @Test fun cachedAndFutureObservationsCannotBePresentedAsCurrent(){
  assertTrue(usableMapFix(52.0,11.0,10f,5_000_000_000L,6_000_000_000L))
  assertTrue(usableMapFix(52.0,11.0,100f,0L,30_000_000_000L))
  assertFalse(usableMapFix(52.0,11.0,10f,0L,30_000_000_001L))
  assertFalse(usableMapFix(52.0,11.0,10f,6_000_000_001L,6_000_000_000L))
 }
 @Test fun coordinatesAndAccuracyMustDescribeARealUsableFix(){
  assertFalse(usableMapFix(Double.NaN,11.0,10f,1L,2L))
  assertFalse(usableMapFix(91.0,11.0,10f,1L,2L))
  assertFalse(usableMapFix(52.0,181.0,10f,1L,2L))
  assertFalse(usableMapFix(52.0,11.0,Float.NaN,1L,2L))
  assertFalse(usableMapFix(52.0,11.0,-1f,1L,2L))
  assertTrue(usableMapFix(52.0,11.0,100f,1L,2L))
 }
}
