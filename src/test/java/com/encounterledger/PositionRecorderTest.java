package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class PositionRecorderTest {
    @Test public void titansWallsAreCapturedUntargetedAndRemovedWithTheScene() {
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
        Player player=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(2913,9569,0):null);
        List<NPC> visible=new ArrayList<>();
        for(int id:new int[]{14152,14153})visible.add(BossBoundaryTest.fake(NPC.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(2912,9569,0):m.equals("getId")?id:m.equals("getName")?"Wall":null));
        Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getNpcs")?visible:null);
        PositionRecorder recorder=new PositionRecorder();
        assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
        assertEquals(2,((List<?>)recorder.snapshot(client,player,RoyalTitansProfile.INSTANCE).get("npcs")).size());
        visible.remove(0);assertEquals(1,((List<?>)recorder.snapshot(client,player,RoyalTitansProfile.INSTANCE).get("npcs")).size());
        visible.clear();assertTrue(((List<?>)recorder.snapshot(client,player,RoyalTitansProfile.INSTANCE).get("npcs")).isEmpty());
    }
    @Test public void raidActorsRemainCapturedWhenNotTargetedButNeverPersistAfterDespawn() {
        int[] state={0},x={3302};Actor[] target={null};
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
        Player player=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(3300,5290,0):m.equals("getInteracting")?target[0]:null);
        NPC npc=BossBoundaryTest.fake(NPC.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(x[0],5290,0):m.equals("getId")?7566:m.equals("getName")?"Vasa Nistirio":null);
        List<NPC> visible=new ArrayList<>(Arrays.asList(npc));
        Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getNpcs")?visible:m.equals("getVarbitValue")?state[0]:null);
        PositionRecorder recorder=new PositionRecorder();
        assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
        state[0]=1;target[0]=npc;
        Map<?,?> first=(Map<?,?>)((List<?>)recorder.snapshot(client,player).get("npcs")).get(0);
        target[0]=null;x[0]=3303;
        Map<?,?> moved=(Map<?,?>)((List<?>)recorder.snapshot(client,player).get("npcs")).get(0);
        assertEquals(first.get("identity"),moved.get("identity"));assertEquals(3303,((Map<?,?>)moved.get("position")).get("x"));
        x[0]=3400;assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
        x[0]=3303;visible.clear();assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
        visible.add(npc);state[0]=0;assertTrue(((List<?>)recorder.snapshot(client,player).get("npcs")).isEmpty());
    }
    @Test public void participantPositionsAreScopedStableAndDoNotRetainAbsentPlayers() {
        int[] enabled={0};
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
        Player local=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(1500,10080,0):m.equals("getName")?"Local":null);
        int[] x={1501};
        Player peer=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(x[0],10080,0):m.equals("getName")?"Teammate":null);
        List<Player> players=new ArrayList<>(Arrays.asList(local,peer));
        Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getPlayers")?players:m.equals("getVarbitValue")?enabled[0]:null);
        PositionRecorder recorder=new PositionRecorder();
        assertFalse(recorder.snapshot(client,local).containsKey("players"));enabled[0]=1;
        Map<?,?> a=(Map<?,?>)((List<?>)recorder.snapshot(client,local,null).get("players")).get(0);
        assertEquals(new HashSet<>(Arrays.asList("identity","name","position")),a.keySet());assertEquals(2,a.get("identity"));assertEquals("Teammate",a.get("name"));
        players.remove(1);assertTrue(((List<?>)recorder.snapshot(client,local,null).get("players")).isEmpty());
        x[0]=1502;players.add(peer);
        Map<?,?> b=(Map<?,?>)((List<?>)recorder.snapshot(client,local,null).get("players")).get(0);
        assertEquals(a.get("identity"),b.get("identity"));assertEquals(1502,((Map<?,?>)b.get("position")).get("x"));
        x[0]=1600;assertTrue(((List<?>)recorder.snapshot(client,local,null).get("players")).isEmpty());
    }
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
