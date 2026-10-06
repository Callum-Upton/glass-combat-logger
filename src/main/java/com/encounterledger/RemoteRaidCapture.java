package com.encounterledger;

/** Client-thread-only raid lifecycle, with immutable rules pinned for each attempt. */
final class RemoteRaidCapture {
    CaptureRules rules;
    String sessionId, completionMessage;
    int part, completionTick=-1, outsideTicks;
    boolean active(){return rules!=null;}
    void start(CaptureRules rules){this.rules=rules;sessionId=java.util.UUID.randomUUID().toString();part=1;completionTick=-1;completionMessage=null;outsideTicks=0;}
    void message(String text,int tick){if(!active())return;String match=rules.completion(text);if(match!=null){if(completionTick<0)completionTick=tick;completionMessage=match;}}
    String endReason(int tick,int region,int plane,int frames){
        if(!active())return null;
        if(completionTick>=0&&tick-completionTick>=3)return "raid_complete";
        outsideTicks=rules.exits.contains(region)&&rules.planes.contains(plane)?outsideTicks+1:0;
        if(outsideTicks>=3&&completionTick<0)return "left_raid";
        return frames>=9000?"length_limit":null;
    }
    void finish(String reason){if("length_limit".equals(reason)){part++;return;}reset();}
    void reset(){rules=null;sessionId=null;completionMessage=null;completionTick=-1;outsideTicks=0;}
}
