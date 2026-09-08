package com.encounterledger;
import java.util.*;

/** Boss-independent actor-animation profiles. -1 means cooldown evidence is incomplete. */
final class WeaponProfiles {
 static final class Attack {
  final String kind; final int delay;
  Attack(String kind,int delay){this.kind=kind;this.delay=delay;}
 }
 private static final Map<String,Map<Integer,Attack>> profiles=new LinkedHashMap<>();
 static {
  normal("emberlight",4,386,390); special("emberlight",-1,11138);
  normal("arclight",4,386,390); special("arclight",-1,2890);
  normal("scythe of vitur",5,8056);normal("holy scythe of vitur",5,8056);normal("sanguine scythe of vitur",5,8056);
  normal("osmumten's fang",5,9471);special("osmumten's fang",-1,11222);
  normal("blade of saeldor",4,386,390);
  normal("abyssal tentacle",4,1658);normal("abyssal whip",4,1658);
  normal("elder maul",6,7516);special("elder maul",-1,11124);
  normal("dragon claws",4,7527);special("dragon claws",-1,7514);
  normal("burning claws",4,7527);special("burning claws",-1,11140);
  normal("saradomin godsword",6,7045,7046,7054);special("saradomin godsword",-1,7640,7641);
  normal("bandos godsword",6,7045,7046,7054);special("bandos godsword",-1,7642,7643);
  special("accursed sceptre",-1,9961);
  normal("tumeken's shadow",5,9543);
  // Demonbane is spell-defined for these casting weapons, not their melee speed.
  for(String w:Arrays.asList("purging staff","kodai wand","toxic staff of the dead","staff of the dead","ahrim's staff","blue moon spear"))normal(w,w.equals("purging staff")?4:5,8977);
  // Ranged style affects speed; capture markers without guessing rapid/accurate delay.
  normal("scorching bow",-1,426);special("scorching bow",-1,11133);
 }
 private static void normal(String w,int d,int... ids){add(w,"attack",d,ids);}
 private static void special(String w,int d,int... ids){add(w,"special attack",d,ids);}
 private static void add(String w,String kind,int d,int... ids){Map<Integer,Attack> map=profiles.computeIfAbsent(w,k->new HashMap<>());for(int id:ids)map.put(id,new Attack(kind,d));}
 static Attack find(String weapon,int animation){
  String w=weapon.toLowerCase(Locale.ROOT).replaceAll(" \\(.*\\)$", "");
  Map<Integer,Attack> map=profiles.get(w);return map==null?null:map.get(animation);
 }
}
