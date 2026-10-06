package com.encounterledger;
import java.util.Locale;
import java.util.UUID;
/** Explicit diagnostic session; never establishes raid identity or completion. */
final class ManualRaidCapture {
    static final int MAX_PART_TICKS=9000;
    String sessionId;
    int part;
    boolean active(){return sessionId!=null;}
    void start(){if(!active()){sessionId=UUID.randomUUID().toString();part=1;}}
    void finish(String reason){if("length_limit".equals(reason)&&active())part++;else reset();}
    void reset(){sessionId=null;part=0;}
    static String command(String[] args){
        if(args==null||args.length!=2||!"raid".equalsIgnoreCase(args[0])||args[1]==null)return null;
        String action=args[1].toLowerCase(Locale.ROOT);
        return "start".equals(action)||"stop".equals(action)||"status".equals(action)?action:null;
    }
}
