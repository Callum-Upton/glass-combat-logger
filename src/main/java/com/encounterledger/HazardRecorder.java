package com.encounterledger;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/** Observed visual anchors only: animation extent is not a server collision footprint. */
final class HazardRecorder {
    private final PositionRecorder positions=new PositionRecorder();
    /** Preserve separate objects, including scheduled visuals; never merge them into a damage count. */
    Map<String,Object> atHit(Client client, Player player, BossProfile profile) {
        Map<String,Object> result=new LinkedHashMap<>();
        List<Map<String,Object>> observations=new ArrayList<>();
        boolean available=player!=null && profile!=null && client.getGraphicsObjects()!=null;
        boolean truncated=false;
        if(available) for(GraphicsObject g:client.getGraphicsObjects()) {
            String type=profile.groundHazard(g.getId());
            if(type==null || g.finished() || g.getLocation()==null || g.getWorldView()!=player.getWorldView())continue;
            WorldPoint world=WorldPoint.fromLocal(g.getWorldView(),g.getLocation().getX(),g.getLocation().getY(),g.getLevel());
            if(player.getWorldLocation()==null || world.distanceTo(player.getWorldLocation())>2)continue;
            WorldPoint p=positions.normalise(client,g.getWorldView(),world);
            if(p==null)continue;
            if(observations.size()>=256){truncated=true;break;}
            Map<String,Object> tile=new LinkedHashMap<>();tile.put("x",p.getX());tile.put("y",p.getY());tile.put("plane",p.getPlane());
            Map<String,Object> observation=new LinkedHashMap<>();
            observation.put("observationIndex",observations.size());
            observation.put("graphicId",g.getId());observation.put("type",type);observation.put("position",tile);
            observation.put("startCycle",g.getStartCycle());observation.put("scheduled",g.getStartCycle()>client.getGameCycle());
            observations.add(observation);
        }
        result.put("basis","client_visual_observations");result.put("clientCycle",client.getGameCycle());
        result.put("available",available);result.put("truncated",truncated);result.put("radiusTiles",2);
        result.put("observations",observations);
        return result;
    }
    List<Map<String,Object>> snapshot(Client client, Player player, BossProfile profile) {
        List<Map<String,Object>> result=new ArrayList<>();
        if(profile==null || player==null || client.getGraphicsObjects()==null)return result;
        for(GraphicsObject graphic:client.getGraphicsObjects()) {
            String type=profile.groundHazard(graphic.getId());
            if(type==null || graphic.finished() || graphic.getStartCycle()>client.getGameCycle()
                || graphic.getWorldView()!=player.getWorldView() || graphic.getLocation()==null)continue;
            WorldPoint world=WorldPoint.fromLocal(graphic.getWorldView(),graphic.getLocation().getX(),graphic.getLocation().getY(),graphic.getLevel());
            WorldPoint point=positions.normalise(client,graphic.getWorldView(),world);
            if(point==null)continue;
            Map<String,Object> tile=new LinkedHashMap<>();
            tile.put("x",point.getX());tile.put("y",point.getY());tile.put("plane",point.getPlane());
            Map<String,Object> hazard=new LinkedHashMap<>();
            hazard.put("type",type);hazard.put("graphicId",graphic.getId());hazard.put("position",tile);
            hazard.put("startCycle",graphic.getStartCycle());hazard.put("animationFrame",graphic.getAnimationFrame());
            result.add(hazard);
            if(result.size()>=256)break;
        }
        return result;
    }
    List<String> possibleSources(Client client,Player player,BossProfile profile) {
        Map<String,Object> tile=positions.position(client,player);
        return overlaps(tile,snapshot(client,player,profile),protections(client,player,profile));
    }
    List<String> protections(Client client,Player player,BossProfile profile) {
        Set<String> result=new LinkedHashSet<>();
        if(profile!=null && player!=null && player.getSpotAnims()!=null)for(ActorSpotAnim spot:player.getSpotAnims()) {
            String type=profile.hazardProtection(spot.getId());
            if(type!=null && spot.getStartCycle()<=client.getGameCycle())result.add(type);
        }
        return new ArrayList<>(result);
    }
    static List<String> overlaps(Map<String,Object> tile,List<Map<String,Object>> hazards,List<String> protections) {
        Set<String> types=new LinkedHashSet<>();
        if(tile!=null)for(Map<String,Object> hazard:hazards)
            if(("fire_wave".equals(hazard.get("type")) || "shadow_wave".equals(hazard.get("type"))) && tile.equals(hazard.get("position")) && !protections.contains(hazard.get("type")))types.add((String)hazard.get("type"));
        return new ArrayList<>(types);
    }
}

