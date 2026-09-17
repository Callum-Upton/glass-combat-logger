package com.encounterledger;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecordingTriggerTest {
 @Test public void divineNeedsConfirmedUseAndGraphicAndNeverHidesOverlappingCombat(){
  Map<String,Object> use=new HashMap<>();use.put("kind","item_use");use.put("label","Divine super combat potion(3)");use.put("confidence","corroborated");
  Map<String,Object> hit=new HashMap<>();hit.put("kind","damage_taken");hit.put("amount",10);hit.put("actorVisualsAtHit",Collections.singletonMap("graphics",Collections.singletonList(Collections.singletonMap("id",560))));
  List<Map<String,Object>> events=new ArrayList<>(Arrays.asList(use,hit));assertTrue(RecordingTrigger.onlyDivineDamage(events));
  events.add(hit);assertFalse(RecordingTrigger.onlyDivineDamage(events));events.remove(2);
  events.add(Collections.singletonMap("kind","damage_done"));assertFalse(RecordingTrigger.onlyDivineDamage(events));events.remove(2);
  use.put("confidence","intent");assertFalse(RecordingTrigger.onlyDivineDamage(events));use.put("confidence","corroborated");
  hit.remove("actorVisualsAtHit");assertFalse(RecordingTrigger.onlyDivineDamage(events));
 }
}
