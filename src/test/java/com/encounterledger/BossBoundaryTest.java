package com.encounterledger;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BossBoundaryTest
{
    interface Answer { Object get(String method); }
    @SuppressWarnings("unchecked") static <T> T fake(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)-> {
            Object value=answer.get(m.getName()); if(value!=null)return value;
            if(m.getReturnType()==boolean.class)return false;
            if(m.getReturnType()==int.class)return 0;
            return null;
        });
    }
    static class Capture extends EncounterLedgerPlugin {
        List<Map<String,Object>> saved=new ArrayList<>();
        @Override void saveEncounter(Map<String,Object> log) { saved.add(log); }
    }
    static class Enemy {
        boolean dead; int ratio=100;
        NPC npc;
        Enemy(int id) { npc=fake(NPC.class,m->{
            if(m.equals("getId"))return id;
            if(m.equals("getName"))return id==14176?"Yama":"Add";
            if(m.equals("isDead"))return dead;
            if(m.equals("getHealthRatio"))return ratio;
            return null;
        }); }
    }
    static class Harness {
        Capture plugin=new Capture();
        Enemy boss=new Enemy(14176);
        Actor target=boss.npc;
        int clock;
        Harness() throws Exception {
            Player player=fake(Player.class,m->{
                if(m.equals("getInteracting"))return target;
                if(m.equals("getWorldLocation"))return new WorldPoint(3200,3200,0);
                return null;
            });
            Client client=fake(Client.class,m->{
                if(m.equals("getLocalPlayer"))return player;
                if(m.equals("getGameState"))return GameState.LOGGED_IN;
                if(m.equals("isClientThread"))return true;
                if(m.equals("getBoostedSkillLevel")||m.equals("getRealSkillLevel"))return 99;
                if(m.equals("getTickCount"))return clock;
                return null;
            });
            set("client",client);
            set("config",new EncounterLedgerConfig(){});
        }
        void set(String name,Object value)throws Exception {Field f=EncounterLedgerPlugin.class.getDeclaredField(name);f.setAccessible(true);f.set(plugin,value);}
        void tick(){clock++;plugin.onGameTick(new GameTick());}
        void hit(Enemy e){HitsplatApplied event=new HitsplatApplied();event.setActor(e.npc);event.setHitsplat(fake(Hitsplat.class,m->{
            if(m.equals("isMine"))return true;
            if(m.equals("getHitsplatType"))return HitsplatID.DAMAGE_ME;
            if(m.equals("getAmount"))return 10;
            return null;
        }));plugin.onHitsplatApplied(event);}
    }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> ticks(Map<String,Object> log){return (List<Map<String,Object>>)log.get("ticks");}
    @Test public void preRollIsBoundedAndDoesNotExtendDeathTail()throws Exception {
        Harness h=new Harness();for(int i=0;i<20;i++)h.tick();h.hit(h.boss);h.tick();
        h.plugin.onActorDeath(new ActorDeath(h.boss.npc));h.tick();for(int i=0;i<16;i++)h.tick();
        Map<String,Object> log=h.plugin.saved.get(0);assertEquals(15,log.get("combatStartTick"));
        assertEquals(17,ticks(log).size());assertEquals(16,log.get("endTick"));
        for(int i=0;i<17;i++)assertEquals(i,ticks(log).get(i).get("tick"));
    }
    @Test public void killKeepsFinalHitAndDeathTickAndNoIdleTail()throws Exception {
        Harness h=new Harness();h.hit(h.boss);h.tick();
        h.plugin.onActorDeath(new ActorDeath(h.boss.npc));
        h.hit(h.boss);h.tick();
        for(int i=0;i<16;i++)h.tick();
        assertEquals(1,h.plugin.saved.size());
        Map<String,Object> log=h.plugin.saved.get(0);
        assertEquals("boss_death",log.get("endReason"));assertEquals(1,log.get("endTick"));
        assertEquals(2,ticks(log).size());
        List<?> events=(List<?>)ticks(log).get(1).get("events");assertEquals(2,events.size());
        for(int i=0;i<20;i++){h.hit(h.boss);h.tick();}
        for(int i=0;i<16;i++)h.tick();
        assertEquals(1,h.plugin.saved.size());assertEquals(2,ticks(log).size());
    }
    @Test public void addsAndLivingDespawnDoNotEndFight()throws Exception {
        Harness h=new Harness();h.hit(h.boss);h.tick();
        Enemy judge=new Enemy(14180);h.hit(judge);
        h.plugin.onActorDeath(new ActorDeath(judge.npc));h.plugin.onNpcDespawned(new NpcDespawned(h.boss.npc));h.tick();
        assertTrue(h.plugin.saved.isEmpty());
        h.plugin.onActorDeath(new ActorDeath(h.boss.npc));h.tick();for(int i=0;i<16;i++)h.tick();assertEquals(1,h.plugin.saved.size());
    }
    @Test public void oneHitKillWorksWithoutSelectedTarget()throws Exception {
        Harness h=new Harness();h.target=null;h.hit(h.boss);h.plugin.onActorDeath(new ActorDeath(h.boss.npc));h.tick();
        for(int i=0;i<16;i++)h.tick();
        assertEquals(1,ticks(h.plugin.saved.get(0)).size());assertEquals("Yama",h.plugin.saved.get(0).get("label"));
    }
    @Test public void zeroHpSnapshotAndNextFightAreHandled()throws Exception {
        Harness h=new Harness();h.hit(h.boss);h.tick();h.boss.ratio=0;h.tick();
        for(int i=0;i<16;i++)h.tick();
        assertEquals("zero_hp_snapshot",h.plugin.saved.get(0).get("endEvidence"));
        Enemy next=new Enemy(14176);h.target=next.npc;h.hit(next);h.tick();
        h.plugin.onActorDeath(new ActorDeath(next.npc));h.tick();for(int i=0;i<16;i++)h.tick();assertEquals(2,h.plugin.saved.size());
        assertEquals(0,ticks(h.plugin.saved.get(1)).get(0).get("tick"));
    }
    @Test public void unrelatedYamaDeathDoesNotEndLocalFight()throws Exception {
        Harness h=new Harness();h.hit(h.boss);h.tick();h.plugin.onActorDeath(new ActorDeath(new Enemy(14176).npc));h.tick();
        assertTrue(h.plugin.saved.isEmpty());
    }
}
