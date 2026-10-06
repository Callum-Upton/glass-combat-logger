package com.encounterledger;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
public class ManualRaidCaptureTest {
    @Test public void commandAndSessionBoundaries(){
        assertEquals("start",ManualRaidCapture.command(new String[]{"RAID","START"}));
        assertNull(ManualRaidCapture.command(new String[]{"raid","start","extra"}));
        assertNull(ManualRaidCapture.command(new String[]{"research","on"}));
        ManualRaidCapture s=new ManualRaidCapture();s.start();String id=s.sessionId;s.start();assertEquals(id,s.sessionId);
        s.finish("length_limit");assertEquals(id,s.sessionId);assertEquals(2,s.part);
        s.finish("connection_lost");assertFalse(s.active());s.start();assertNotEquals(id,s.sessionId);
    }
    static void set(Object o,String name,Object value)throws Exception{Field f=EncounterLedgerPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static Object get(Object o,String name)throws Exception{Field f=EncounterLedgerPlugin.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @SuppressWarnings("unchecked") static <T>T fake(Class<T> c,java.util.function.BiFunction<String,Object[],Object> answer){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},(o,m,a)->{Object r=answer.apply(m.getName(),a);if(r!=null)return r;if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==boolean.class)return false;return null;});}
    @Test @SuppressWarnings("unchecked") public void quietRoomsDeathAndLongRunsStayInAnExplicitSession()throws Exception{
        int[] time={0},hp={99};List<Map<String,Object>> saved=new ArrayList<>();
        Player p=fake(Player.class,(m,a)->m.equals("getName")?"Synthetic tester":m.equals("getWorldLocation")?new WorldPoint(3200,3200,0):null);
        Client client=fake(Client.class,(m,a)->{
            if(m.equals("getLocalPlayer"))return p;if(m.equals("getGameState"))return GameState.LOGGED_IN;if(m.equals("isClientThread"))return true;
            if(m.equals("getTickCount"))return time[0];if(m.equals("getBoostedSkillLevel"))return a[0]==Skill.HITPOINTS?hp[0]:50;if(m.equals("getRealSkillLevel"))return 99;return null;
        });
        EncounterLedgerPlugin plugin=new EncounterLedgerPlugin(){@Override void saveEncounter(Map<String,Object> log){saved.add(log);}};
        set(plugin,"client",client);set(plugin,"config",fake(EncounterLedgerConfig.class,(m,a)->m.equals("idleTicks")?15:null));
        ManualRaidCapture session=(ManualRaidCapture)get(plugin,"manualRaid");session.start();String id=session.sessionId;
        for(int i=0;i<ManualRaidCapture.MAX_PART_TICKS+1;i++){time[0]++;hp[0]=i==25?0:99;plugin.onGameTick(new GameTick());}
        assertEquals(1,saved.size());assertEquals("length_limit",saved.get(0).get("endReason"));assertFalse(saved.get(0).containsKey("officialTiming"));assertFalse(saved.get(0).containsKey("boss"));
        assertEquals(ManualRaidCapture.MAX_PART_TICKS,((List<?>)saved.get(0).get("ticks")).size());
        Map<String,Object> current=(Map<String,Object>)get(plugin,"encounter");Map<?,?> context=(Map<?,?>)current.get("manualRaidCapture");assertEquals(id,context.get("sessionId"));assertEquals(2,context.get("part"));assertEquals(1,((List<?>)current.get("ticks")).size());
    }
}
