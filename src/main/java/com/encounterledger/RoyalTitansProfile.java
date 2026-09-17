package com.encounterledger;

/** Capture-only profile from the September 13 duo corpus. Website owns attack interpretation. */
final class RoyalTitansProfile implements BossProfile {
    static final RoyalTitansProfile INSTANCE=new RoyalTitansProfile();
    public String id(){return "royal_titans";}
    public String name(){return "Royal Titans";}
    public boolean matches(int id){return id==12596 || id==14147 || id==14148 || id==14149;}
    public boolean hasEncounterArea(){return true;}
    // Template region containing the instanced arena; never use allocated instance world coordinates.
    public boolean containsEncounterTile(net.runelite.api.coords.WorldPoint p,boolean instanced){
        return instanced && p!=null && p.getPlane()==0 && p.getRegionID()==11669;
    }
    public boolean includesSpatialNpc(int id){return matches(id) || (id>=14150 && id<=14154);}
    public boolean observesNpcDamage(int id){return matches(id) || id==14150 || id==14151;}
    public String groundHazard(int id){return id>=3206 && id<=3223 ? "royal_titans_visual" : null;}
    public String[] animation(int npcId,int animationId){return null;}
    public String[] projectile(int id){return null;}
    public String[] spawn(int id){return null;}
    public String[] overhead(String text){return null;}
    public String[] graphic(int id,boolean local){return null;}
    public String[] announcement(String text){return null;}
    static boolean completion(String text){return text.replaceAll("<[^>]*>", "").replaceAll("@[A-Za-z0-9_]+@", "").trim().matches("Your Royal Titans kill count is: [0-9,]+\\.");}
}
