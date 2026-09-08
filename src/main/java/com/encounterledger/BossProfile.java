package com.encounterledger;

/** Boss rules are opt-in by NPC ID; unknown encounters never inherit another boss's rules. */
interface BossProfile
{
    String id();
    String name();
    boolean matches(int npcId);
    String[] animation(int npcId, int animationId);
    String[] projectile(int graphicId);
    String[] spawn(int npcId);
    String[] overhead(String text);
    String[] graphic(int graphicId, boolean localPlayer);
    String[] announcement(String text);
    default String groundHazard(int graphicId) { return null; }
    default String hazardProtection(int graphicId) { return null; }
    default String replayProjectile(int graphicId) { return null; }
    default boolean observesNpcDamage(int npcId) { return matches(npcId); }
    default boolean includesSpatialNpc(int npcId) { return false; }

    static BossProfile forNpc(int npcId) { return YamaProfile.INSTANCE.matches(npcId) ? YamaProfile.INSTANCE : null; }
}
