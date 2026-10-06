package com.encounterledger;
import java.util.regex.Pattern;
/** Text evidence only. Caller must independently establish the intended raid/location. */
final class RaidLifecycleMessages {
    private RaidLifecycleMessages(){}
    static String clean(String text){return text==null?"":text.replace("<br>"," ").replaceAll("<[^>]*>","").replaceAll("@[A-Za-z0-9_]+@","").replaceAll("\\s+"," ").trim();}
    static boolean genericStart(String text){return clean(text).equals("The raid has begun!");}
    static boolean genericCompletion(String text){return clean(text).matches("Congratulations - your raid is complete!(?: Team size: .*)?");}
    static boolean namedEntry(String text,String raid){
        if(raid==null||raid.trim().isEmpty())return false;
        return clean(text).matches("You enter (?:the )?"+Pattern.quote(raid)+"(?: \\([^()]+ Mode\\))?\\.\\.\\.");
    }
    static boolean namedCompletion(String text,String raid){
        if(raid==null||raid.trim().isEmpty())return false;
        String name=Pattern.quote(raid)+"(?:: (?:Entry|Expert|Hard) Mode)?";
        for (String line : (text == null ? "" : text).split("(?i)<br\\s*/?>")) {
        String s=clean(line);
        // Whole-raid time/count only. A room's Duration or Challenge complete is insufficient.
        if (s.matches("Your completed "+name+" count is: [0-9,]+\\.")
            || s.matches(name+" (?:challenge |total )?completion time: [0-9]+:[0-5][0-9](?:\\.[0-9]{1,2})?(?:\\..*| \\(new personal best\\))?")) return true;
        }
        return false;
    }
}
