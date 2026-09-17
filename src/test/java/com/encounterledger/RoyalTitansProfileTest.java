package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.ChatMessageType;
public class RoyalTitansProfileTest {
 @Test public void scopedCapture(){
  RoyalTitansProfile p=RoyalTitansProfile.INSTANCE;
  for(int id:new int[]{12596,14147,14148,14149})assertSame(p,BossProfile.forNpc(id));
  assertNull(BossProfile.forNpc(14150));assertTrue(p.includesSpatialNpc(14153));assertTrue(p.includesSpatialNpc(14152));assertFalse(p.observesNpcDamage(14152));assertFalse(p.observesNpcDamage(14153));
  assertTrue(p.containsEncounterTile(new WorldPoint(2913,9569,0),true));
  assertFalse(p.containsEncounterTile(new WorldPoint(2913,9569,0),false));
  assertFalse(p.containsEncounterTile(new WorldPoint(2913,9526,0),true));
  assertFalse(p.containsEncounterTile(new WorldPoint(2913,9569,1),true));
  assertTrue(RoyalTitansProfile.completion("Your Royal Titans kill count is: 500."));
  assertFalse(RoyalTitansProfile.completion("Your Yama kill count is: 500."));
  assertEquals("royal_titans_visual",p.groundHazard(3211));assertNull(p.groundHazard(1233));
 }
 static void message(BossBoundaryTest.Harness h,String text){ChatMessage m=new ChatMessage();m.setType(ChatMessageType.GAMEMESSAGE);m.setMessage(text);h.plugin.onChatMessage(m);}
 @Test public void eitherTitanStartsOneEncounterAndFirstDeathDoesNotEndIt()throws Exception{
  for(int id:new int[]{12596,14147}){
   BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy first=new BossBoundaryTest.Enemy(id);
   h.target=first.npc;h.plugin.area=true;h.hit(first);h.tick();h.target=null;
   h.plugin.onActorDeath(new ActorDeath(first.npc));
   for(int i=0;i<40;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
   message(h,"Fight duration: 0:30.00. Personal best: 0:20.00");h.tick();assertTrue(h.plugin.saved.isEmpty());
   message(h,"Your Royal Titans kill count is: 500.");h.tick();assertTrue(h.plugin.saved.isEmpty());
   message(h,"Fight duration: 1:28.20. Personal best: 0:59.40");
   assertEquals(1,h.plugin.saved.size());assertEquals("Royal Titans",h.plugin.saved.get(0).get("label"));
   assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));assertNotNull(h.plugin.saved.get(0).get("officialTiming"));
  }
 }
 @Test public void exitGraceAndDeath()throws Exception{
  for(boolean death:new boolean[]{false,true}){
   BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(14147);
   h.target=boss.npc;h.plugin.area=true;h.hit(boss);h.tick();h.target=null;h.plugin.area=false;h.tick();assertTrue(h.plugin.saved.isEmpty());
   if(death)h.set("playerDeathPending",true);h.tick();if(!death)h.tick();
   assertEquals(1,h.plugin.saved.size());assertEquals(death?"player_death":"left_encounter",h.plugin.saved.get(0).get("endReason"));
  }
 }
}
