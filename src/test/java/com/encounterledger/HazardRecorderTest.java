package com.encounterledger;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HazardRecorderTest {
    @Test @SuppressWarnings({"unchecked","rawtypes"}) public void hitObservationsKeepSeparateScheduledWaves() throws Exception {
        net.runelite.api.WorldView view=BossBoundaryTest.fake(net.runelite.api.WorldView.class,m->null);
        net.runelite.api.Player player=BossBoundaryTest.fake(net.runelite.api.Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new net.runelite.api.coords.WorldPoint(0,0,0):null);
        List<net.runelite.api.GraphicsObject> objects=new ArrayList<>();
        for(int i=0;i<2;i++)objects.add(BossBoundaryTest.fake(net.runelite.api.GraphicsObject.class,m->{switch(m){case "getWorldView":return view;case "getLocation":return new net.runelite.api.coords.LocalPoint(64,64);case "getId":return 3265;case "getStartCycle":return 115;default:return null;}}));
        Class type=net.runelite.api.Client.class.getMethod("getGraphicsObjects").getReturnType();
        Object collection=BossBoundaryTest.fake(type,m->m.equals("iterator")?objects.iterator():null);
        net.runelite.api.Client client=BossBoundaryTest.fake(net.runelite.api.Client.class,m->m.equals("getGraphicsObjects")?collection:m.equals("getGameCycle")?100:null);
        Map<String,Object> snapshot=new HazardRecorder().atHit(client,player,YamaProfile.INSTANCE);
        List<?> observations=(List<?>)snapshot.get("observations");
        assertEquals(2,observations.size());
        assertEquals(true,((Map<?,?>)observations.get(0)).get("scheduled"));
        assertNotEquals(((Map<?,?>)observations.get(0)).get("observationIndex"),((Map<?,?>)observations.get(1)).get("observationIndex"));
        assertEquals(false,snapshot.get("truncated"));
    }
    @Test public void overlapRequiresExactTileAndFloorAndRespectsMatchingProtection() {
        Map<String,Object> tile=new HashMap<>();tile.put("x",1500);tile.put("y",10080);tile.put("plane",0);
        Map<String,Object> fire=new HashMap<>();fire.put("position",tile);fire.put("type","fire_wave");
        Map<String,Object> shadow=new HashMap<>(fire);shadow.put("type","shadow_wave");
        List<Map<String,Object>> hazards=Arrays.asList(fire,fire,shadow);
        assertEquals(Arrays.asList("fire_wave","shadow_wave"),HazardRecorder.overlaps(tile,hazards,Collections.emptyList()));
        assertEquals(Collections.singletonList("shadow_wave"),HazardRecorder.overlaps(tile,hazards,Collections.singletonList("fire_wave")));
        Map<String,Object> other=new HashMap<>(tile);other.put("plane",1);
        assertTrue(HazardRecorder.overlaps(other,hazards,Collections.emptyList()).isEmpty());
        other.put("plane",0);other.put("x",1501);
        assertTrue(HazardRecorder.overlaps(other,hazards,Collections.emptyList()).isEmpty());
        assertTrue(HazardRecorder.overlaps(null,hazards,Collections.emptyList()).isEmpty());
    }
    @Test public void waveFamilyExcludesBossProjectilesAndMatchesProtectionElements() {
        assertEquals("shadow_wave",YamaProfile.INSTANCE.groundHazard(3263));
        assertEquals("fire_wave",YamaProfile.INSTANCE.groundHazard(3269));
        assertNull(YamaProfile.INSTANCE.groundHazard(3270));
        assertEquals("fire_wave",YamaProfile.INSTANCE.hazardProtection(3280));
        assertEquals("shadow_wave",YamaProfile.INSTANCE.hazardProtection(3281));
        assertEquals("flaming_skull",YamaProfile.INSTANCE.replayProjectile(3250));
        assertEquals("meteor",YamaProfile.INSTANCE.replayProjectile(3273));
        assertEquals("skull_impact",YamaProfile.INSTANCE.groundHazard(3251));
        assertEquals("flaming_skull",YamaProfile.INSTANCE.groundHazard(3250));
        assertEquals("meteor",YamaProfile.INSTANCE.groundHazard(3271));
        assertTrue(YamaProfile.INSTANCE.observesNpcDamage(14180));
        assertFalse(YamaProfile.INSTANCE.observesNpcDamage(1));
    }
}
