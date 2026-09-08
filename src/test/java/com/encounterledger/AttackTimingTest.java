package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
public class AttackTimingTest {
 @Test public void weaponAndAnimationMustMatch(){assertEquals(5,AttackTiming.delay(8056,"Scythe of vitur"));assertEquals(-1,AttackTiming.delay(8056,"Purging staff"));}
 @Test public void demonbaneUsesCastWeapon(){assertEquals(4,AttackTiming.delay(8977,"Purging staff"));assertEquals(5,AttackTiming.delay(8977,"Ancient staff"));}
 @Test public void unsupportedSpeedIsUnknown(){assertEquals(-1,AttackTiming.delay(426,"Magic shortbow"));assertFalse(AttackTiming.cue(829));}
 @Test public void specialsAreMarkersWithoutAssumedCooldown(){
  assertTrue(AttackTiming.cue(11124,"Elder maul"));
  assertEquals("special attack",WeaponProfiles.find("Elder maul",11124).kind);
  assertEquals(-1,AttackTiming.delay(11124,"Elder maul"));
  assertFalse(AttackTiming.cue(11124,"Purging staff"));
 }
 @Test public void ornamentedWeaponsAndStyleVariants(){
  assertEquals(6,AttackTiming.delay(7045,"Saradomin godsword (or)"));
  assertEquals(6,AttackTiming.delay(7054,"Bandos godsword"));
  assertEquals("special attack",WeaponProfiles.find("Saradomin godsword (or)",7641).kind);
 }
 @Test public void effectAnimationsAreNotPlayerAttacks(){
  assertFalse(AttackTiming.cue(11141,"Purging staff"));
  assertFalse(AttackTiming.cue(11516,"Noxious halberd"));
 }
}
