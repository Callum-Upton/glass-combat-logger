package com.encounterledger;

/** IDs from RuneLite gameval AnimationID/SpotanimID. See docs/boss-events.md. */
final class YamaProfile implements BossProfile
{
    static final YamaProfile INSTANCE = new YamaProfile();
    public String id() { return "yama"; }
    public String name() { return "Yama"; }
    public boolean matches(int npcId) { return npcId == 14176; }
    public boolean hasEncounterArea() { return true; }
    // Template region containing the arena, Judge islands and stepping stones.
    public boolean containsEncounterTile(net.runelite.api.coords.WorldPoint tile, boolean instanced) {
        return instanced && tile != null && tile.getPlane()==0 && tile.getRegionID()==6045;
    }
    // Research capture: moving stepping-stone fire spells are non-combat NPCs.
    public boolean includesSpatialNpc(int npcId) { return npcId==13507; }
    public String groundHazard(int graphicId) {
        if(graphicId==2856)return "skull_impact";
        // An effect ID may be delivered as a scene graphic rather than a Projectile.
        String projectile=replayProjectile(graphicId);
        if(projectile!=null)return projectile;
        if(graphicId==3251 || graphicId==3252)return "skull_impact";
        if(graphicId==3255 || graphicId==3262)return "rock_impact";
        if(graphicId>=3263 && graphicId<=3266)return "shadow_wave";
        if(graphicId>=3267 && graphicId<=3269)return "fire_wave";
        return null;
    }
    public String replayProjectile(int graphicId) {
        if(graphicId==2855)return "flaming_skull";
        if(graphicId==3250)return "flaming_skull";
        if(graphicId==3254)return "flaming_rock";
        if(graphicId>=3271 && graphicId<=3273)return "meteor";
        return null;
    }
    public boolean observesNpcDamage(int npcId) { return matches(npcId) || npcId==14180; }
    public String hazardProtection(int graphicId) {
        return graphicId==3280?"fire_wave":graphicId==3281?"shadow_wave":null;
    }
    public String[] announcement(String text) {
        text=text.replaceAll("@[a-zA-Z0-9_]+@", "").trim();
        if (text.equals("You've been seared to the ground!")) return new String[]{"fire_streak_injury", "Fire streak movement disruption", "fire"};
        if (text.equals("You absorb a Glyph of Fire.")) return new String[]{"fire_glyph_activation", "Fire glyph activation cost", "fire"};
        if (text.equals("You've been injured and can't use protection prayers!")) return new String[]{"shadow_wave_injury", "Shadow wave prayer disruption", "shadow"};
        if (!text.startsWith("Yama conjures") || text.length() > 240) return null;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String category = lower.contains("shadow") ? "shadow" : lower.contains("fire") || lower.contains("flam") || lower.contains("meteor") ? "fire" : "summon";
        return new String[]{"special_announcement", text, category};
    }
    public String[] spawn(int id) {
        if (id == 14179) return new String[]{"void_flare_spawn", "Void flare summoned", "summon"};
        if (id == 14180) return new String[]{"judge_spawn", "Judge of Yama appears", "phase"};
        return null;
    }
    public String[] overhead(String text) {
        return java.util.Arrays.asList("Begone", "Begone!", "You bore me.", "Enough.").contains(text)
            ? new String[]{"intermission", "Yama intermission", "phase"} : null;
    }
    public String[] graphic(int id, boolean localPlayer) {
        if (localPlayer) {
            if (id == 3244) return new String[]{"standard_ranged_cue", "Standard ranged attack cue", "shadow"};
            if (id == 3247) return new String[]{"standard_magic_cue", "Standard magic attack cue", "fire"};
            if (id == 3241 || id == 3242) return new String[]{"flare_player_impact", "Void flare player impact", "explosion"};
            if (id == 3280) return new String[]{"fire_protection", "Fire glyph protection", "fire"};
            if (id == 3281) return new String[]{"shadow_protection", "Shadow glyph protection", "shadow"};
            return null;
        }
        switch(id) {
            case 3253: return projectile(3254);
            case 3256: return projectile(3257);
            case 3259: return projectile(3260);
            case 3270: return projectile(3271);
            default: return null;
        }
    }
    public String[] animation(int npcId, int animation)
    {
        if (npcId == 14179 && animation == 12136) return new String[]{"void_flare_explode", "Void flare explosion", "explosion"};
        if (npcId != 14176) return null;
        if (animation == 12156) return new String[]{"summon", "Yama summons", "summon"};
        return null;
    }
    public String[] projectile(int id)
    {
        switch (id)
        {
            case 3254: return new String[]{"fire_special", "Fire special · flaming rock", "fire"};
            case 3257: return new String[]{"shadow_spike", "Shadow special · spike", "shadow"};
            case 3260: return new String[]{"shadow_root", "Shadow special · root", "shadow"};
            case 3271: case 3272: case 3273: return new String[]{"meteor", "Fire special · meteor", "fire"};
            case 3250: return new String[]{"fire_skull", "Fire skull projectile", "fire"};
            default: return null;
        }
    }
}
