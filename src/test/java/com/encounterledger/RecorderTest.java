package com.encounterledger;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecorderTest
{
    interface Answer { Object get(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Answer answer)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            Object value = answer.get(method.getName(), args);
            if (value != null) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
    }
    private static Object hitAnswer(String method, int amount) { if (method.equals("isMine")) return true; if (method.equals("getAmount")) return amount; if (method.equals("getHitsplatType")) return HitsplatID.DAMAGE_ME; return null; }
    private static void field(Object owner, String name, Object value) throws Exception
    { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> ticks(EncounterLedgerPlugin plugin) throws Exception
    { Field field = plugin.getClass().getDeclaredField("ticks"); field.setAccessible(true); return (List<Map<String,Object>>)field.get(plugin); }
    @Test public void capturesFirstHitAndSameTickHealingWithoutLosingQuietTicks() throws Exception
    {
        int[] hp = {70};
        List<String> chat = new ArrayList<>();
        NPC npc = proxy(NPC.class, (method, args) -> method.equals("getName") ? "Target" : null);
        Player player = proxy(Player.class, (method, args) -> {
            if (method.equals("getInteracting")) return npc;
            if (method.equals("getWorldLocation")) return new WorldPoint(3200,3200,0);
            return null;
        });
        Client client = proxy(Client.class, (method, args) -> {
            if (method.equals("isClientThread")) return true;
            if (method.equals("addChatMessage")) { chat.add((String)args[2]); return null; }
            if (method.equals("getLocalPlayer")) return player;
            if (method.equals("getGameState")) return GameState.LOGGED_IN;
            if (method.equals("getBoostedSkillLevel")) return args[0] == Skill.HITPOINTS ? hp[0] : 50;
            if (method.equals("getRealSkillLevel")) return 99;
            return null;
        });
        EncounterLedgerPlugin plugin = new EncounterLedgerPlugin();
        field(plugin,"client",client);
        field(plugin,"config", proxy(EncounterLedgerConfig.class,(method,args) -> method.equals("idleTicks") ? 15 : null));
        plugin.onGameTick(new GameTick());
        assertNull(ticks(plugin));
        HitsplatApplied outgoing = new HitsplatApplied(); outgoing.setActor(npc);
        outgoing.setHitsplat(proxy(Hitsplat.class,(method,args) -> hitAnswer(method, 30)));
        plugin.onHitsplatApplied(outgoing);
        HitsplatApplied incoming = new HitsplatApplied(); incoming.setActor(player);
        incoming.setHitsplat(proxy(Hitsplat.class,(method,args) -> hitAnswer(method, 10)));
        plugin.onHitsplatApplied(incoming);
        hp[0] = 80;
        plugin.onGameTick(new GameTick());
        assertEquals(2,ticks(plugin).size());
        assertEquals(20,ticks(plugin).get(1).get("healingEstimate"));
        assertEquals(2,((List<?>)ticks(plugin).get(1).get("events")).size());
        plugin.onGameTick(new GameTick());
        assertEquals(3,ticks(plugin).size());
        assertEquals(0,ticks(plugin).get(2).get("healingEstimate"));
        assertTrue(((List<?>)ticks(plugin).get(2).get("events")).isEmpty());
        assertEquals(Collections.singletonList("[Glass Combat Logger] Recording started."), chat);
        // Capture the save submission without touching the real user's log directory.
        ThreadPoolExecutor writer = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable task) { }
        };
        field(plugin, "writer", writer);
        GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        plugin.onGameStateChanged(logout);
        assertEquals(2, chat.size());
        assertTrue(chat.get(1).contains("Recording stopped (login screen). Saving 3 ticks"));
        assertFalse(chat.get(1).contains("Log saved"));
        writer.shutdown();
    }
}

