package com.encounterledger;

import java.util.*;
import java.util.regex.*;

/** Evidence only: never substitute observed replay length for the game timer. */
final class KillTiming {
    private static final Pattern DURATION = Pattern.compile("^Fight duration: (\\d{1,3}):([0-5]\\d)(?:\\.(\\d{1,2}))?(?:\\. Personal best:.*| \\(new personal best\\).*)?$");
    static Map<String,Object> parse(String message) {
        message=message.replaceAll("@[A-Za-z0-9_]+@", "").replaceAll("<[^>]*>", "").trim();
        Matcher m=DURATION.matcher(message);
        if(!m.matches())return null;
        int millis=Integer.parseInt(m.group(1))*60000+Integer.parseInt(m.group(2))*1000;
        if(m.group(3)!=null)millis+=Integer.parseInt(m.group(3))*(m.group(3).length()==1?100:10);
        if(millis<=0)return null;
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("source","game_duration_message");out.put("durationMs",millis);out.put("message",message);
        out.put("precisionMs",m.group(3)==null?1000:m.group(3).length()==1?100:10);
        return out;
    }
}
