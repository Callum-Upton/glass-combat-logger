package com.encounterledger;

import java.util.Locale;

final class ResearchCommand {
    private ResearchCommand() {}
    static String mode(String[] args) {
        if(args==null||args.length!=2||!"research".equalsIgnoreCase(args[0])||args[1]==null)return null;
        String value=args[1].toLowerCase(Locale.ROOT);
        return "on".equals(value)||"off".equals(value)||"status".equals(value)||"continuous".equals(value)?value:null;
    }
}
