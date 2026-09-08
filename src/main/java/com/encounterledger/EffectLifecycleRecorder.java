package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/** Client visual observations, never server damage windows. Bounded per encounter. */
final class EffectLifecycleRecorder {
    // Arena-wide effects can schedule more than 1,000 graphics in a single tick.
    static final int MAX_ACTIVE=4096, MAX_EVENTS=8192;
    private final PositionRecorder positions=new PositionRecorder();
    private final Map<GraphicsObject,Map<String,Object>> active=new IdentityHashMap<>();
    private final List<Map<String,Object>> events=new ArrayList<>();
    private int nextId; private boolean truncated;
    void reset(){active.clear();events.clear();nextId=0;truncated=false;}
    private void emit(Map<String,Object> state,String phase,Client client){
        if(events.size()>=MAX_EVENTS){truncated=true;return;}
        Map<String,Object> e=new LinkedHashMap<>(state);e.put("phase",phase);e.put("clientCycle",client.getGameCycle());e.put("clientTick",client.getTickCount());events.add(e);
    }
    void observe(Client client,Player player,GraphicsObject g,boolean created){
        if(player==null||g.getWorldView()!=player.getWorldView()||g.getLocation()==null)return;
        WorldPoint world=WorldPoint.fromLocal(g.getWorldView(),g.getLocation().getX(),g.getLocation().getY(),g.getLevel());
        if(world.distanceTo(player.getWorldLocation())>48)return;
        WorldPoint p=positions.normalise(client,g.getWorldView(),world);if(p==null)return;
        Map<String,Object> tile=new LinkedHashMap<>();tile.put("x",p.getX());tile.put("y",p.getY());tile.put("plane",p.getPlane());
        Map<String,Object> s=active.get(g);
        if(s==null){
            if(g.finished())return;
            if(active.size()>=MAX_ACTIVE){truncated=true;return;}
            s=new LinkedHashMap<>();s.put("identity",++nextId);s.put("graphicId",g.getId());s.put("position",tile);s.put("startCycle",g.getStartCycle());s.put("started",false);active.put(g,s);emit(s,created?"created":"first_observed",client);
        }
        if(!tile.equals(s.get("position"))){s.put("position",tile);emit(s,"moved",client);}
        if(!Boolean.TRUE.equals(s.get("started"))&&client.getGameCycle()>=g.getStartCycle()){s.put("started",true);emit(s,"start_observed",client);}
        if(g.finished()){emit(s,"finished_observed",client);active.remove(g);}
    }
    void scan(Client client,Player player){
        if(player==null||client.getGraphicsObjects()==null)return;
        Set<GraphicsObject> present=Collections.newSetFromMap(new IdentityHashMap<>());
        for(GraphicsObject g:client.getGraphicsObjects()){present.add(g);observe(client,player,g,false);}
        for(GraphicsObject g:new ArrayList<>(active.keySet()))if(!present.contains(g)){
            emit(active.get(g),g.finished()?"finished_observed":"no_longer_visible",client);active.remove(g);
        }
    }
    Map<String,Object> drain(){
        Map<String,Object> result=new LinkedHashMap<>();result.put("events",new ArrayList<>(events));result.put("truncated",truncated);result.put("basis","client_visual_observations");events.clear();truncated=false;return result;
    }
}
