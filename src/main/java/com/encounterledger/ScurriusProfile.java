package com.encounterledger;

/** Public and private arena share the same template tiles in recorded snapshots. */
final class ScurriusProfile implements BossProfile {
    static final ScurriusProfile INSTANCE=new ScurriusProfile();
    public String id(){return "scurrius";}
    public String name(){return "Scurrius";}
    public boolean matches(int id){return id==7221 || id==7222;}
    public boolean hasEncounterArea(){return true;}
    public boolean containsEncounterTile(net.runelite.api.coords.WorldPoint p,boolean instanced){
        return p!=null && p.getPlane()==0 && p.getX()>=3276 && p.getX()<=3309 && p.getY()>=9857 && p.getY()<=9878;
    }
    public String[] animation(int npcId,int animationId){return null;}
    public String[] projectile(int graphicId){return null;}
    public String[] spawn(int npcId){return null;}
    public String[] overhead(String text){return null;}
    public String[] graphic(int graphicId,boolean localPlayer){return null;}
    public String[] announcement(String text){return null;}
}
