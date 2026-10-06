package com.encounterledger;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Bounded data schema. No remote expressions, code, URLs, or capture/privacy switches. */
final class CaptureRules {
    static final int MAX_BYTES = 32768;
    final int revision;
    final long expiresAt;
    final boolean enabled;
    private boolean localOverride;
    boolean diagnostic;
    final List<String> starts, completions;
    final Set<Integer> regions, exits, planes;
    private CaptureRules(int revision,long expiresAt,boolean enabled,List<String> starts,List<String> completions,
                         Set<Integer> regions,Set<Integer> exits,Set<Integer> planes) {
        this.revision=revision;this.expiresAt=expiresAt;this.enabled=enabled;
        this.starts=Collections.unmodifiableList(starts);this.completions=Collections.unmodifiableList(completions);
        this.regions=Collections.unmodifiableSet(regions);this.exits=Collections.unmodifiableSet(exits);this.planes=Collections.unmodifiableSet(planes);
    }
    static CaptureRules local(String start,String end) {
        start=RaidLifecycleMessages.clean(start);end=end==null?"":end.trim();
        if(start.length()<8||start.length()>240||end.length()<8||end.length()>240||end.contains("<")||end.chars().anyMatch(c->c<32||c==127)
            ||!end.toLowerCase(Locale.ROOT).contains("fractured archive"))return null;
        CaptureRules r=new CaptureRules(0,Long.MAX_VALUE,true,Arrays.asList(start),Arrays.asList(end),new HashSet<>(),new HashSet<>(),new HashSet<>());
        r.localOverride=true;return r;
    }
    boolean localOverride(){return localOverride;}
    static CaptureRules parse(String json,long now) {
        if(json==null||json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Rules too large");
        JsonObject o=new JsonParser().parse(json).getAsJsonObject();
        Set<String> keys=new HashSet<>(Arrays.asList("schemaVersion","revision","expiresAt","enabled","bossId","startMessages","completionPrefixes","startRegions","exitRegions","planes"));
        for(String key:o.keySet())if(!keys.contains(key))throw new IllegalArgumentException("Unknown rule field");
        if(o.size()!=keys.size()||number(o.get("schemaVersion"),1,1)!=1||!Arrays.asList("fractured_archive","capture_test").contains(o.get("bossId").getAsString()))throw new IllegalArgumentException("Unsupported schema");
        int rev=number(o.get("revision"),0,Integer.MAX_VALUE);
        long expires=Instant.parse(o.get("expiresAt").getAsString()).getEpochSecond();
        if(expires<=now||expires>now+7*86400)throw new IllegalArgumentException("Rules expired or validity too long");
        if(!o.get("enabled").isJsonPrimitive()||!o.getAsJsonPrimitive("enabled").isBoolean())throw new IllegalArgumentException("Invalid enabled flag");
        boolean enabled=o.get("enabled").getAsBoolean();
        boolean diagnostic="capture_test".equals(o.get("bossId").getAsString());
        List<String> starts=strings(o,"startMessages"),ends=strings(o,"completionPrefixes");
        for(String prefix:ends)if(!diagnostic&&(!prefix.toLowerCase(Locale.ROOT).contains("fractured archive")||!prefix.endsWith("completion time: ")))throw new IllegalArgumentException("Not a raid completion prefix");
        Set<Integer> regions=numbers(o,"startRegions",65535),exits=numbers(o,"exitRegions",65535),planes=numbers(o,"planes",3);
        if(!Collections.disjoint(regions,exits))throw new IllegalArgumentException("Conflicting regions");
        if(enabled&&(starts.isEmpty()||ends.isEmpty()||(!diagnostic&&(regions.isEmpty()||planes.isEmpty()))))throw new IllegalArgumentException("Missing start context");
        CaptureRules result=new CaptureRules(rev,expires,enabled,starts,ends,regions,exits,planes);result.diagnostic=diagnostic;return result;
    }
    private static int number(JsonElement e,int min,int max) {
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.toString().matches("[0-9]+"))throw new IllegalArgumentException("Invalid number");
        long n=new java.math.BigDecimal(e.toString()).longValueExact();if(n<min||n>max)throw new IllegalArgumentException("Out of range");return (int)n;
    }
    private static List<String> strings(JsonObject o,String key) {
        JsonArray a=o.getAsJsonArray(key);if(a.size()>16)throw new IllegalArgumentException("Too many messages");
        List<String> out=new ArrayList<>();
        for(JsonElement e:a){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid text");String s=e.getAsString();if(s.length()<5||s.length()>240||s.contains("<")||s.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("Invalid text");out.add(s);}
        return out;
    }
    private static Set<Integer> numbers(JsonObject o,String key,int max) {
        JsonArray a=o.getAsJsonArray(key);if(a.size()>64)throw new IllegalArgumentException("Too many regions");
        Set<Integer> out=new HashSet<>();for(JsonElement e:a)out.add(number(e,0,max));return out;
    }
    boolean starts(String message,int region,int plane,long now) {
        return enabled&&now<expiresAt&&(localOverride||diagnostic||(regions.contains(region)&&planes.contains(plane)))&&starts.contains(RaidLifecycleMessages.clean(message));
    }
    String completion(String message) {
        for(String line:(message==null?"":message).split("(?i)<br\\s*/?>")) {
            String clean=RaidLifecycleMessages.clean(line);
            if(diagnostic)return completions.contains(clean)?clean:null;
            for(String prefix:completions)if(clean.startsWith(prefix)) {
                if(localOverride)return clean;
                String time=clean.substring(prefix.length());
                // Fixed local grammar, never supplied by the server. Retain the original message as evidence.
                if(time.matches("(?:[0-9]{1,2}:)?[0-9]{1,3}:[0-5][0-9](?:\\.[0-9]{1,2})?(?:\\..*| \\(new personal best\\))?"))return clean;
            }
        }
        return null;
    }
}
