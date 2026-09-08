package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConsumableTrackerTest {
    @Test public void foodRequiresInventoryChangeAndEmitsOnce() {
        ConsumableTracker t=new ConsumableTracker();t.click(2,13441,1,"Anglerfish","Eat",10);
        assertNull(t.changed(2,13441,1,"Anglerfish",10));
        assertEquals("healing",t.changed(2,-1,0,"",11).category);
        assertNull(t.changed(2,-1,0,"",11));
    }
    @Test public void potionDoseAndFinalDoseAreRecognisedWithoutRequiringPrayerIncrease() {
        ConsumableTracker t=new ConsumableTracker();t.click(0,1,1,"Super restore(4)","Drink",0);
        assertEquals("prayer",t.changed(0,2,1,"Super restore(3)",1).category);
        t.click(0,3,1,"Prayer potion(1)","Drink",2);
        assertNotNull(t.changed(0,229,1,"Vial",2));
    }
    @Test public void cancelledStaleMovedAndNonConsumableActionsDoNotCount() {
        ConsumableTracker t=new ConsumableTracker();t.click(0,1,1,"Shark","Eat",0);
        assertNull(t.changed(0,2,1,"Staff",0));
        t.click(0,1,1,"Shark","Eat",0);assertNull(t.changed(0,-1,0,"",3));
        t.click(0,1,1,"Shark","Eat",3);t.click(0,1,1,"Shark","Drop",3);
        assertNull(t.changed(0,-1,0,"",3));
        assertNull(t.changed(1,-1,0,"",3));
    }
    @Test public void potionCategoriesDoNotInventHealingFromStatPotions() {
        assertEquals("potion",ConsumableTracker.category("Drink","Super combat potion(4)"));
        assertEquals("healing",ConsumableTracker.category("Drink","Saradomin brew(4)"));
        assertEquals("prayer",ConsumableTracker.category("Drink","Zamorak brew(4)"));
    }
}
