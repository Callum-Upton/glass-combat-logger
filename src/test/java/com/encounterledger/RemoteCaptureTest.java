package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.coords.WorldPoint;
import static com.encounterledger.ManualRaidCaptureTest.*;

public class RemoteCaptureTest {
    static String json(long now,int revision){return "{\"schemaVersion\":1,\"revision\":"+revision+",\"expiresAt\":\""+java.time.Instant.ofEpochSecond(now+86400)+"\",\"enabled\":true,\"bossId\":\"fractured_archive\",\"startMessages\":[\"Synthetic raid start\"],\"completionPrefixes\":[\"The Fractured Archive completion time: \"],\"startRegions\":[1234],\"exitRegions\":[1235],\"planes\":[0]}";}
    @Test public void messagesNeedContextAndFullRaidTime(){
        CaptureRules r=CaptureRules.parse(json(100,1),100);
        assertTrue(r.starts("<col=ff0000>Synthetic raid start</col>",1234,0,100));
        assertFalse(r.starts("Synthetic raid start",1235,0,100));assertFalse(r.starts("Synthetic raid start",1234,1,100));
        assertFalse(r.starts("Synthetic raid start",1234,0,86500));assertFalse(r.starts("Player: Synthetic raid start",1234,0,100));
        assertNull(r.completion("Challenge complete: Guardian. Duration: 1:00"));assertNull(r.completion("The Fractured Archive completion time: 1:99"));
        assertNotNull(r.completion("Room complete<br>The Fractured Archive completion time: 25:40.20. Personal best: 24:00"));
        for(String bad:Arrays.asList(json(100,1).replace("1234","-1"),json(100,1).replace("1234","18446744073709551617"),json(100,1).replace("true","\"true\""),json(100,1).replace("\"startRegions\":[1234]","\"startRegions\":[]"))) {
            try{CaptureRules.parse(bad,100);fail("Invalid manifest accepted");}catch(RuntimeException expected){}
        }
    }
    @Test public void pinnedSessionGraceRolloverAndExit(){
        CaptureRules first=CaptureRules.parse(json(100,1),100),next=CaptureRules.parse(json(100,2),100);
        RemoteRaidCapture s=new RemoteRaidCapture();s.start(first);String id=s.sessionId;
        assertSame(first,s.rules);assertNotSame(next,s.rules);
        assertEquals("length_limit",s.endReason(1,9999,0,9000));s.finish("length_limit");assertEquals(id,s.sessionId);assertEquals(2,s.part);
        s.message("The Fractured Archive completion time: 20:00",10);s.message("The Fractured Archive completion time: 20:00",12);
        assertNull(s.endReason(12,1234,0,30));assertEquals("raid_complete",s.endReason(13,1234,0,31));s.finish("raid_complete");assertFalse(s.active());
        s.start(next);for(int i=0;i<5;i++)assertNull(s.endReason(i,1235,1,10));
        assertNull(s.endReason(6,1235,0,10));assertNull(s.endReason(7,1235,0,11));assertEquals("left_raid",s.endReason(8,1235,0,12));
    }
    @Test public void localOverridesAreExplicitAndDoNotNeedNetwork(){
        assertNull(CaptureRules.local("",""));assertNull(CaptureRules.local("The raid has begun!","Duration:"));
        CaptureRules r=CaptureRules.local("Synthetic raid start","The Fractured Archive completion time:");
        assertTrue(r.localOverride());assertTrue(r.starts("Synthetic raid start",-1,-1,100));
        assertNotNull(r.completion("The Fractured Archive completion time: 2:00"));assertNull(r.completion("Player: The Fractured Archive completion time: 2:00"));
    }
    @Test public void downloaderConsentCacheAndMalformedResponse()throws Exception{
        boolean[] allow={false};String[] body={json(java.time.Instant.now().getEpochSecond(),1)};List<okhttp3.Request> requests=new ArrayList<>();
        EncounterLedgerConfig config=new EncounterLedgerConfig(){public boolean allowNetworking(){return allow[0];}public String connectionKey(){return "zy_"+"a".repeat(43);}};
        okhttp3.OkHttpClient shared=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{requests.add(chain.request());return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),body[0])).build();}).build();
        Path dir=Files.createTempDirectory("zenyte-rule-test"),cache=dir.resolve("rules.json");
        try(CaptureRuleClient c=new CaptureRuleClient(config,shared,cache)){
            c.refresh();assertTrue(requests.isEmpty());assertNull(c.current());
            allow[0]=true;c.refresh();assertEquals(1,c.current().revision);assertTrue(Files.exists(cache));
            assertEquals("https://zenyte.gg/api/plugin/capture-rules",requests.get(0).url().toString());assertNull(requests.get(0).header("Authorization"));
            body[0]="{}";c.refresh();assertEquals(1,c.current().revision);
            body[0]=json(java.time.Instant.now().getEpochSecond(),0);c.refresh();assertEquals(1,c.current().revision);
            allow[0]=false;c.refresh();assertEquals(3,requests.size());assertNull(c.current());
        }finally{Files.deleteIfExists(cache);Files.deleteIfExists(dir.resolve("rules.json.tmp"));Files.deleteIfExists(dir);}
    }
    @Test @SuppressWarnings("unchecked") public void actualRecorderIgnoresPlayerChatKeepsDeathsAndCompletesOnce()throws Exception{
        int[] time={0},hp={99};List<Map<String,Object>> saved=new ArrayList<>();
        Player p=fake(Player.class,(m,a)->m.equals("getName")?"Synthetic tester":m.equals("getWorldLocation")?new WorldPoint(3200,3200,0):null);
        Client client=fake(Client.class,(m,a)->{if(m.equals("getLocalPlayer"))return p;if(m.equals("getGameState"))return GameState.LOGGED_IN;if(m.equals("isClientThread"))return true;if(m.equals("getTickCount"))return time[0];if(m.equals("getBoostedSkillLevel"))return a[0]==Skill.HITPOINTS?hp[0]:50;if(m.equals("getRealSkillLevel"))return 99;return null;});
        EncounterLedgerPlugin plugin=new EncounterLedgerPlugin(){@Override void saveEncounter(Map<String,Object> log){saved.add(log);}};
        set(plugin,"client",client);set(plugin,"config",new EncounterLedgerConfig(){public boolean archiveLocalTriggers(){return true;}public String archiveStartMessage(){return "Synthetic raid start";}public String archiveEndMessage(){return "The Fractured Archive completion time:";}});
        ChatMessage chat=new ChatMessage();chat.setType(ChatMessageType.PUBLICCHAT);chat.setMessage("Synthetic raid start");plugin.onChatMessage(chat);plugin.onGameTick(new GameTick());assertNull(get(plugin,"encounter"));
        chat.setType(ChatMessageType.GAMEMESSAGE);plugin.onChatMessage(chat);
        for(int i=0;i<25;i++){time[0]++;hp[0]=i==10?0:99;plugin.onGameTick(new GameTick());}assertTrue(saved.isEmpty());
        Map<String,Object> log=(Map<String,Object>)get(plugin,"encounter");assertEquals("fractured_archive",((Map<?,?>)log.get("boss")).get("id"));
        chat.setMessage("The Fractured Archive completion time: 25:00");plugin.onChatMessage(chat);
        for(int i=0;i<3;i++){time[0]++;plugin.onGameTick(new GameTick());}
        assertEquals(1,saved.size());assertEquals("raid_complete",saved.get(0).get("endReason"));assertFalse(saved.get(0).containsKey("officialTiming"));
        plugin.onChatMessage(chat);time[0]++;plugin.onGameTick(new GameTick());assertEquals(1,saved.size());assertNull(get(plugin,"encounter"));
    }
    @Test @SuppressWarnings("unchecked") public void downloadedStymphikeMessagesCanArriveInSameTick()throws Exception{
        int[] time={0},hp={99};List<Map<String,Object>> saved=new ArrayList<>();
        Player p=fake(Player.class,(m,a)->m.equals("getName")?"Synthetic tester":m.equals("getWorldLocation")?new WorldPoint(3200,3200,0):null);
        Client client=fake(Client.class,(m,a)->{if(m.equals("getLocalPlayer"))return p;if(m.equals("getGameState"))return GameState.LOGGED_IN;if(m.equals("isClientThread"))return true;if(m.equals("getTickCount"))return time[0];if(m.equals("getBoostedSkillLevel"))return a[0]==Skill.HITPOINTS?hp[0]:50;if(m.equals("getRealSkillLevel"))return 99;return null;});
        EncounterLedgerPlugin plugin=new EncounterLedgerPlugin(){@Override void saveEncounter(Map<String,Object> log){saved.add(log);}};
                String definition=json(java.time.Instant.now().getEpochSecond(),2).replace("fractured_archive","capture_test").replace("Synthetic raid start","You caught a stymphike!").replace("The Fractured Archive completion time: ","Untradeable drop: Stymphike carcass");
        EncounterLedgerConfig cfg=new EncounterLedgerConfig(){public boolean allowNetworking(){return true;}public String connectionKey(){return "zy_"+"a".repeat(43);}public boolean captureTriggerTest(){return true;}public boolean liveLogging(){return true;}public boolean autoUpload(){return true;}};
        CaptureRuleClient rules=new CaptureRuleClient(cfg,new okhttp3.OkHttpClient(),java.nio.file.Paths.get("unused-test-cache"));
        java.lang.reflect.Field current=CaptureRuleClient.class.getDeclaredField("current");current.setAccessible(true);current.set(rules,CaptureRules.parse(definition,java.time.Instant.now().getEpochSecond()));
        List<String> sends=new ArrayList<>();LiveUploader uploads=new LiveUploader(new com.google.gson.Gson(),text->{},(endpoint,key,body)->sends.add(endpoint),cfg);
        set(plugin,"client",client);set(plugin,"config",cfg);set(plugin,"captureRuleClient",rules);set(plugin,"liveUploader",uploads);
        ChatMessage chat=new ChatMessage();chat.setType(ChatMessageType.PUBLICCHAT);chat.setMessage("You caught a stymphike!");plugin.onChatMessage(chat);plugin.onGameTick(new GameTick());assertNull(get(plugin,"encounter"));
        chat.setType(ChatMessageType.GAMEMESSAGE);plugin.onChatMessage(chat);
        chat.setMessage("<col=ff0000>Untradeable drop: Stymphike carcass</col>");plugin.onChatMessage(chat);
        plugin.onGameTick(new GameTick());assertTrue(saved.isEmpty());
        Map<String,Object> log=(Map<String,Object>)get(plugin,"encounter");assertFalse(log.containsKey("boss"));assertEquals(true,log.get("captureTest"));
        chat.setMessage("Untradeable drop: Stymphike carcass");plugin.onChatMessage(chat);
        for(int i=0;i<3;i++){time[0]++;plugin.onGameTick(new GameTick());}
        assertEquals(1,saved.size());assertEquals("capture_test_complete",saved.get(0).get("endReason"));assertFalse(saved.get(0).containsKey("officialTiming"));
        plugin.onChatMessage(chat);time[0]++;plugin.onGameTick(new GameTick());assertEquals(1,saved.size());assertNull(get(plugin,"encounter"));
    }

}
