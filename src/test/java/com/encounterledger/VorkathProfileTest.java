package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.ChatMessageType;

public class VorkathProfileTest {
 @Test public void areaAndCuesAreScoped(){
  VorkathProfile p=VorkathProfile.INSTANCE;
  assertSame(p,BossProfile.forNpc(8061));assertNull(BossProfile.forNpc(8060));
  assertTrue(p.containsEncounterTile(new WorldPoint(2282,4063,0),true));
  assertTrue(p.containsEncounterTile(new WorldPoint(2282,4064,0),true));
  assertFalse(p.containsEncounterTile(new WorldPoint(1923,7050,1),true));
  assertFalse(p.containsEncounterTile(new WorldPoint(2282,4064,0),false));
  assertEquals("ranged",p.projectile(1477)[0]);assertEquals("magic",p.projectile(1479)[0]);
  assertNull(p.animation(8063,7951));assertNull(p.projectile(2337));
 }
 @Test public void acidPauseAndDelayedTimingKeepOneFight()throws Exception{
  BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();
  BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(8061);
  h.target=boss.npc;h.plugin.area=true;h.hit(boss);h.tick();h.target=null;
  for(int i=0;i<80;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
  h.plugin.onActorDeath(new ActorDeath(boss.npc));h.tick();
  for(int i=0;i<6;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
  ChatMessage message=new ChatMessage();message.setType(ChatMessageType.GAMEMESSAGE);message.setMessage("Fight duration: 1:49.80. Personal best: 1:10.80");
  h.plugin.onChatMessage(message);
  assertEquals(1,h.plugin.saved.size());assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));
  assertNotNull(h.plugin.saved.get(0).get("officialTiming"));assertEquals(82,BossBoundaryTest.ticks(h.plugin.saved.get(0)).size());
 }
 @Test public void exitAndDeathUseEncounterGrace()throws Exception{
  for(boolean death:new boolean[]{false,true}){
   BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(8061);
   h.target=boss.npc;h.plugin.area=true;h.hit(boss);h.tick();h.target=null;h.plugin.area=false;h.tick();assertTrue(h.plugin.saved.isEmpty());
   if(death)h.set("playerDeathPending",true);h.tick();if(!death){assertTrue(h.plugin.saved.isEmpty());h.tick();}
   assertEquals(1,h.plugin.saved.size());assertEquals(death?"player_death":"left_encounter",h.plugin.saved.get(0).get("endReason"));
  }
 }
 @Test public void postKillSpawnCannotAcquireBossKillTime()throws Exception{
  BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();BossBoundaryTest.Enemy boss=new BossBoundaryTest.Enemy(8061);
  h.target=boss.npc;h.plugin.area=true;h.hit(boss);h.tick();h.plugin.onActorDeath(new ActorDeath(boss.npc));h.tick();h.target=null;
  for(int i=0;i<16;i++)h.tick();assertEquals(1,h.plugin.saved.size());int size=BossBoundaryTest.ticks(h.plugin.saved.get(0)).size();
  h.set("config",new EncounterLedgerConfig(){@Override public boolean combinedCapture(){return true;}}); // Explicit research can retain a standalone spawn.
  h.plugin.area=null; // Generic spawn has no recognised encounter area.
  BossBoundaryTest.Enemy spawn=new BossBoundaryTest.Enemy(8063);h.target=spawn.npc;h.hit(spawn);h.tick();h.target=null;
  for(int i=0;i<16;i++)h.tick();assertEquals(2,h.plugin.saved.size());
  assertEquals(size,BossBoundaryTest.ticks(h.plugin.saved.get(0)).size());assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));
  assertEquals("idle_timeout",h.plugin.saved.get(1).get("endReason"));assertNull(h.plugin.saved.get(1).get("boss"));assertNull(h.plugin.saved.get(1).get("officialTiming"));
 }
}
