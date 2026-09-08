package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class PositionRecorderTest {
    @Test public void fireSpellsAreCapturedOnlyForYamaAndFollowMovementWithoutGhosts() {
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
        Player player=BossBoundaryTest.fake(Player.class,m->{if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(1479,10085,0);return null;});
        int[] y={10094};
        NPC npc=BossBoundaryTest.fake(NPC.class,m->{if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(1478,y[0],0);if(m.equals("getId"))return 13507;if(m.equals("getName"))return "<col=00ffff>Fire spell</col>";return null;});
        List<NPC> visible=new ArrayList<>();visible.add(npc);
        Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getNpcs")?visible:null);
        PositionRecorder recorder=new PositionRecorder();
        assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
        Map<?,?> first=(Map<?,?>)((List<?>)recorder.snapshot(client,player,YamaProfile.INSTANCE).get("npcs")).get(0);
        assertEquals("Fire spell",first.get("name"));assertEquals(10094,((Map<?,?>)first.get("position")).get("y"));
        y[0]=10093;
        Map<?,?> next=(Map<?,?>)((List<?>)recorder.snapshot(client,player,YamaProfile.INSTANCE).get("npcs")).get(0);
        assertEquals(first.get("identity"),next.get("identity"));assertEquals(10093,((Map<?,?>)next.get("position")).get("y"));
        visible.clear();assertTrue(((List<?>)recorder.snapshot(client,player,YamaProfile.INSTANCE).get("npcs")).isEmpty());
    }
    @Test public void glyphSnapshotsDeduplicateSceneObjectsAndObserveRemovalAndState() {
        Tile[][][] tiles=new Tile[1][2][1];
        int[] id={56335};
        GameObject glyph=BossBoundaryTest.fake(GameObject.class,m->{if(m.equals("getId"))return id[0];if(m.equals("getWorldLocation"))return new WorldPoint(1500,10080,0);return null;});
        Tile tile=BossBoundaryTest.fake(Tile.class,m->m.equals("getGameObjects")?new GameObject[]{glyph}:null);
        tiles[0][0][0]=tile;tiles[0][1][0]=tile;
        Scene scene=BossBoundaryTest.fake(Scene.class,m->m.equals("getTiles")?tiles:null);
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->m.equals("getScene")?scene:null);
        Player player=BossBoundaryTest.fake(Player.class,m->{if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(1500,10080,0);return null;});
        Client client=BossBoundaryTest.fake(Client.class,m->null);
        PositionRecorder recorder=new PositionRecorder();
        List<?> first=(List<?>)recorder.snapshot(client,player).get("glyphs");
        assertEquals(1,first.size());assertEquals("active",((Map<?,?>)first.get(0)).get("state"));
        id[0]=56338;
        assertEquals("fading",((Map<?,?>)((List<?>)recorder.snapshot(client,player).get("glyphs")).get(0)).get("state"));
        id[0]=56337;
        assertEquals("inactive",((Map<?,?>)((List<?>)recorder.snapshot(client,player).get("glyphs")).get(0)).get("state"));
        tiles[0][0][0]=null;tiles[0][1][0]=null;
        assertTrue(((List<?>)recorder.snapshot(client,player).get("glyphs")).isEmpty());
        assertNull(PositionRecorder.glyphState(56340));
    }
    @Test public void instanceChunksNormaliseAndUnloadedChunksStayUnknown() {
        int[][][] chunks=new int[4][13][13];
        for(int[][] plane:chunks)for(int[] row:plane)Arrays.fill(row,-1);
        chunks[0][0][0]=(187<<14)|(1260<<3);
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->{
            if(m.equals("isInstance"))return true;if(m.equals("getBaseX")||m.equals("getBaseY"))return 3200;
            if(m.equals("getSizeX")||m.equals("getSizeY"))return 104;if(m.equals("getId"))return -1;
            if(m.equals("getInstanceTemplateChunks"))return chunks;return null;
        });
        Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getWorldView")?view:null);
        Actor actor=BossBoundaryTest.fake(Actor.class,m->{if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(3201,3202,0);return null;});
        PositionRecorder recorder=new PositionRecorder();
        Map<String,Object> point=recorder.position(client,actor);
        assertEquals(1497,point.get("x"));assertEquals(10082,point.get("y"));
        chunks[0][0][0]=-1;assertNull(recorder.position(client,actor));
    }
    @Test public void snapshotsUseTilesAndStableIdentitiesWithoutCarryingDespawnedActors() {
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
        Player player=BossBoundaryTest.fake(Player.class,m->{if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(1500,10080,0);return null;});
        NPCComposition composition=BossBoundaryTest.fake(NPCComposition.class,m->m.equals("getSize")?3:null);
        java.util.function.Supplier<NPC> enemy=()->BossBoundaryTest.fake(NPC.class,m->{
            if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(1502,10082,0);
            if(m.equals("getId"))return 14176;if(m.equals("getIndex"))return 4;if(m.equals("getName"))return "Yama";
            if(m.equals("getTransformedComposition"))return composition;return null;
        });
        List<NPC> visible=new ArrayList<>();visible.add(enemy.get());
        Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getNpcs")?visible:null);
        PositionRecorder recorder=new PositionRecorder();
        Map<?,?> first=(Map<?,?>)((List<?>)recorder.snapshot(client,player).get("npcs")).get(0);
        assertEquals(3,first.get("size"));assertEquals(true,first.get("footprintKnown"));
        assertEquals(1502,((Map<?,?>)first.get("position")).get("x"));
        Map<?,?> repeated=(Map<?,?>)((List<?>)recorder.snapshot(client,player).get("npcs")).get(0);
        assertEquals(first.get("identity"),repeated.get("identity"));
        visible.clear();assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
        visible.add(enemy.get());Map<?,?> replacement=(Map<?,?>)((List<?>)recorder.snapshot(client,player).get("npcs")).get(0);
        assertNotEquals(first.get("identity"),replacement.get("identity"));
    }
    @Test public void missingWorldViewIsUnknownRatherThanFabricatedCoordinates() {
        PositionRecorder recorder=new PositionRecorder();
        Actor actor=BossBoundaryTest.fake(Actor.class,m->m.equals("getWorldLocation")?new WorldPoint(1,2,0):null);
        assertNull(recorder.position(null,actor));
    }
}
