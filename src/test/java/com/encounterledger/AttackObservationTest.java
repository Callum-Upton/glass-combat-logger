package com.encounterledger;
import java.lang.reflect.*;import java.util.*;
import net.runelite.api.*;import org.junit.Test;import static org.junit.Assert.*;
public class AttackObservationTest {
 @Test @SuppressWarnings("unchecked") public void unknownWeaponsKeepLaunchEvidenceWithoutInterpretation() throws Exception {
  EncounterLedgerPlugin plugin=new EncounterLedgerPlugin();
  int[] state={123456,900,99999};String[] name={"Future weapon"};
  NPC npc=BossBoundaryTest.fake(NPC.class,m->m.equals("getId")?8061:null);
  Actor[] target={npc};
  Player player=BossBoundaryTest.fake(Player.class,m->m.equals("getAnimation")?state[0]:m.equals("getInteracting")?target[0]:null);
  ItemContainer gear=BossBoundaryTest.fake(ItemContainer.class,m->m.equals("getItem")?new Item(state[2],1):null);
  ItemComposition definition=BossBoundaryTest.fake(ItemComposition.class,m->m.equals("getName")?name[0]:null);
  Client client=BossBoundaryTest.fake(Client.class,m->m.equals("getGameCycle")?state[1]:m.equals("getTickCount")?30:m.equals("getItemContainer")?gear:m.equals("getItemDefinition")?definition:m.equals("getVarpValue")?1:null);
  Field cf=EncounterLedgerPlugin.class.getDeclaredField("client");cf.setAccessible(true);cf.set(plugin,client);
  Method capture=EncounterLedgerPlugin.class.getDeclaredMethod("recordAttackObservation",Player.class);capture.setAccessible(true);
  Field pf=EncounterLedgerPlugin.class.getDeclaredField("pending");pf.setAccessible(true);
  List<Map<String,Object>> pending=(List<Map<String,Object>>)pf.get(plugin);
  capture.invoke(plugin,player);capture.invoke(plugin,player);assertEquals(1,pending.size());
  Map<String,Object> first=pending.get(0);assertEquals("attack_observation",first.get("kind"));assertEquals(123456,first.get("animationId"));assertEquals(99999,first.get("itemId"));assertEquals(1,first.get("attackStyle"));assertFalse(first.containsKey("attackDelayTicks"));
  state[1]++;state[2]=99998;name[0]="Second future weapon";capture.invoke(plugin,player);
  assertEquals(2,pending.size());assertEquals("Future weapon",first.get("weaponName"));assertEquals("Second future weapon",pending.get(1).get("weaponName"));
  state[0]=-1;state[1]++;capture.invoke(plugin,player);assertEquals(2,pending.size());
  state[0]=345678;target[0]=null;capture.invoke(plugin,player);assertEquals(2,pending.size());
 }
}
