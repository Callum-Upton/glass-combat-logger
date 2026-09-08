package com.encounterledger;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;

/** End-of-tick server tiles, normalised to the instance template rather than the camera. */
final class PositionRecorder {
    private final Map<NPC,Integer> identities = new IdentityHashMap<>();
    private int nextIdentity;
    private static Map<String,Object> map(Object... fields) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<fields.length;i+=2)result.put((String)fields[i],fields[i+1]);
        return result;
    }
    WorldPoint normalise(Client client, WorldView view, WorldPoint world) {
        if(view==null || world==null)return null;
        if(!view.isInstance())return world;
        LocalPoint local=LocalPoint.fromWorld(view,world);
        if(local==null)return null;
        int[][][] chunks=view.getInstanceTemplateChunks();
        int x=local.getSceneX()/8,y=local.getSceneY()/8,p=world.getPlane();
        if(chunks==null || p<0 || p>=chunks.length || x<0 || x>=chunks[p].length || y<0 || y>=chunks[p][x].length || chunks[p][x][y]==-1)return null;
        return WorldPoint.fromLocalInstance(client,local,p);
    }
    Map<String,Object> position(Client client,Actor actor) {
        if(actor==null)return null;
        WorldPoint point=normalise(client,actor.getWorldView(),actor.getWorldLocation());
        return point==null?null:map("x",point.getX(),"y",point.getY(),"plane",point.getPlane());
    }
    Map<String,Object> snapshot(Client client,Player player) {
        return snapshot(client,player,null);
    }
    Map<String,Object> snapshot(Client client,Player player,BossProfile profile) {
        List<Map<String,Object>> npcs=new ArrayList<>();
        List<NPC> visible=client.getNpcs();
        boolean truncated=false;
        if(visible!=null) for(NPC npc:visible) {
            if(npc.getWorldView()!=player.getWorldView() || npc.getWorldLocation()==null || player.getWorldLocation()==null
                || npc.getWorldLocation().distanceTo(player.getWorldLocation())>48)continue;
            // Include combat NPCs plus Yama's encounter actors; omit decorative non-combat NPCs.
            if(npc.getCombatLevel()<=0 && npc.getId()!=14176 && npc.getId()!=14179 && npc.getId()!=14180 && npc!=player.getInteracting()
                && (profile==null || !profile.includesSpatialNpc(npc.getId())))continue;
            Map<String,Object> position=position(client,npc);
            if(position==null)continue;
            if(npcs.size()>=128){truncated=true;break;}
            NPCComposition composition=npc.getTransformedComposition();
            int size=composition==null?1:Math.max(1,Math.min(64,composition.getSize()));
            // Rotated template chunks can change which corner is southwest.
            WorldPoint world=npc.getWorldLocation();
            int minX=(Integer)position.get("x"),minY=(Integer)position.get("y"),maxX=minX,maxY=minY;
            boolean footprintKnown=composition!=null;
            for(int dx:new int[]{0,size-1})for(int dy:new int[]{0,size-1}) {
                WorldPoint corner=normalise(client,npc.getWorldView(),world.dx(dx).dy(dy));
                if(corner==null || corner.getPlane()!=(Integer)position.get("plane")){footprintKnown=false;continue;}
                minX=Math.min(minX,corner.getX());minY=Math.min(minY,corner.getY());maxX=Math.max(maxX,corner.getX());maxY=Math.max(maxY,corner.getY());
            }
            footprintKnown=footprintKnown && maxX-minX+1==size && maxY-minY+1==size;
            if(footprintKnown) {position.put("x",minX);position.put("y",minY);}
            int identity=identities.computeIfAbsent(npc,n->++nextIdentity);
            npcs.add(map("id",npc.getId(),"index",npc.getIndex(),"identity",identity,"name",npc.getName()==null?"NPC":net.runelite.client.util.Text.removeTags(npc.getName()),
                "position",position,"size",footprintKnown?size:1,"footprintKnown",footprintKnown,"dead",npc.isDead(),"animationId",npc.getAnimation(),
                "actorVisuals",ActorVisualSnapshot.capture(npc,client.getGameCycle())));
        }
        Map<String,Object> result=map("coordinateSystem","instance_template","player",position(client,player),"npcs",npcs,"truncated",truncated);
        result.put("playerVisuals",ActorVisualSnapshot.capture(player,client.getGameCycle()));
        List<Map<String,Object>> glyphs=glyphs(client,player);
        if(glyphs!=null)result.put("glyphs",glyphs);
        return result;
    }
    static String glyphState(int id) {
        if(id==56335 || id==56336)return "active";
        if(id==56337)return "inactive";
        if(id==56338 || id==56339)return "fading";
        return null;
    }
    // Snapshot scene objects so pre-existing glyphs, transforms and removals are all observed.
    // RuneLite gameval ObjectID1: FLOORKIT_SUMMONING03_FULL01/02, INACTIVE, DEACTIVATE.
    private List<Map<String,Object>> glyphs(Client client,Player player) {
        WorldView view=player.getWorldView();
        WorldPoint p=normalise(client,view,player.getWorldLocation());
        if(p==null || p.getRegionID()!=6045)return null;
        Scene scene=view.getScene();
        if(scene==null || scene.getTiles()==null)return null;
        int plane=player.getWorldLocation().getPlane();
        Tile[][][] tiles=scene.getTiles();
        if(plane<0 || plane>=tiles.length)return null;
        Set<TileObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        List<Map<String,Object>> result=new ArrayList<>();
        for(Tile[] row:tiles[plane])if(row!=null)for(Tile tile:row)if(tile!=null) {
            List<TileObject> objects=new ArrayList<>();
            objects.add(tile.getGroundObject());objects.add(tile.getDecorativeObject());objects.add(tile.getWallObject());
            if(tile.getGameObjects()!=null)Collections.addAll(objects,tile.getGameObjects());
            for(TileObject object:objects) {
                if(object==null || !seen.add(object))continue;
                // Only inspect the known Yama glyph family, never arbitrary scene definitions.
                int id=object.getId();
                if(glyphState(id)==null)continue;
                ObjectComposition definition=client.getObjectDefinition(id);
                if(definition!=null && definition.getImpostorIds()!=null) {
                    definition=definition.getImpostor();
                    if(definition==null)continue;
                    id=definition.getId();
                }
                String state=glyphState(id);
                WorldPoint point=normalise(client,view,object.getWorldLocation());
                if(state==null || point==null || point.getRegionID()!=6045)continue;
                result.add(map("id",id,"state",state,"position",map("x",point.getX(),"y",point.getY(),"plane",point.getPlane())));
            }
        }
        return result;
    }
    void reset() {identities.clear();nextIdentity=0;}
}
