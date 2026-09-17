package com.encounterledger;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class RecordingFolder {
    static String name(String boss, String id) {
        return name(boss,id,LocalDateTime.now());
    }
    static String logFile(Map<String,Object> log) {
        String id=UUID.fromString(String.valueOf(log.get("id"))).toString();
        if(log.containsKey("researchSessionId"))return id+".json";
        LocalDateTime started=Instant.parse(String.valueOf(log.get("startedAt"))).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return name((String)log.get("label"),id,started)+".json";
    }
    private static String name(String boss,String id,LocalDateTime time) {
        String label=net.runelite.client.util.Text.removeTags(boss==null?"PvM":boss).replaceAll("[^A-Za-z0-9_-]+","-").replaceAll("^-+|-+$","");
        if(label.isEmpty())label="PvM";
        label=label.substring(0,Math.min(48,label.length()));
        return label+"_"+time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_"+UUID.fromString(id);
    }
    static String validate(String folder,String id) {
        String uuid=UUID.fromString(id).toString();
        if(folder.equals(uuid)||folder.matches("[A-Za-z0-9_-]{1,100}_"+uuid))return folder;
        throw new IllegalArgumentException("Invalid recording folder");
    }
}
