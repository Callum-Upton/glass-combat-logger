package com.encounterledger;

import java.util.*;
import java.util.regex.*;

/** Clicks are intentions. Emit only after a matching inventory change, never from HP alone. */
final class ConsumableTracker
{
    private static final Pattern DOSE = Pattern.compile("^(.*)\\((\\d)\\)$");
    static final class Use {
        final int slot, id, quantity, tick; final String name, category, option;
        Use(int slot,int id,int quantity,String name,String category,String option,int tick) {
            this.slot=slot;this.id=id;this.quantity=quantity;this.name=name;this.category=category;this.option=option;this.tick=tick;
        }
    }
    private final Map<Integer,Use> attempts = new HashMap<>();
    static String category(String option,String name) {
        String n=name.toLowerCase(Locale.ROOT);
        if (option.equalsIgnoreCase("Eat")) return "healing";
        if (!option.equalsIgnoreCase("Drink")) return null;
        if (n.contains("prayer") || n.contains("super restore") || n.contains("sanfew") || n.contains("zamorak brew")) return "prayer";
        if (n.contains("saradomin brew") || n.contains("guthix rest") || n.contains("mix")) return "healing";
        return "potion";
    }
    void click(int slot,int id,int quantity,String name,String option,int tick) {
        attempts.remove(slot);
        String category=category(option,name);
        if(category!=null) attempts.put(slot,new Use(slot,id,quantity,name,category,option,tick));
    }
    Use changed(int slot,int id,int quantity,String name,int tick) {
        Use use=attempts.get(slot);
        if(use==null)return null;
        if(tick-use.tick>2){attempts.remove(slot);return null;}
        if(id==use.id && quantity>=use.quantity)return null;
        attempts.remove(slot);
        if(id==use.id)return use;
        if(use.option.equalsIgnoreCase("Eat")) {
            // Food may disappear or become a smaller portion. A swap into an occupied slot is not eating.
            if(id<0 || name.toLowerCase(Locale.ROOT).contains("cake") && use.name.toLowerCase(Locale.ROOT).contains("cake")
                || name.toLowerCase(Locale.ROOT).contains("pizza") && use.name.toLowerCase(Locale.ROOT).contains("pizza")
                || name.toLowerCase(Locale.ROOT).contains("pie") && use.name.toLowerCase(Locale.ROOT).contains("pie"))return use;
            return null;
        }
        Matcher before=DOSE.matcher(use.name), after=DOSE.matcher(name);
        if(before.matches()) {
            int dose=Integer.parseInt(before.group(2));
            if(after.matches() && before.group(1).equals(after.group(1)) && Integer.parseInt(after.group(2))==dose-1)return use;
            if(dose==1 && (id<0 || name.equalsIgnoreCase("Vial") || name.equalsIgnoreCase("Empty cup")))return use;
        }
        return null;
    }
    void expire(int tick) { attempts.values().removeIf(use->tick-use.tick>2); }
    void clear() { attempts.clear(); }
}
