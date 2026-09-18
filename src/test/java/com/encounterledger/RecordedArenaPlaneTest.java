package com.encounterledger;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
/** Template locations corroborated by boss-present capture snapshots, September 18. */
public class RecordedArenaPlaneTest {
 @Test public void mappedBossPlanesMatchRecordedArenas(){
  BossProfile[] profiles={YamaProfile.INSTANCE,ScurriusProfile.INSTANCE,VorkathProfile.INSTANCE,RoyalTitansProfile.INSTANCE,ResearchBossProfile.ZULRAH,ResearchBossProfile.ZULRAH,ResearchBossProfile.DUKE,ResearchBossProfile.PHOSANI};
  int[][] tiles={{1500,10080,0},{3290,9865,0},{2270,4060,0},{2912,9568,0},{2266,3073,0},{2274,3062,0},{3040,6432,0},{3872,9952,3}};
  for(int i=0;i<profiles.length;i++)for(int plane=0;plane<4;plane++){
   int[] t=tiles[i];assertEquals(profiles[i].name()+" plane "+plane,plane==t[2],profiles[i].containsEncounterTile(new WorldPoint(t[0],t[1],plane),true));
  }
 }
}