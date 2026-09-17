package com.encounterledger;

/** Development profile: post-quest captures, September 2026. See docs/vorkath-attack-map.md. */
final class VorkathProfile implements BossProfile {
    static final VorkathProfile INSTANCE=new VorkathProfile();
    public String id(){return "vorkath";}
    public String name(){return "Vorkath";}
    public boolean matches(int npcId){return npcId==8061;}
    public boolean hasEncounterArea(){return true;}
    // Conservative mapped viewport across both template regions; excludes teleport/house scenes.
    public boolean containsEncounterTile(net.runelite.api.coords.WorldPoint p,boolean instanced){
        return instanced&&p!=null&&p.getPlane()==0&&p.getX()>=2255&&p.getX()<=2289&&p.getY()>=4048&&p.getY()<=4082;
    }
    public boolean observesNpcDamage(int npcId){return matches(npcId)||npcId==8063;}
    public boolean includesSpatialNpc(int npcId){return npcId==8063;}
    public String[] animation(int npcId,int animationId){
        if(!matches(npcId))return null;
        switch(animationId){
            case 7951:return cue("melee","Melee attack","melee");
            case 7957:return cue("acid_phase","Acid phase","special");
            default:return null;
        }
    }
    public String[] projectile(int id){
        switch(id){
            case 1477:return cue("ranged","Ranged attack","ranged");
            case 1479:return cue("magic","Magic attack","magic");
            case 393:return cue("dragonfire","Dragonfire","fire");
            case 1470:return cue("venom_dragonfire","Venom dragonfire","fire");
            case 1471:return cue("corrupting_dragonfire","Prayer-disabling dragonfire","fire");
            case 1481:return cue("deadly_dragonfire","Deadly dragonfire","special");
            case 1482:return cue("rapid_fire","Rapid fire","fire");
            case 395:return cue("freeze","Freezing dragonfire","special");
            case 1484:return cue("spawn_launch","Zombified spawn launched","special");
            default:return null;
        }
    }
    public String[] spawn(int id){return id==8063?cue("spawn","Zombified spawn","summon"):null;}
    public String[] overhead(String text){return null;}
    public String[] graphic(int id,boolean local){return null;}
    public String[] announcement(String text){return null;}
    private static String[] cue(String id,String label,String category){return new String[]{id,label,category};}
}
