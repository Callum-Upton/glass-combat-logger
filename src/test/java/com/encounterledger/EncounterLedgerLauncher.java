package com.encounterledger;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
public class EncounterLedgerLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(EncounterLedgerPlugin.class);
        RuneLite.main(args);
    }
}
