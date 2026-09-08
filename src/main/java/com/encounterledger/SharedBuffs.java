package com.encounterledger;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
/** Boss-independent observed state. Timer values are retained without inventing source items. */
final class SharedBuffs {
 static Map<String,Object> state(boolean active,int raw,int unit){
  Map<String,Object> s=new LinkedHashMap<>();s.put("active",active);s.put("rawValue",raw);
  if(unit>0){s.put("remainingTicksUpperBound",Math.max(0,raw)*unit);s.put("timerResolutionTicks",unit);}
  return s;
 }
 static void timer(Map<String,Object> out,Client c,String id,int varbit,int unit){int raw=c.getVarbitValue(varbit);out.put(id,state(raw>0,raw,unit));}
 @SuppressWarnings("unchecked") static void merge(Map<String,Object> out,String id,int value){int raw=(Integer)((Map<String,Object>)out.get(id)).get("rawValue");if(value>raw)out.put(id,state(true,value,1));}
 static Map<String,Object> snapshot(Client c){
  Map<String,Object> out=new LinkedHashMap<>();
  timer(out,c,"heart_cooldown",VarbitID.IMBUED_HEART_TIMER,10);
  timer(out,c,"saturated_heart",VarbitID.SATURATED_HEART_TIME,0);
  timer(out,c,"prayer_regeneration",VarbitID.PRAYER_REGENERATION_POTION_TIMER,12);
  timer(out,c,"goading",VarbitID.GOADING_POTION_TIMER,6);
  timer(out,c,"menaphite_remedy",VarbitID.STATRENEWAL_POTION_TIMER,25);
  timer(out,c,"antifire",VarbitID.ANTIFIRE_POTION,30);
  timer(out,c,"super_antifire",VarbitID.SUPER_ANTIFIRE_POTION,20);
  out.put("stamina",state(c.getVarbitValue(VarbitID.STAMINA_ACTIVE)>0,c.getVarbitValue(VarbitID.STAMINA_DURATION),10));
  timer(out,c,"divine_attack",VarbitID.DIVINEATTACK_POTION_TIME,1);
  timer(out,c,"divine_strength",VarbitID.DIVINESTRENGTH_POTION_TIME,1);
  timer(out,c,"divine_defence",VarbitID.DIVINEDEFENCE_POTION_TIME,1);
  timer(out,c,"divine_ranged",VarbitID.DIVINERANGE_POTION_TIME,1);
  timer(out,c,"divine_magic",VarbitID.DIVINEMAGIC_POTION_TIME,1);
  int combat=c.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME),bastion=c.getVarbitValue(VarbitID.DIVINEBASTION_POTION_TIME),battle=c.getVarbitValue(VarbitID.DIVINEBATTLEMAGE_POTION_TIME);
  merge(out,"divine_attack",combat);merge(out,"divine_strength",combat);merge(out,"divine_defence",Math.max(combat,Math.max(bastion,battle)));merge(out,"divine_ranged",bastion);merge(out,"divine_magic",battle);
  int poison=c.getVarpValue(net.runelite.api.gameval.VarPlayerID.POISON);
  boolean helm=false;
  ItemContainer gear=c.getItemContainer(InventoryID.EQUIPMENT);
  if(gear!=null && c.getVarbitValue(VarbitID.CHARGES_SERPENTINE_HELM_QUANTITY)>0){
   for(Item item:gear.getItems())if(item.getId()>=0){String name=c.getItemDefinition(item.getId()).getName();
    if(name.equals("Serpentine helm")||name.equals("Tanzanite helm")||name.equals("Magma helm"))helm=true;
   }
  }
  out.put("poison_immunity",state(poison<0||helm,poison,0));
  out.put("venom_immunity",state(poison < -38||helm,poison,0));
  out.put("preserve",state(c.isPrayerActive(Prayer.PRESERVE),0,0));
  for(Skill skill:new Skill[]{Skill.ATTACK,Skill.STRENGTH,Skill.DEFENCE,Skill.RANGED,Skill.MAGIC}){
   int base=c.getRealSkillLevel(skill),current=c.getBoostedSkillLevel(skill);
   Map<String,Object> s=state(current>base,current,0);s.put("base",base);s.put("current",current);
   out.put("boost_"+skill.name().toLowerCase(Locale.ROOT),s);
  }
  return out;
 }
}
