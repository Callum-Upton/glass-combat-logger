package com.encounterledger;
import java.util.Locale;
/** Initial verified profiles. Unsupported animations remain raw observations. */
final class AttackTiming {
 static int delay(int animation,String weapon){
  WeaponProfiles.Attack profile=WeaponProfiles.find(weapon,animation);if(profile!=null)return profile.delay;
  String w=weapon.toLowerCase(Locale.ROOT);
  if(animation==8056&&w.contains("scythe of vitur"))return 5;
  if(animation==1658&&(w.contains("whip")||w.contains("tentacle")))return 4;
  if(animation==9543&&w.contains("tumeken"))return 5;
  if(animation==8977)return w.contains("purging staff")?4:5;
  if(animation==1167||animation==1162||animation==711||animation==727||animation==9144||animation==11423||animation==11429||animation==11430)return (w.contains("harmonised")||w.contains("trident")||w.contains("sanguinesti")||w.contains("accursed")||w.contains("thammaron")||w.contains("warped")||w.contains("dawnbringer"))?-1:5;
  return -1;
 }
 static boolean cue(int animation,String weapon){return WeaponProfiles.find(weapon,animation)!=null||cue(animation);}
 static boolean cue(int a){return a==8056||a==1658||a==9543||a==8977||a==1167||a==1162||a==711||a==727||a==9144||a==11423||a==11429||a==11430||a==426||a==427||a==5061;}
}
