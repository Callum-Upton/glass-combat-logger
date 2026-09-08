package com.encounterledger;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MechanicsTest {
    @Test public void damageEvidenceIsLocalAndUsesExactMessages() {
        BossProfile y=YamaProfile.INSTANCE;
        assertEquals("standard_magic_cue",y.graphic(3247,true)[0]);
        assertEquals("standard_ranged_cue",y.graphic(3244,true)[0]);
        assertNull(y.graphic(3247,false));
        assertEquals("flare_player_impact",y.graphic(3242,true)[0]);
        assertEquals("fire_glyph_activation",y.announcement("@mes_hl_ora@You absorb a Glyph of Fire.")[0]);
        assertEquals("fire_streak_injury",y.announcement("You've been seared to the ground!")[0]);
        assertNull(y.announcement("Another player says You absorb a Glyph of Fire."));
    }

    @Test public void profilesAreOptInAndDoNotConfuseBasicAttacksWithSpecials() {
        assertNull(BossProfile.forNpc(1)); assertNull(BossProfile.forNpc(14179));
        BossProfile y=BossProfile.forNpc(14176);
        assertEquals("yama",y.id());
        assertNull(y.animation(1,12136)); assertNull(y.animation(14179,12134));
        assertEquals("void_flare_explode",y.animation(14179,12136)[0]);
        assertNull(y.graphic(3243,false)); assertNull(y.graphic(3246,false));
        assertEquals("shadow_root",y.projectile(3260)[0]);
        assertEquals("fire_special",y.projectile(3254)[0]);
        assertNull(y.graphic(3253,true));
        assertNull(y.announcement("Hello from another player"));
        assertEquals("shadow",y.announcement("Yama conjures shadow.")[2]);
    }
    @Test public void flareSpawnAndExplosionAndProjectileAreTickedWithoutRepeatedMovement() throws Exception {
        BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();h.hit(h.boss);h.tick();
        NPC flare=BossBoundaryTest.fake(NPC.class,m->{if(m.equals("getId"))return 14179;if(m.equals("getAnimation"))return 12136;return null;});
        h.plugin.onNpcSpawned(new NpcSpawned(flare));
        AnimationChanged animation=new AnimationChanged();animation.setActor(flare);h.plugin.onAnimationChanged(animation);
        Projectile projectile=BossBoundaryTest.fake(Projectile.class,m->{if(m.equals("getId"))return 3254;if(m.equals("getEndCycle"))return 10000;return null;});
        ProjectileMoved move=new ProjectileMoved();move.setProjectile(projectile);
        h.plugin.onProjectileMoved(move);h.plugin.onProjectileMoved(move);h.tick();
        h.plugin.onProjectileMoved(move);
        h.plugin.onActorDeath(new ActorDeath(h.boss.npc));h.tick();
        for(int i=0;i<16;i++)h.tick();
        Map<String,Object> log=h.plugin.saved.get(0);
        assertEquals("yama",((Map<?,?>)log.get("boss")).get("id"));
        List<?> events=(List<?>)BossBoundaryTest.ticks(log).get(1).get("events");
        assertEquals(3,events.size());
        assertEquals(1,((List<?>)BossBoundaryTest.ticks(log).get(2).get("events")).size());
    }
    @Test public void unrelatedFightNeverReceivesYamaEvents() throws Exception {
        BossBoundaryTest.Harness h=new BossBoundaryTest.Harness();
        BossBoundaryTest.Enemy other=new BossBoundaryTest.Enemy(1);h.target=other.npc;h.hit(other);h.tick();
        h.plugin.onNpcSpawned(new NpcSpawned(new BossBoundaryTest.Enemy(14179).npc));
        GameStateChanged state=new GameStateChanged();state.setGameState(GameState.LOGIN_SCREEN);h.plugin.onGameStateChanged(state);
        for(int i=0;i<16;i++)h.tick();
        Map<String,Object> log=h.plugin.saved.get(0);assertNull(log.get("boss"));assertNull(log.get("trailingEvents"));
    }
}
