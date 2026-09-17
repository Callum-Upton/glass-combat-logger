package com.encounterledger;

import java.util.*;
import java.util.regex.*;

/** Raid lifecycle evidence, independent of individual room NPCs. */
final class CoxCapture {
    static final int IN_RAID = 5432, RAID_STATE = 5425;
    private boolean seenLobby, blocked;
    boolean active, startMessage, complete;
    String layout;
    Map<String,Object> timing;
    private static final Pattern TIME = Pattern.compile("Duration:\\s*(\\d+):([0-5]\\d)(?:\\.(\\d{1,2}))?");
    private static final Pattern TEAM = Pattern.compile("Team size:\\s*(\\d+) players?");

    static String clean(String raw) { return raw.replace("<br>", " ").replaceAll("<[^>]*>", "").trim(); }
    static boolean recognized(String raw) {
        String s=clean(raw);
        return s.equals("The raid has begun!") || s.startsWith("Map Layout: ") || s.startsWith("Congratulations - your raid is complete!");
    }
    void message(String raw) {
        String s=clean(raw);
        if(s.equals("The raid has begun!") && !blocked) startMessage=true;
        if(s.startsWith("Map Layout: ")) layout=s.substring(12).trim();
        if(active && s.startsWith("Congratulations - your raid is complete!")) {
            complete=true;
            timing=new LinkedHashMap<>(); timing.put("source","cox_completion_message"); timing.put("message",s);
            Matcher m=TIME.matcher(s);
            if(m.find()) {
                long ms=Long.parseLong(m.group(1))*60000+Integer.parseInt(m.group(2))*1000;
                if(m.group(3)!=null)ms+=Integer.parseInt(m.group(3))*(m.group(3).length()==1?100:10);
                timing.put("durationMs",ms);
                timing.put("precisionMs",m.group(3)==null?1000:m.group(3).length()==1?100:10);
            }
            m=TEAM.matcher(s); if(m.find())timing.put("teamSize",Integer.parseInt(m.group(1)));
        }
    }
    /** null means no new raid; true means the start was observed, false means joined mid-raid. */
    Boolean poll(boolean inside,int state) {
        if(active)return null;
        if(!inside || state==0) { blocked=false; seenLobby=inside; }
        if(inside && !blocked && (state>0 || startMessage)) {
            boolean observed=seenLobby || startMessage;
            active=true; complete=false; timing=null; startMessage=false;
            return observed;
        }
        return null;
    }
    void ended() { active=false; blocked=true; seenLobby=false; startMessage=false; complete=false; timing=null; layout=null; }
    void reset() { ended(); blocked=false; }
}
