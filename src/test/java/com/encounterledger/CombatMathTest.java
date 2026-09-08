package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
public class CombatMathTest
{
    @Test public void damageAndFoodOnSameTick() { assertEquals(20, CombatMath.healingEstimate(70, 80, 10)); }
    @Test public void ordinaryDamageIsNotHealing() { assertEquals(0, CombatMath.healingEstimate(70, 60, 10)); }
    @Test public void noBaselineIsNotHealing() { assertEquals(0, CombatMath.healingEstimate(-1, 99, 10)); }
    @Test public void resourceSplatsAreNotHpDamage() { assertFalse(CombatMath.isHpDamage(60, false, false)); assertFalse(CombatMath.isHpDamage(6, false, false)); }
    @Test public void poisonAndVenomAreDamage() { assertTrue(CombatMath.isHpDamage(65, false, false)); assertTrue(CombatMath.isHpDamage(5, false, false)); }
}
