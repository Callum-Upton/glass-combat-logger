package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpellTrackerTest {
    @Test public void castsAndTriggersAndReceivedSpellsAreDistinct() {
        assertEquals("cast",SpellTracker.graphic(725)[2]);
        assertEquals("received",SpellTracker.graphic(726)[2]);
        assertEquals("trigger",SpellTracker.graphic(1855)[2]);
        assertEquals("trigger",SpellTracker.graphic(3289)[2]);
        assertEquals("thrall",SpellTracker.graphic(1874)[0]);
        assertNull(SpellTracker.graphic(3243));
    }
    @Test public void activeFlagsSupportPrecastAndUpgradedDeathCharge() {
        SpellTracker t=new SpellTracker();
        assertEquals(true,t.snapshot(10,1,2,1).get("thrall"));
        assertEquals(2,t.snapshot(10,1,2,1).get("deathChargeCharges"));
        assertEquals(true,t.snapshot(11,0,1,0).get("death_charge"));
        assertEquals(false,t.snapshot(12,0,0,0).get("thrall"));
        assertNull(t.snapshot(12,0,0,0).get("mark_of_darkness"));
    }
    @Test public void markEstimateExpiresAndResetsToUnknown() {
        SpellTracker t=new SpellTracker();t.markCast(10,99,false);
        assertEquals(true,t.snapshot(306,0,0,0).get("mark_of_darkness"));
        assertEquals(false,t.snapshot(307,0,0,0).get("mark_of_darkness"));
        t.markCast(10,99,true);assertEquals(1485,t.snapshot(10,0,0,0).get("markRemainingTicks"));
        t.reset();assertNull(t.snapshot(10,0,0,0).get("markRemainingTicks"));
    }
}
