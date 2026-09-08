package com.encounterledger;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class RecordingFolder {
    static String name(String boss, String id) {
        String label=net.runelite.client.util.Text.removeTags(boss==null?"PvM":boss).replaceAll("[^A-Za-z0-9_-]+","-").replaceAll("^-+|-+$","");
        if(label.isEmpty())label="PvM";
        label=label.substring(0,Math.min(48,label.length()));
        return label+"_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_"+UUID.fromString(id);
    }
    static String validate(String folder,String id) {
        String uuid=UUID.fromString(id).toString();
        if(folder.equals(uuid)||folder.matches("[A-Za-z0-9_-]{1,100}_"+uuid))return folder;
        throw new IllegalArgumentException("Invalid recording folder");
    }
}
