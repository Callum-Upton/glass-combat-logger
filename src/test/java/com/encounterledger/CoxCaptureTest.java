package com.encounterledger;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CoxCaptureTest {
    @Test public void distinguishesLobbyStartAndPartialJoin() {
        CoxCapture c=new CoxCapture();
        assertNull(c.poll(true,0)); assertEquals(Boolean.TRUE,c.poll(true,1));
        c.ended(); assertNull(c.poll(true,1));
        c.poll(false,0); assertEquals(Boolean.FALSE,c.poll(true,1));
        c.reset(); c.message("The raid has begun!"); assertEquals(Boolean.TRUE,c.poll(true,1));
    }
    @Test public void parsesCompletionAndLimitsChatCapture() {
        CoxCapture c=new CoxCapture(); c.poll(true,0); c.poll(true,1);
        c.message("Map Layout: Challenge Mode (Full).");
        assertEquals("Challenge Mode (Full).",c.layout);
        c.message("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>2 players</col> Duration: <col=ff0000>23:25.20</col> Personal best: 19:38.40");
        assertTrue(c.complete); assertEquals(1405200L,c.timing.get("durationMs"));
        assertEquals(2,c.timing.get("teamSize"));
        assertFalse(CoxCapture.recognized("hello clan"));
    }
    private static void set(Object o,String name,Object v)throws Exception {
        Field f=EncounterLedgerPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(o,v);
    }
    private static Object get(Object o,String name)throws Exception {
        Field f=EncounterLedgerPlugin.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    interface Answer { Object value(String method,Object[] args); }
    @SuppressWarnings("unchecked") private static <T>T fake(Class<T> type,Answer answer) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            Object v=answer.value(m.getName(),a);if(v!=null)return v;
            if(m.getReturnType()==boolean.class)return false;
            if(m.getReturnType()==int.class)return 0;return null;
        });
    }
    static class Recorder extends EncounterLedgerPlugin {
        Map<String,Object> saved;
        @Override void saveEncounter(Map<String,Object> log){saved=log;}
    }
    @Test public void recordsQuietRoomsDeathAndCompletionBeyondGenericLimit()throws Exception {
        int[] state={0}, inside={1}, tick={0}, projectileReads={0};
        Player player=fake(Player.class,(m,a)->m.equals("getWorldLocation")?new WorldPoint(3200,3200,0):null);
        Client client=fake(Client.class,(m,a)->{
            if(m.equals("getLocalPlayer"))return player;
            if(m.equals("getGameState"))return GameState.LOGGED_IN;
            if(m.equals("isClientThread"))return true;
            if(m.equals("getTickCount"))return tick[0];
            if(m.equals("getProjectiles")){projectileReads[0]++;return null;}
            if(m.equals("getBoostedSkillLevel")||m.equals("getRealSkillLevel"))return 99;
            if(m.equals("getVarbitValue"))return ((Integer)a[0])==5432?inside[0]:((Integer)a[0])==5425?state[0]:0;
            return null;
        });
        Recorder p=new Recorder();set(p,"client",client);
        set(p,"config",fake(EncounterLedgerConfig.class,(m,a)->m.equals("idleTicks")?15:null));
        p.onGameTick(new GameTick()); assertNull(get(p,"encounter"));
        state[0]=1;
        for(int i=0;i<2010;i++){tick[0]++;p.onGameTick(new GameTick());}
        assertNotNull(get(p,"encounter")); assertNull(p.saved);
        assertTrue("CoX requests raw projectiles without an individual boss profile",projectileReads[0]>0);
        p.onActorDeath(new ActorDeath(player));
        set(p,"bossDeathPending",true);p.onGameTick(new GameTick());assertNull(p.saved);
        ChatMessage message=new ChatMessage();message.setType(ChatMessageType.FRIENDSCHAT);
        message.setMessage("Congratulations - your raid is complete! Team size: 2 players Duration: 25:46.20");
        p.onChatMessage(message);p.onGameTick(new GameTick());assertNull(p.saved);
        message.setType(ChatMessageType.FRIENDSCHATNOTIFICATION);p.onChatMessage(message);
        p.onGameTick(new GameTick());assertEquals("raid_complete",p.saved.get("endReason"));
        assertEquals("Chambers of Xeric",p.saved.get("label"));
        p.onGameTick(new GameTick());assertNull(get(p,"encounter"));
        // Reset in the lobby, start a new raid, and allow an exit grace period.
        state[0]=0;p.onGameTick(new GameTick());state[0]=1;p.onGameTick(new GameTick());p.saved=null;
        inside[0]=0;p.onGameTick(new GameTick());p.onGameTick(new GameTick());assertNull(p.saved);
        p.onGameTick(new GameTick());assertEquals("left_raid",p.saved.get("endReason"));
        inside[0]=1;state[0]=0;p.onGameTick(new GameTick());state[0]=1;p.onGameTick(new GameTick());p.saved=null;
        GameStateChanged lost=new GameStateChanged();lost.setGameState(GameState.CONNECTION_LOST);p.onGameStateChanged(lost);
        assertEquals("connection_lost",p.saved.get("endReason"));
    }
}
