package com.encounterledger;

import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import java.lang.reflect.Field;

public class GrotesqueGuardiansProfileTest {
 @Test public void transformationsWithoutFurtherHitsDoNotLoseEncounterProfile() throws Exception {
  BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();h.plugin.area=true;
  final int[] id={7851};BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(7851);
  boss.npc=BossBoundaryTest.fake(NPC.class,m->m.equals("getId")?id[0]:m.equals("getName")?"Dusk":m.equals("getHealthRatio")?100:null);
  h.target=boss.npc;h.hit(boss);h.tick();h.target=null;
  for(int form:new int[]{7854,7855,7882,7883,7886,7887,7888,7889}) {
   id[0]=form;assertSame(ResearchBossProfile.GROTESQUE_GUARDIANS,BossProfile.forNpc(form));
   h.plugin.onActorDeath(new ActorDeath(boss.npc));
   for(int i=0;i<40;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
  }
  RoyalTitansProfileTest.message(h,"Your Grotesque Guardians kill count is: 123.");h.tick();
  RoyalTitansProfileTest.message(h,"Fight duration: 3:01.20. Personal best: 2:46.20");
  assertEquals(1,h.plugin.saved.size());assertEquals("Grotesque Guardians",h.plugin.saved.get(0).get("label"));
 }
 @Test public void playerDeathDuringExitGraceWinsOverLeavingArena() throws Exception {
  BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(7882);
  h.plugin.area=true;h.target=boss.npc;h.hit(boss);h.tick();h.target=null;h.plugin.area=false;h.tick();
  Field field=EncounterLedgerPlugin.class.getDeclaredField("client");field.setAccessible(true);
  h.plugin.onActorDeath(new ActorDeath(((Client)field.get(h.plugin)).getLocalPlayer()));h.tick();
  assertEquals(1,h.plugin.saved.size());assertEquals("player_death",h.plugin.saved.get(0).get("endReason"));
 }
 @Test public void dawnAndDuskStayInOneEncounterThroughEveryTransition() throws Exception {
  BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();h.plugin.area=true;
  for(int id:new int[]{7852,7851,7853,7855,7882,7883,7884,7885,7886,7887,7888,7889}) {
   BossBoundaryTest.Enemy phase=new BossBoundaryTest.Enemy(id);
   assertSame(ResearchBossProfile.GROTESQUE_GUARDIANS,BossProfile.forNpc(id));
   h.target=phase.npc;h.hit(phase);h.tick();h.target=null;
   phase.dead=true;h.plugin.onActorDeath(new ActorDeath(phase.npc));
   for(int i=0;i<40;i++)h.tick();
   assertTrue("A phase must not finish the shared fight",h.plugin.saved.isEmpty());
  }
  RoyalTitansProfileTest.message(h,"Your Grotesque Guardians kill count is: 123.");h.tick();
  RoyalTitansProfileTest.message(h,"Fight duration: 5:01.20. Personal best: 2:46.20");
  assertEquals(1,h.plugin.saved.size());
  assertEquals("Grotesque Guardians",h.plugin.saved.get(0).get("label"));
  assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));
  assertNotNull(h.plugin.saved.get(0).get("officialTiming"));
 }
}
