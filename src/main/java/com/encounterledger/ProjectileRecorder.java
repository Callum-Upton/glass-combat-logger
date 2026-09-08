package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/** Perspective-local trajectories. A visual destination is not proof of a hit or ownership. */
final class ProjectileRecorder {
    private final PositionRecorder positions=new PositionRecorder();
    private final Map<Projectile,Integer> identities=new IdentityHashMap<>();
    private int nextIdentity;
    void reset(){identities.clear();nextIdentity=0;}
    private Map<String,Object> tile(Client client,WorldView view,WorldPoint world) {
        WorldPoint p=positions.normalise(client,view,world);
        if(p==null)return null;
        Map<String,Object> result=new LinkedHashMap<>();result.put("x",p.getX());result.put("y",p.getY());result.put("plane",p.getPlane());return result;
    }
    List<Map<String,Object>> snapshot(Client client,Player player,BossProfile profile) {
        List<Map<String,Object>> result=new ArrayList<>();
        if(profile==null || player==null || player.getWorldView()!=client.getTopLevelWorldView() || client.getProjectiles()==null)return result;
        int cycle=client.getGameCycle();
        identities.keySet().removeIf(p->p.getEndCycle()<cycle);
        for(Projectile p:client.getProjectiles()) {
            String type=profile.replayProjectile(p.getId());
            if(type==null)type="unclassified_projectile";
            if(p.getStartCycle()>cycle || p.getEndCycle()<cycle)continue;
            WorldView view=player.getWorldView();
            Map<String,Object> position=tile(client,view,WorldPoint.fromLocal(view,(int)p.getX(),(int)p.getY(),p.getSourceLevel()));
            if(position==null)continue;
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("identity",identities.computeIfAbsent(p,k->++nextIdentity));item.put("graphicId",p.getId());item.put("type",type);
            item.put("position",position);item.put("source",tile(client,view,p.getSourcePoint()));item.put("target",tile(client,view,p.getTargetPoint()));
            item.put("startCycle",p.getStartCycle());item.put("endCycle",p.getEndCycle());item.put("observedCycle",cycle);
            item.put("remainingCycles",Math.max(0,p.getEndCycle()-cycle));item.put("scope","recording_perspective");
            Actor source=p.getSourceActor(),target=p.getTargetActor();
            item.put("sourceNpcId",source instanceof NPC?((NPC)source).getId():null);
            item.put("targetKind",target==player?"local_player":target instanceof NPC?"npc":target instanceof Player?"other_player":"tile_or_unknown");
            result.add(item);if(result.size()>=256)break;
        }
        return result;
    }
}
