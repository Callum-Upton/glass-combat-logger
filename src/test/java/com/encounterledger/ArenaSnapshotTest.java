package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaSnapshotTest {
    @Test public void capturesMoreThanOldObjectLimitAndDetachesCollisionAndHeightValues() {
        Tile[][][] tiles=new Tile[1][60][50];
        int[][] flags=new int[60][50];flags[0][0]=1234;
        int[][][] heights=new int[1][61][51];heights[0][0][0]=99;
        for(int x=0;x<60;x++)for(int y=0;y<50;y++) {
            WorldPoint p=new WorldPoint(3200+x,3200+y,0);
            GameObject o=BossBoundaryTest.fake(GameObject.class,m->{if(m.equals("getId"))return 56246;if(m.equals("getWorldLocation"))return p;return null;});
            tiles[0][x][y]=BossBoundaryTest.fake(Tile.class,m->m.equals("getGameObjects")?new GameObject[]{o}:null);
        }
        Scene scene=BossBoundaryTest.fake(Scene.class,m->m.equals("getTiles")?tiles:null);
        CollisionData collision=BossBoundaryTest.fake(CollisionData.class,m->m.equals("getFlags")?flags:null);
        WorldView view=BossBoundaryTest.fake(WorldView.class,m->{if(m.equals("getScene"))return scene;if(m.equals("getBaseX")||m.equals("getBaseY"))return 3200;if(m.equals("getCollisionMaps"))return new CollisionData[]{collision};if(m.equals("getTileHeights"))return heights;return null;});
        Player player=BossBoundaryTest.fake(Player.class,m->m.equals("getWorldView")?view:null);
        Client client=BossBoundaryTest.fake(Client.class,m->null);
        Map<String,Object> snapshot=new ArenaSnapshot().capture(client,player);
        assertEquals(3000,((List<?>)snapshot.get("objects")).size());assertEquals(false,snapshot.get("truncated"));
        int[] first=(int[])((List<?>)snapshot.get("tiles")).get(0);
        assertEquals(3200,first[3]);assertEquals(1234,first[6]);assertEquals(99,first[7]);
        flags[0][0]=0;heights[0][0][0]=0;assertEquals(1234,first[6]);assertEquals(99,first[7]);
    }
    @Test public void missingSceneIsUnavailableNotAnEmptyArena() {
        assertNull(new ArenaSnapshot().capture(null,null));
    }
}
