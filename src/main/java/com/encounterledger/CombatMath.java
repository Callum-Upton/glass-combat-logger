package com.encounterledger;

final class CombatMath
{
    private CombatMath() {}
    // A residual, not an authoritative food/healing event. Missing damage and overkill affect it.
    static int healingEstimate(int previousHp, int currentHp, int damage)
    {
        return previousHp < 0 ? 0 : Math.max(0, currentHp - previousHp + damage);
    }
    static boolean isHpDamage(int type, boolean mine, boolean others)
    {
        return mine || others || type == 65 || type == 5 || type == 67 || type == 74;
    }
}
