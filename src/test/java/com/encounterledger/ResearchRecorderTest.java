package com.encounterledger;
import java.lang.reflect.*;
import java.util.*;
import com.google.gson.Gson;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResearchRecorderTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();
    static class Harness {
        boolean enabled,combined;int tick=50,cycle=1500;
        List<Runnable> writes=new ArrayList<>();List<String> notices=new ArrayList<>();
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
        Player player=BossBoundaryTest.fake(Player.class,m->{if(m.equals("getWorldView"))return view;if(m.equals("getWorldLocation"))return new WorldPoint(3200,3200,0);if(m.equals("getName"))return "Test recorder";return null;});
        Client client=BossBoundaryTest.fake(Client.class,m->{if(m.equals("getGameState"))return GameState.LOGGED_IN;if(m.equals("getLocalPlayer"))return player;if(m.equals("getTickCount"))return tick;if(m.equals("getGameCycle"))return cycle;return null;});
        EncounterLedgerConfig config=new EncounterLedgerConfig(){public boolean researchMode(){return enabled;}public boolean combinedCapture(){return combined;}};
        ResearchRecorder recorder=new ResearchRecorder(client,config,new Gson(),writes::add,notices::add,null);
        Map<?,?> session() throws Exception {Field f=ResearchRecorder.class.getDeclaredField("session");f.setAccessible(true);return (Map<?,?>)f.get(recorder);}
        void emit() throws Exception {Method m=ResearchRecorder.class.getDeclaredMethod("emit",String.class,Map.class);m.setAccessible(true);m.invoke(recorder,"test_observation",ResearchRecorder.map("id",999));}
    }
    @Test public void disabledModeDoesNothingAndManualStopQueuesOneDetachedLog() throws Exception {
        Harness h=new Harness();h.recorder.onGameTick(new GameTick());assertNull(h.session());assertTrue(h.writes.isEmpty());
        h.enabled=true;h.recorder.onGameTick(new GameTick());Map<?,?> session=h.session();assertNotNull(session);
        h.cycle++;h.emit();List<?> events=(List<?>)session.get("events");Map<?,?> last=(Map<?,?>)events.get(events.size()-1);
        assertEquals(1501,last.get("clientCycle"));assertEquals(0,last.get("tick"));
        h.enabled=false;h.recorder.onGameTick(new GameTick());assertNull(h.session());assertEquals(1,h.writes.size());assertEquals("manual_stop",session.get("endReason"));
        h.recorder.stop("again");assertEquals(1,h.writes.size());
        h.enabled=true;h.recorder.onGameTick(new GameTick());assertNotNull(h.session());assertNotSame(session,h.session());
    }
    @Test public void capRollsWithoutLossAndKeepsSessionTimeline() throws Exception {
        Harness h=new Harness();h.enabled=true;h.recorder.onGameTick(new GameTick());Map<?,?> session=h.session();
        for(int i=0;i<ResearchRecorder.MAX_EVENTS+2;i++)h.emit();
        assertEquals(ResearchRecorder.MAX_EVENTS,((List<?>)session.get("events")).size());
        assertEquals("event_limit",session.get("endReason"));assertEquals(true,session.get("limitReached"));
        Map<?,?> next=h.session();assertNotNull(next);assertEquals(2,next.get("part"));assertEquals(session.get("sessionId"),next.get("sessionId"));
        List<?> remaining=(List<?>)next.get("events");assertFalse(remaining.isEmpty());
        assertEquals((long)ResearchRecorder.MAX_EVENTS,((Map<?,?>)remaining.get(0)).get("sequence"));
        assertEquals(true,session.get("continues"));assertEquals(1,h.writes.size());
        h.recorder.stop("manual_stop");assertEquals(false,next.get("continues"));assertEquals(2,h.writes.size());
    }
    @Test public void tickLimitRollsWithoutResettingRelativeTick() throws Exception {
        Harness h=new Harness();h.enabled=true;h.recorder.onGameTick(new GameTick());Map<?,?> session=h.session();
        h.tick+=ResearchRecorder.MAX_TICKS-1;h.recorder.onGameTick(new GameTick());assertEquals("tick_limit",session.get("endReason"));assertNotNull(h.session());
        h.tick++;h.recorder.onGameTick(new GameTick());List<?> events=(List<?>)h.session().get("events");
        assertEquals(ResearchRecorder.MAX_TICKS,((Map<?,?>)events.get(0)).get("tick"));assertEquals(2,h.session().get("part"));
    }
    @Test public void combinedModeStartsResearchAndPinsSessionUntilEncounterEnds() throws Exception {
        Harness h=new Harness();h.combined=true;h.recorder.onGameTick(new GameTick());assertNull(h.recorder.activeSessionId());h.recorder.beginEncounter("Yama");
        String id=h.recorder.activeSessionId();assertNotNull(id);
        Map<String,Object> encounter=new HashMap<>();encounter.put("researchSessionId",id);
        java.nio.file.Path root=temporary.newFolder().toPath();
        assertEquals(root.resolve("research").resolve(id),EncounterLedgerPlugin.encounterDirectory(root,encounter));
        assertEquals(root,EncounterLedgerPlugin.encounterDirectory(root,new HashMap<>()));
        h.recorder.retainForEncounter();h.combined=false;h.tick++;h.recorder.onGameTick(new GameTick());
        assertEquals(id,h.recorder.activeSessionId());assertTrue(h.writes.isEmpty());
        h.recorder.releaseEncounter();assertNull(h.recorder.activeSessionId());assertEquals(1,h.writes.size());
        h.combined=true;h.tick++;h.recorder.onGameTick(new GameTick());assertNull(h.recorder.activeSessionId());h.recorder.beginEncounter("Scurrius");assertNotEquals(id,h.recorder.activeSessionId());
        assertEquals(root.resolve("research").resolve(id),EncounterLedgerPlugin.encounterDirectory(root,encounter));
    }
    @Test public void combinedModeAutomaticallyRotatesToNextEncounterFolder() throws Exception {
        Harness h=new Harness();h.combined=true;h.recorder.onGameTick(new GameTick());assertNull(h.recorder.activeSessionId());h.recorder.beginEncounter("Yama");
        String first=h.recorder.activeSessionId();h.recorder.retainForEncounter();
        h.recorder.releaseEncounter();assertNull(h.recorder.activeSessionId());assertEquals(1,h.writes.size());
        h.tick++;h.recorder.onGameTick(new GameTick());
        assertNull(h.recorder.activeSessionId());h.recorder.beginEncounter("Yama");assertNotNull(h.recorder.activeSessionId());assertNotEquals(first,h.recorder.activeSessionId());
    }
    @Test public void partsAreWrittenIntoOneFolderAsValidJsonWithNoSequenceGap() throws Exception {
        Harness h=new Harness();java.nio.file.Path root=temporary.newFolder().toPath();
        h.recorder=new ResearchRecorder(h.client,h.config,new Gson(),h.writes::add,h.notices::add,null,root);
        h.enabled=true;h.recorder.onGameTick(new GameTick());String id=(String)h.session().get("sessionId");
        for(int i=0;i<ResearchRecorder.MAX_EVENTS;i++)h.emit();h.recorder.stop("manual_stop");
        for(Runnable write:h.writes)write.run();
        java.nio.file.Path folder=root.resolve(id);
        Map<?,?> first=new Gson().fromJson(java.nio.file.Files.readString(folder.resolve("part-0001.research.json")),Map.class);
        Map<?,?> second=new Gson().fromJson(java.nio.file.Files.readString(folder.resolve("part-0002.research.json")),Map.class);
        List<?> a=(List<?>)first.get("events"),b=(List<?>)second.get("events");
        assertEquals(ResearchRecorder.MAX_EVENTS,a.size());assertEquals(id,second.get("sessionId"));
        assertEquals(((Number)((Map<?,?>)a.get(a.size()-1)).get("sequence")).longValue()+1,((Number)((Map<?,?>)b.get(0)).get("sequence")).longValue());
        assertEquals(false,second.get("continues"));assertEquals("manual_stop",second.get("endReason"));
        try(java.util.stream.Stream<java.nio.file.Path> files=java.nio.file.Files.list(folder)){assertEquals(2,files.count());}
    }
    @Test public void rejectedSavePausesInsteadOfSilentlyContinuing() throws Exception {
        Harness h=new Harness();
        h.recorder=new ResearchRecorder(h.client,h.config,new Gson(),r->{throw new java.util.concurrent.RejectedExecutionException();},h.notices::add,null);
        h.enabled=true;h.recorder.onGameTick(new GameTick());
        for(int i=0;i<ResearchRecorder.MAX_EVENTS+1;i++)h.emit();
        assertNull(h.session());assertTrue(h.notices.stream().anyMatch(n->n.contains("could not be queued")));
    }
    @Test public void arenaIsSavedOncePerSceneBaseInsteadOfEveryTick() throws Exception {
        Harness h=new Harness();int[] base={3200};
        net.runelite.api.Tile tile=BossBoundaryTest.fake(net.runelite.api.Tile.class,m->null);
        net.runelite.api.Scene scene=BossBoundaryTest.fake(net.runelite.api.Scene.class,m->m.equals("getTiles")?new net.runelite.api.Tile[][][]{{{tile}}}:null);
        h.view=BossBoundaryTest.fake(WorldView.class,m->{if(m.equals("getScene"))return scene;if(m.equals("getBaseX")||m.equals("getBaseY"))return base[0];return null;});
        h.enabled=true;h.recorder.onGameTick(new GameTick());assertEquals(1,h.writes.size());
        h.tick++;h.recorder.onGameTick(new GameTick());assertEquals(1,h.writes.size());
        base[0]+=8;h.tick++;h.recorder.onGameTick(new GameTick());assertEquals(2,h.writes.size());
        h.recorder.stop("manual_stop");assertEquals(3,h.writes.size());
    }
}
