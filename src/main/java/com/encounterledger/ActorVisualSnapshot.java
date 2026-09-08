package com.encounterledger;

import java.util.*;
import net.runelite.api.*;

/** Raw client observations. An empty visual list never asserts server-side immunity. */
final class ActorVisualSnapshot {
    static final int LIMIT = 32;
    static Map<String,Object> capture(Actor actor, int cycle) {
        Map<String,Object> result = new LinkedHashMap<>();
        List<Map<String,Object>> graphics = new ArrayList<>();
        boolean available = actor != null && actor.getSpotAnims() != null;
        boolean truncated = false;
        if (available) for (ActorSpotAnim spot : actor.getSpotAnims()) {
            if (graphics.size() >= LIMIT) { truncated = true; break; }
            Map<String,Object> graphic = new LinkedHashMap<>();
            graphic.put("id", spot.getId());
            graphic.put("startCycle", spot.getStartCycle());
            graphic.put("scheduled", spot.getStartCycle() > cycle);
            graphics.add(graphic);
        }
        result.put("basis", "client_visual_observations");
        result.put("clientCycle", cycle);
        result.put("available", available);
        result.put("truncated", truncated);
        result.put("graphics", graphics);
        if (actor instanceof NPC) {
            NPCComposition composition = ((NPC) actor).getTransformedComposition();
            if (composition != null) result.put("transformedNpcId", composition.getId());
        }
        return result;
    }
}
