package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
public class ResearchBossProfileTest {
 @Test public void boundariesUseInstancedTemplateRegions(){
  ResearchBossProfile[] profiles={ResearchBossProfile.ZULRAH,ResearchBossProfile.DUKE,ResearchBossProfile.PHOSANI,ResearchBossProfile.VARDORVIS};
  int[] ids={2042,12191,9418,12223},regions={9007,12132,15515,4405};
  for(int i=0;i<profiles.length;i++){
   ResearchBossProfile p=profiles[i];assertSame(p,BossProfile.forNpc(ids[i]));
   WorldPoint tile=new WorldPoint((regions[i]>>8)*64+32,(regions[i]&255)*64+32,i==2?3:0);
   assertTrue(p.containsEncounterTile(tile,true));assertFalse(p.containsEncounterTile(tile,false));
   assertFalse(p.containsEncounterTile(new WorldPoint(3200,3200,0),true));
   assertFalse(p.containsEncounterTile(new WorldPoint(tile.getX(),tile.getY(),1),true));
   assertTrue(p.completionMessage("Your "+p.name()+" kill count is: 1,234."));
   assertFalse(p.completionMessage("Your Yama kill count is: 1."));
  }
  assertNull(BossProfile.forNpc(12224));assertNull(BossProfile.forNpc(12226));assertNull(BossProfile.forNpc(9469));assertNull(BossProfile.forNpc(2045));
 }
 @Test public void phosaniRecordedPlaneKeepsFightOpenAndExitStillCloses()throws Exception{
  BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(9418);
  WorldPoint arena=new WorldPoint((15515>>8)*64+32,(15515&255)*64+32,3);
  assertFalse(ResearchBossProfile.PHOSANI.containsEncounterTile(new WorldPoint(arena.getX(),arena.getY(),0),true));
  h.target=boss.npc;h.plugin.area=ResearchBossProfile.PHOSANI.containsEncounterTile(arena,true);h.hit(boss);
  for(int i=0;i<45;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
  h.target=null;h.plugin.area=ResearchBossProfile.PHOSANI.containsEncounterTile(new WorldPoint(3200,3200,0),false);
  h.tick();h.tick();assertTrue(h.plugin.saved.isEmpty());h.tick();
  assertEquals(1,h.plugin.saved.size());assertEquals("left_encounter",h.plugin.saved.get(0).get("endReason"));
 }
 @Test public void phaseDeathDoesNotSplitButCompletionAndExitDo()throws Exception{
  for(int id:new int[]{2042,12191,9418,12223}){
   BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(id);
   h.target=boss.npc;h.plugin.area=true;h.hit(boss);h.tick();h.target=null;
   h.plugin.onActorDeath(new ActorDeath(boss.npc));for(int i=0;i<40;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
   RoyalTitansProfileTest.message(h,"Your "+BossProfile.forNpc(id).name()+" kill count is: 12.");h.tick();
   RoyalTitansProfileTest.message(h,"Fight duration: 1:41. Personal best: 0:46");
   assertEquals(1,h.plugin.saved.size());assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));
   assertNotNull(h.plugin.saved.get(0).get("officialTiming"));
   BossBoundaryTest.Harness exit=new BossBoundaryTest.Harness();exit.target=boss.npc;exit.plugin.area=true;exit.hit(boss);exit.tick();exit.plugin.area=false;exit.target=null;exit.tick();exit.tick();assertTrue(exit.plugin.saved.isEmpty());exit.tick();assertEquals("left_encounter",exit.plugin.saved.get(0).get("endReason"));
  }
 }
}
