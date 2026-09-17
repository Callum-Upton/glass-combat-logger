package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
public class ResearchBossProfileTest {
 @Test public void boundariesUseInstancedTemplateRegions(){
  ResearchBossProfile[] profiles={ResearchBossProfile.ZULRAH,ResearchBossProfile.DUKE,ResearchBossProfile.PHOSANI};
  int[] ids={2042,12191,9418},regions={9007,12132,15515};
  for(int i=0;i<profiles.length;i++){
   ResearchBossProfile p=profiles[i];assertSame(p,BossProfile.forNpc(ids[i]));
   WorldPoint tile=new WorldPoint((regions[i]>>8)*64+32,(regions[i]&255)*64+32,0);
   assertTrue(p.containsEncounterTile(tile,true));assertFalse(p.containsEncounterTile(tile,false));
   assertFalse(p.containsEncounterTile(new WorldPoint(3200,3200,0),true));
   assertFalse(p.containsEncounterTile(new WorldPoint(tile.getX(),tile.getY(),1),true));
   assertTrue(p.completionMessage("Your "+p.name()+" kill count is: 1,234."));
   assertFalse(p.completionMessage("Your Yama kill count is: 1."));
  }
  assertNull(BossProfile.forNpc(9469));assertNull(BossProfile.forNpc(2045));
 }
 @Test public void phaseDeathDoesNotSplitButCompletionAndExitDo()throws Exception{
  for(int id:new int[]{2042,12191,9418}){
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
