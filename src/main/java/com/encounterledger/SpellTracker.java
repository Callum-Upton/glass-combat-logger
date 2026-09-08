package com.encounterledger;

import java.util.*;

/** Local spell state, independent of boss rules. Unknown is distinct from inactive. */
final class SpellTracker {
    static final String[] IDS = {"vengeance", "death_charge", "thrall"};
    static final int[] VARBITS = {2450,12411,12413};
    private int markEnd = -1;
    static String[] graphic(int id) {
        switch(id) {
            case 725: case 2605: return new String[]{"vengeance","Vengeance cast","cast"};
            case 726: return new String[]{"vengeance_other","Vengeance Other received","received"};
            case 1854: case 3288: return new String[]{"death_charge","Death Charge cast","cast"};
            case 1855: case 3289: return new String[]{"death_charge","Death Charge effect triggered","trigger"};
            case 1852: return new String[]{"mark_of_darkness","Mark of Darkness cast","cast"};
            case 1873: return new String[]{"thrall","Ghost thrall summon cast","cast"};
            case 1874: return new String[]{"thrall","Skeleton thrall summon cast","cast"};
            case 1875: return new String[]{"thrall","Zombie thrall summon cast","cast"};
            default: return null;
        }
    }
    void markCast(int tick,int magic,boolean purging) { markEnd=tick+Math.max(0,magic)*3*(purging?5:1); }
    Map<String,Object> snapshot(int tick,int vengeance,int death,int thrall) {
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("vengeance",vengeance>0); state.put("death_charge",death>0);state.put("thrall",thrall>0);
        state.put("deathChargeCharges",death);
        state.put("mark_of_darkness",markEnd<0?null:tick<markEnd);
        state.put("markRemainingTicks",markEnd<0?null:Math.max(0,markEnd-tick));
        state.put("markTiming","estimated_from_observed_cast");
        return state;
    }
    void reset() { markEnd=-1; }
}
