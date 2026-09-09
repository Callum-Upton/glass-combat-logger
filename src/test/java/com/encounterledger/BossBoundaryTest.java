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
        Boolean area;
        @Override Boolean encounterArea(Player player,BossProfile profile) { return area; }
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
    @Test public void transitionWithoutCombatSurvivesIdleThenExitEnds()throws Exception {
        Harness h=new Harness();h.plugin.area=true;h.hit(h.boss);h.tick();h.target=null;
        for(int i=0;i<80;i++)h.tick();
        assertTrue(h.plugin.saved.isEmpty());
        h.plugin.area=false;h.tick();assertTrue(h.plugin.saved.isEmpty());h.tick();h.tick();
        assertEquals(1,h.plugin.saved.size());assertEquals("left_encounter",h.plugin.saved.get(0).get("endReason"));
    }
    @Test public void areaRetentionDoesNotOverrideBossDeath()throws Exception {
        Harness h=new Harness();h.plugin.area=true;h.hit(h.boss);h.tick();h.target=null;
        for(int i=0;i<40;i++)h.tick();h.plugin.onActorDeath(new ActorDeath(h.boss.npc));h.tick();
        for(int i=0;i<16;i++)h.tick();assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));
    }
    @Test public void unknownAreaRetainsIdleFallback()throws Exception {
        Harness h=new Harness();h.hit(h.boss);h.tick();h.target=null;
        for(int i=0;i<16;i++)h.tick();assertEquals("idle_timeout",h.plugin.saved.get(0).get("endReason"));
    }
    @Test public void delayedPlayerDeathWinsOverExit()throws Exception {
        Harness h=new Harness();h.plugin.area=true;h.hit(h.boss);h.tick();h.plugin.area=false;h.tick();
        h.set("playerDeathPending",true);h.tick();assertEquals("player_death",h.plugin.saved.get(0).get("endReason"));
    }
    @Test public void scurriusAreaSupportsPublicAndPrivateAndEndsPerKill()throws Exception {
        assertSame(ScurriusProfile.INSTANCE,BossProfile.forNpc(7221));assertSame(ScurriusProfile.INSTANCE,BossProfile.forNpc(7222));
        assertTrue(ScurriusProfile.INSTANCE.containsEncounterTile(new WorldPoint(3296,9872,0),true));
        assertTrue(ScurriusProfile.INSTANCE.containsEncounterTile(new WorldPoint(3296,9872,0),false));
        assertFalse(ScurriusProfile.INSTANCE.containsEncounterTile(new WorldPoint(3275,9872,0),false));
        Harness h=new Harness();Enemy rat=new Enemy(7221);h.target=rat.npc;h.plugin.area=true;h.hit(rat);h.tick();h.target=null;
        for(int i=0;i<40;i++)h.tick();assertTrue(h.plugin.saved.isEmpty());
        h.plugin.onActorDeath(new ActorDeath(rat.npc));h.tick();assertEquals("boss_death",h.plugin.saved.get(0).get("endReason"));
        for(int i=0;i<20;i++)h.tick();assertEquals(1,h.plugin.saved.size());
        Enemy next=new Enemy(7221);h.target=next.npc;h.hit(next);h.tick();h.plugin.onActorDeath(new ActorDeath(next.npc));h.tick();assertEquals(2,h.plugin.saved.size());
    }
    @Test public void yamaAreaIncludesJudgeAndRejectsOutsideOrNonInstance() {
        assertTrue(YamaProfile.INSTANCE.containsEncounterTile(new WorldPoint(1507,10084,0),true));
        assertTrue(YamaProfile.INSTANCE.containsEncounterTile(new WorldPoint(1480,10091,0),true));
        assertTrue(YamaProfile.INSTANCE.containsEncounterTile(new WorldPoint(1478,10097,0),true));
        assertFalse(YamaProfile.INSTANCE.containsEncounterTile(new WorldPoint(1507,10084,0),false));
        assertFalse(YamaProfile.INSTANCE.containsEncounterTile(new WorldPoint(1507,10084,1),true));
        assertFalse(YamaProfile.INSTANCE.containsEncounterTile(new WorldPoint(3200,3200,0),true));
    }
}
