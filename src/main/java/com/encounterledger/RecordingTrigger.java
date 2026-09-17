package com.encounterledger;
import java.util.*;

final class RecordingTrigger {
 static boolean divineUse(List<Map<String,Object>> events){
  return events.stream().anyMatch(e->"item_use".equals(e.get("kind"))&&"corroborated".equals(e.get("confidence"))&&String.valueOf(e.get("label")).toLowerCase(Locale.ROOT).startsWith("divine "));
 }
 static boolean onlyDivineDamage(List<Map<String,Object>> events){
  if(!divineUse(events))return false;
  List<Map<String,Object>> hits=new ArrayList<>();
  for(Map<String,Object> e:events){
   if("damage_done".equals(e.get("kind")))return false;
   if("damage_taken".equals(e.get("kind")))hits.add(e);
  }
  if(hits.size()!=1||!Integer.valueOf(10).equals(hits.get(0).get("amount")))return false;
  Object visuals=hits.get(0).get("actorVisualsAtHit");
  if(!(visuals instanceof Map))return false;
  Object graphics=((Map<?,?>)visuals).get("graphics");if(!(graphics instanceof List))return false;
  return ((List<?>)graphics).stream().anyMatch(g->g instanceof Map&&Integer.valueOf(560).equals(((Map<?,?>)g).get("id")));
 }
}
