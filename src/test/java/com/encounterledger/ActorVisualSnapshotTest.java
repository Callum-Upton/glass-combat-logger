package com.encounterledger;

import java.util.*;
import net.runelite.api.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActorVisualSnapshotTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Actor actor(List<ActorSpotAnim> spots) throws Exception {
        Class type=Actor.class.getMethod("getSpotAnims").getReturnType();
        Object collection=BossBoundaryTest.fake(type,m->m.equals("iterator")?spots.iterator():null);
        return BossBoundaryTest.fake(Actor.class,m->m.equals("getSpotAnims")?collection:null);
    }
    @Test public void scheduledEffectsAreNotReportedAsActiveAndMissingIsExplicit() throws Exception {
        ActorSpotAnim future=BossBoundaryTest.fake(ActorSpotAnim.class,m->m.equals("getId")?3280:m.equals("getStartCycle")?115:null);
        Map<String,Object> snapshot=ActorVisualSnapshot.capture(actor(Collections.singletonList(future)),100);
        Map<?,?> graphic=(Map<?,?>)((List<?>)snapshot.get("graphics")).get(0);
        assertEquals(true,graphic.get("scheduled"));
        assertEquals(true,snapshot.get("available"));
        assertEquals(false,snapshot.get("truncated"));
        assertEquals(false,ActorVisualSnapshot.capture(null,100).get("available"));
        assertEquals(true,ActorVisualSnapshot.capture(actor(Collections.emptyList()),100).get("available"));
    }
    @Test public void duplicateVisualsSurviveAndOverflowIsExplicit() throws Exception {
        ActorSpotAnim spot=BossBoundaryTest.fake(ActorSpotAnim.class,m->m.equals("getId")?3242:null);
        List<ActorSpotAnim> spots=new ArrayList<>(Collections.nCopies(33,spot));
        Map<String,Object> snapshot=ActorVisualSnapshot.capture(actor(spots),100);
        assertEquals(32,((List<?>)snapshot.get("graphics")).size());
        assertEquals(true,snapshot.get("truncated"));
    }
}
