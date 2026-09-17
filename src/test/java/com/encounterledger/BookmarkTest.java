package com.encounterledger;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import org.junit.Test;
import static org.junit.Assert.*;

public class BookmarkTest {
 private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 private static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 @Test public void attachesToPressedTickDoesNotStartRecordingAndDeduplicates()throws Exception{
  int[] tick={100};
  Client client=BossBoundaryTest.fake(Client.class,m->{if(m.equals("getTickCount"))return tick[0];if(m.equals("getGameCycle"))return 3005;if(m.equals("isClientThread"))return true;if(m.equals("getGameState"))return GameState.LOGGED_IN;return null;});
  EncounterLedgerPlugin p=new EncounterLedgerPlugin();set(p,"client",client);
  Method add=p.getClass().getDeclaredMethod("addBookmark");add.setAccessible(true);
  add.invoke(p);assertNull(get(p,"encounter"));assertTrue(((List<?>)get(p,"pending")).isEmpty());
  List<Map<String,Object>> events=new ArrayList<>();Map<String,Object> frame=new HashMap<>();frame.put("clientTick",100);frame.put("events",events);
  set(p,"ticks",new ArrayList<>(Arrays.asList(frame)));set(p,"encounter",new HashMap<>());
  add.invoke(p);add.invoke(p);assertEquals(1,events.size());assertEquals("bookmark",events.get(0).get("kind"));assertEquals(100,events.get(0).get("clientTick"));assertEquals(3005,events.get(0).get("clientCycle"));
  tick[0]++;add.invoke(p);assertEquals(1,((List<?>)get(p,"pending")).size());assertEquals(1,events.size());
 }
 @Test public void researchBookmarkHasSameIdAndDoesNotOpenSession()throws Exception{
  ResearchRecorderTest.Harness h=new ResearchRecorderTest.Harness();assertFalse(h.recorder.bookmark("test"));
  h.enabled=true;h.recorder.onGameTick(new GameTick());assertTrue(h.recorder.bookmark("shared-id"));
  List<?> es=(List<?>)h.session().get("events");Map<?,?> e=(Map<?,?>)es.get(es.size()-1);
  assertEquals("bookmark",e.get("kind"));assertEquals(50,e.get("clientTick"));assertEquals("shared-id",((Map<?,?>)e.get("data")).get("bookmarkId"));
 }
}
