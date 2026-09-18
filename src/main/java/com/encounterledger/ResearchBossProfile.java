package com.encounterledger;

import net.runelite.api.coords.WorldPoint;
import java.util.regex.Pattern;

/** Capture boundaries only. Damage/animation interpretation belongs to the website. */
final class ResearchBossProfile implements BossProfile {
    static final ResearchBossProfile ZULRAH=new ResearchBossProfile("zulrah","Zulrah",new int[]{2042,2043,2044},new int[]{9007,9008},0);
    static final ResearchBossProfile DUKE=new ResearchBossProfile("duke","Duke Sucellus",new int[]{12167,12191,12192},new int[]{12132},0);
    static final ResearchBossProfile PHOSANI=new ResearchBossProfile("phosanis_nightmare","Phosani's Nightmare",new int[]{377,9417,9418,9420,9421,9422,9424,11153,11154,11155},new int[]{15515},3);
    private final String id,name;
    private final int[] npcs,regions;
    private final int plane;
    private ResearchBossProfile(String id,String name,int[] npcs,int[] regions,int plane){this.id=id;this.name=name;this.npcs=npcs;this.regions=regions;this.plane=plane;}
    public String id(){return id;}
    public String name(){return name;}
    public boolean matches(int id){for(int n:npcs)if(n==id)return true;return false;}
    static BossProfile forNpc(int id){for(ResearchBossProfile p:new ResearchBossProfile[]{ZULRAH,DUKE,PHOSANI})if(p.matches(id))return p;return null;}
    public boolean hasEncounterArea(){return true;}
    public boolean containsEncounterTile(WorldPoint p,boolean instanced){
        if(!instanced||p==null||p.getPlane()!=plane)return false;
        for(int r:regions)if(p.getRegionID()==r)return true;return false;
    }
    // Preserve nearby arena actors (including non-combat adds) within PositionRecorder's caps.
    public boolean includesSpatialNpc(int id){return true;}
    public boolean completionMessage(String text){
        String clean=text.replaceAll("<[^>]*>","").replaceAll("@[A-Za-z0-9_]+@","").trim();
        return clean.matches("Your "+Pattern.quote(name)+" kill count is: [0-9,]+\\.");
    }
    // Phase shield depletion, dives and replacement actors are not kill evidence.
    public boolean requiresCompletionMessage(){return true;}
    public String[] animation(int npc,int animation){return null;}
    public String[] projectile(int id){return null;}
    public String[] spawn(int id){return null;}
    public String[] overhead(String text){return null;}
    public String[] graphic(int id,boolean local){return null;}
    public String[] announcement(String text){return null;}
}
