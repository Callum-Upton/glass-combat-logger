package com.encounterledger;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/** Static scene evidence, stored separately from high-volume combat observations. */
final class ArenaSnapshot {
    static final int MAX_TILES=65536,MAX_OBJECTS=65536;
    private final PositionRecorder positions=new PositionRecorder();
    Map<String,Object> capture(Client client,Player player) {
        WorldView view=player==null?null:player.getWorldView();
        Scene scene=view==null?null:view.getScene();
        if(scene==null || scene.getTiles()==null)return null;
        Tile[][][] sceneTiles=scene.getTiles();
        CollisionData[] collision=view.getCollisionMaps();
        int[][][] heights=view.getTileHeights();byte[][][] settings=view.getTileSettings();
        List<int[]> tiles=new ArrayList<>();List<Map<String,Object>> objects=new ArrayList<>();
        Set<TileObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean truncated=false;
        outer:for(int plane=0;plane<sceneTiles.length;plane++) {
            if(sceneTiles[plane]==null)continue;
            for(int x=0;x<sceneTiles[plane].length;x++) {
                if(sceneTiles[plane][x]==null)continue;
                for(int y=0;y<sceneTiles[plane][x].length;y++) {
                    Tile t=sceneTiles[plane][x][y];if(t==null)continue;
                    if(tiles.size()>=MAX_TILES){truncated=true;break outer;}
                    WorldPoint point=positions.normalise(client,view,new WorldPoint(view.getBaseX()+x,view.getBaseY()+y,plane));
                    int flags=-1;
                    if(collision!=null && plane<collision.length && collision[plane]!=null) {
                        int[][] values=collision[plane].getFlags();if(values!=null && x<values.length && values[x]!=null && y<values[x].length)flags=values[x][y];
                    }
                    int height=heights!=null && plane<heights.length && heights[plane]!=null && x<heights[plane].length && heights[plane][x]!=null && y<heights[plane][x].length?heights[plane][x][y]:Integer.MIN_VALUE;
                    int setting=settings!=null && plane<settings.length && settings[plane]!=null && x<settings[plane].length && settings[plane][x]!=null && y<settings[plane][x].length?settings[plane][x][y]&255:-1;
                    SceneTilePaint paint=t.getSceneTilePaint();
                    tiles.add(new int[]{plane,x,y,point==null?-1:point.getX(),point==null?-1:point.getY(),point==null?-1:point.getPlane(),flags,height,setting,paint==null?-1:paint.getRBG(),paint==null?-1:paint.getTexture(),t.getSceneTileModel()==null?0:1});
                    List<TileObject> all=new ArrayList<>();all.add(t.getGroundObject());all.add(t.getWallObject());all.add(t.getDecorativeObject());if(t.getGameObjects()!=null)Collections.addAll(all,t.getGameObjects());
                    for(TileObject o:all)if(o!=null && seen.add(o)) {
                        if(objects.size()>=MAX_OBJECTS){truncated=true;continue;}
                        WorldPoint p=positions.normalise(client,view,o.getWorldLocation());
                        ObjectComposition definition=client.getObjectDefinition(o.getId());
                        if(definition!=null && definition.getImpostorIds()!=null)definition=definition.getImpostor();
                        Map<String,Object> item=ResearchRecorder.map("id",o.getId(),"transformedId",definition==null?-1:definition.getId(),
                            "kind",o instanceof GameObject?"game":o instanceof WallObject?"wall":o instanceof GroundObject?"ground":"decorative",
                            "scenePlane",plane,"sceneX",x,"sceneY",y,"position",p==null?null:ResearchRecorder.map("x",p.getX(),"y",p.getY(),"plane",p.getPlane()));
                        if(o instanceof GameObject) {
                            GameObject g=(GameObject)o;Point min=g.getSceneMinLocation(),max=g.getSceneMaxLocation();
                            if(min!=null && max!=null)item.put("sceneBounds",new int[]{min.getX(),min.getY(),max.getX(),max.getY()});
                            item.put("orientation",g.getOrientation());
                        }
                        objects.add(item);
                    }
                }
            }
        }
        return ResearchRecorder.map("format","encounter-ledger-arena","schemaVersion",1,"worldViewId",view.getId(),"baseX",view.getBaseX(),"baseY",view.getBaseY(),
            "instance",view.isInstance(),"instanceTemplateChunks",view.isInstance()?copy(view.getInstanceTemplateChunks()):null,
            "tileColumns",Arrays.asList("scenePlane","sceneX","sceneY","templateX","templateY","templatePlane","collisionFlags","heightSW","tileSettings","paintRGB","textureId","hasTileModel"),
            "collisionBasis","unrotated_scene_axes","missingHeight",Integer.MIN_VALUE,"missingValue",-1,"tiles",tiles,"objects",objects,"truncated",truncated,
            "maxTiles",MAX_TILES,"maxObjects",MAX_OBJECTS);
    }
    private static int[][][] copy(int[][][] values) {
        if(values==null)return null;int[][][] result=new int[values.length][][];
        for(int p=0;p<values.length;p++)if(values[p]!=null){result[p]=new int[values[p].length][];for(int x=0;x<values[p].length;x++)if(values[p][x]!=null)result[p][x]=values[p][x].clone();}
        return result;
    }
}
