package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class EffectLifecycleRecorderTest {
 @Test public void arenaBurstRetainsScheduledEffectsAndStillReportsOverflow(){
  WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
  Player player=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(0,0,0):null);
  Client client=BossBoundaryTest.fake(Client.class,m->null);
  EffectLifecycleRecorder recorder=new EffectLifecycleRecorder();
  for(int i=0;i<1100;i++)recorder.observe(client,player,graphic(view),true);
  Map<String,Object> burst=recorder.drain();
  assertEquals(1100,((List<?>)burst.get("events")).size());assertEquals(false,burst.get("truncated"));
  for(int i=1100;i<=EffectLifecycleRecorder.MAX_ACTIVE;i++)recorder.observe(client,player,graphic(view),true);
  assertEquals(true,recorder.drain().get("truncated"));
  recorder.reset();recorder.observe(client,player,graphic(view),true);
  assertEquals(false,recorder.drain().get("truncated"));
 }
 private GraphicsObject graphic(WorldView view){return BossBoundaryTest.fake(GraphicsObject.class,m->{switch(m){case "getWorldView":return view;case "getLocation":return new LocalPoint(64,64);case "getId":return 3265;case "getStartCycle":return 600;default:return null;}});}
 @Test public void delayedStartHasStableIdentityAndFinishedObservation(){
  int[] cycle={10};boolean[] done={false};
  WorldView view=BossBoundaryTest.fake(WorldView.class,m->null);
  Player player=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint(0,0,0):null);
  Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getGameCycle")?cycle[0]:null);
  GraphicsObject g=BossBoundaryTest.fake(GraphicsObject.class,m->{switch(m){case "getWorldView":return view;case "getLocation":return new LocalPoint(64,64);case "getId":return 3252;case "getStartCycle":return 12;case "finished":return done[0];default:return null;}});
  EffectLifecycleRecorder recorder=new EffectLifecycleRecorder();
  recorder.observe(client,player,g,true);recorder.observe(client,player,g,false);
  List<?> first=(List<?>)recorder.drain().get("events");assertEquals(1,first.size());assertEquals("created",((Map<?,?>)first.get(0)).get("phase"));
  cycle[0]=12;recorder.observe(client,player,g,false);done[0]=true;cycle[0]=15;recorder.observe(client,player,g,false);
  List<?> last=(List<?>)recorder.drain().get("events");assertEquals(2,last.size());assertEquals("start_observed",((Map<?,?>)last.get(0)).get("phase"));assertEquals("finished_observed",((Map<?,?>)last.get(1)).get("phase"));assertEquals(((Map<?,?>)first.get(0)).get("identity"),((Map<?,?>)last.get(1)).get("identity"));
  assertTrue(((List<?>)recorder.drain().get("events")).isEmpty());
 }
}
