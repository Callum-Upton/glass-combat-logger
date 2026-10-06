package com.encounterledger;
import org.junit.Test;import static org.junit.Assert.*;
import java.util.*;import net.runelite.api.*;import net.runelite.api.coords.WorldPoint;import net.runelite.api.events.*;
import static com.encounterledger.ManualRaidCaptureTest.*;
public class ToaCaptureTest {
 @Test public void instanceEntryQuietRoomsExitAndRollover(){
  ToaCapture c=new ToaCapture();assertFalse(c.poll(14160,false));assertFalse(c.poll(ToaCapture.LOBBY,false));assertTrue(c.poll(14160,true));assertTrue(c.observedEntry);String session=c.sessionId;
  for(int region:ToaCapture.REGIONS){assertFalse(c.poll(region,true));assertNull(c.endReason(50,100));}
  assertEquals("length_limit",c.endReason(50,9000));c.finish("length_limit");assertTrue(c.active);assertEquals(session,c.sessionId);assertEquals(2,c.part);
  for(int i=0;i<5;i++)c.poll(-1,false);assertNull(c.endReason(51,1));
  c.poll(13454,false);c.poll(13454,false);assertNull(c.endReason(52,2));c.poll(13454,false);assertEquals("left_raid",c.endReason(53,3));
 }
 @Test public void totalTimeNotRoomOrChallengeTimeEndsRaid(){
  ToaCapture c=new ToaCapture();c.poll(15184,true);assertFalse(c.observedEntry);
  c.message("Challenge complete: The Wardens. Duration: 8:08Tombs of Amascut: Expert Mode challenge completion time: 28:52. Personal best: 27:46",10);assertNull(c.endReason(100,100));
  c.message("Tombs of Amascut: Expert Mode total completion time: 32:19. Personal best: 30:55",11);assertEquals(1939000L,c.timing.get("durationMs"));assertEquals("expert",c.timing.get("mode"));
  c.message("Your completed Tombs of Amascut: Expert Mode count is: 262.",12);assertNull(c.endReason(13,100));assertEquals("raid_complete",c.endReason(14,100));c.finish("raid_complete");assertFalse(c.poll(14672,true));assertFalse(c.poll(14160,true));
  c.poll(13454,false);assertTrue(c.poll(14160,true));
 }
 @Test public void completionGrammarRejectsSpoofAndInvalidTime(){
  for(String bad:Arrays.asList("Player: Tombs of Amascut total completion time: 32:19", "Tombs of Amascut total completion time: 32:99", "Challenge complete: Akkha. Duration: 4:04. Total: 16:40", "Your completed Tombs of Amascut: Expert Mode count is: 262.")){
   ToaCapture c=new ToaCapture();c.poll(14676,true);c.message(bad,1);assertNull(c.endReason(20,50));
  }
  for(String mode:Arrays.asList("",": Entry Mode",": Expert Mode")){ToaCapture c=new ToaCapture();c.poll(15184,true);c.message("<col=ff0000>Tombs of Amascut"+mode+" total completion time: 60:01.20</col>",1);assertEquals(3601200L,c.timing.get("durationMs"));}
 }
 @Test @SuppressWarnings("unchecked") public void actualRecorderKeepsDeathAndRoomChangesTogether()throws Exception{
  int[] region={14160},time={0},hp={99};boolean[] inside={true};List<Map<String,Object>> saved=new ArrayList<>();
  WorldView view=fake(WorldView.class,(m,a)->null);
  Player p=fake(Player.class,(m,a)->m.equals("getName")?"Synthetic tester":m.equals("getWorldView")?view:m.equals("getWorldLocation")?new WorldPoint((region[0]>>8)*64+5,(region[0]&255)*64+5,region[0]==14676?1:0):null);
  Client client=fake(Client.class,(m,a)->{if(m.equals("getLocalPlayer"))return p;if(m.equals("getGameState"))return GameState.LOGGED_IN;if(m.equals("isInInstancedRegion"))return inside[0];if(m.equals("isClientThread"))return true;if(m.equals("getTickCount"))return time[0];if(m.equals("getBoostedSkillLevel"))return a[0]==Skill.HITPOINTS?hp[0]:50;if(m.equals("getRealSkillLevel"))return 99;return null;});
  EncounterLedgerPlugin plugin=new EncounterLedgerPlugin(){@Override void saveEncounter(Map<String,Object> log){saved.add(log);}};set(plugin,"client",client);set(plugin,"config",new EncounterLedgerConfig(){});
  for(int i=0;i<40;i++){time[0]++;region[0]=i<10?14160:i<20?14676:15184;hp[0]=i==15?0:99;plugin.onGameTick(new GameTick());}assertTrue(saved.isEmpty());
  ChatMessage chat=new ChatMessage();chat.setType(ChatMessageType.PUBLICCHAT);chat.setMessage("Tombs of Amascut: Expert Mode total completion time: 32:19. Personal best: 30:55");plugin.onChatMessage(chat);assertNull(((ToaCapture)get(plugin,"toa")).timing);
  chat.setType(ChatMessageType.GAMEMESSAGE);plugin.onChatMessage(chat);for(int i=0;i<3;i++){time[0]++;plugin.onGameTick(new GameTick());}
  assertEquals(1,saved.size());assertEquals("raid_complete",saved.get(0).get("endReason"));assertEquals("toa",((Map<?,?>)saved.get(0).get("boss")).get("id"));assertEquals(43,((List<?>)saved.get(0).get("ticks")).size());
 }
}
