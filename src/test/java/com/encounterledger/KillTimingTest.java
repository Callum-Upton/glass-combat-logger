package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
public class KillTimingTest {
 @Test public void stripsGameColourTokens(){assertEquals(411600,KillTiming.parse("Fight duration: @red@6:51.60</col>. Personal best: 2:54.60").get("durationMs"));}
 @Test public void capturesDurationNotPersonalBest(){assertEquals(395400,KillTiming.parse("Fight duration: 6:35.40. Personal best: 2:54.60").get("durationMs"));}
 @Test public void acceptsPersonalBestAndRetainsPrecision(){assertEquals(180600,KillTiming.parse("Fight duration: 3:00.6 (new personal best)").get("durationMs"));assertEquals(1000,KillTiming.parse("Fight duration: 3:00. Personal best: 2:54").get("precisionMs"));}
 @Test public void rejectsUnrelatedOrInvalid(){assertNull(KillTiming.parse("Personal best: 2:54.60"));assertNull(KillTiming.parse("Fight duration: 1:99.00"));assertNull(KillTiming.parse("Fight duration: 0:00"));}
}
