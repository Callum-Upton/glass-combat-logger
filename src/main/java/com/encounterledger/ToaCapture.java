package com.encounterledger;
import java.util.*;
import java.util.regex.*;
/** Raid-wide capture. Region entry is capture evidence, never an official timer start. */
final class ToaCapture {
    static final int LOBBY=13454;
    static final Set<Integer> REGIONS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(14160,15698,15700,14162,14164,15186,15188,14674,14676,15184,15696,14672)));
    private static final Pattern TOTAL=Pattern.compile("^Tombs of Amascut(?:: (Entry|Expert) Mode)? total completion time: (\\d{1,4}):([0-5]\\d)(?:\\.(\\d{1,2}))?(?:\\.?(?: Personal best:.*| \\(new personal best\\))?)?$");
    private static final Pattern COUNT=Pattern.compile("^Your completed Tombs of Amascut(?:: (Entry|Expert) Mode)? count is: [0-9,]+\\.$");
    boolean active,observedEntry;int part,outside,completionTick=-1;String sessionId;Map<String,Object> timing;
    private boolean blocked,seenLobby;
    boolean poll(int region,boolean instanced){
        boolean inside=instanced&&REGIONS.contains(region);
        if(active){if(region>=0)outside=inside?0:outside+1;return false;}
        if(!inside){blocked=false;seenLobby=region==LOBBY;return false;}
        if(blocked||region==14672)return false;
        active=true;observedEntry=seenLobby&&region==14160;sessionId=UUID.randomUUID().toString();part=1;outside=0;completionTick=-1;timing=null;return true;
    }
    boolean message(String raw,int tick){
        if(!active)return false;
        String text=RaidLifecycleMessages.clean(raw);boolean evidence=false;
        for(String line:raw.split("(?i)<br\\s*/?>")){
            String clean=RaidLifecycleMessages.clean(line);Matcher m=TOTAL.matcher(clean);
            if(m.matches()){
                long ms=Long.parseLong(m.group(2))*60000+Integer.parseInt(m.group(3))*1000;
                if(m.group(4)!=null)ms+=Integer.parseInt(m.group(4))*(m.group(4).length()==1?100:10);
                timing=new LinkedHashMap<>();timing.put("source","toa_total_completion_message");timing.put("durationMs",ms);timing.put("message",clean);timing.put("mode",m.group(1)==null?"normal":m.group(1).toLowerCase(Locale.ROOT));
                timing.put("precisionMs",m.group(4)==null?1000:m.group(4).length()==1?100:10);evidence=true;
            }
        }
        if(evidence&&completionTick<0)completionTick=tick;
        return evidence||COUNT.matcher(text).matches()||text.startsWith("Challenge complete: ")||text.startsWith("Tombs of Amascut");
    }
    String endReason(int tick,int size){
        if(completionTick>=0&&tick-completionTick>=3)return "raid_complete";
        if(outside>=3)return "left_raid";
        return size>=9000?"length_limit":null;
    }
    void finish(String reason){if("length_limit".equals(reason)){part++;return;}active=false;blocked=true;seenLobby=false;}
    void reset(){active=false;blocked=false;seenLobby=false;outside=0;completionTick=-1;timing=null;}
}
