package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResearchCommandTest {
    @Test public void explicitModesOnly() {
        for(String mode:new String[]{"on","off","status","continuous"})assertEquals(mode,ResearchCommand.mode(new String[]{"research",mode}));
        assertEquals("on",ResearchCommand.mode(new String[]{"RESEARCH","ON"}));
        assertNull(ResearchCommand.mode(null));
        assertNull(ResearchCommand.mode(new String[]{"research"}));
        assertNull(ResearchCommand.mode(new String[]{"research","maybe"}));
        assertNull(ResearchCommand.mode(new String[]{"research","on","extra"}));
        assertNull(ResearchCommand.mode(new String[]{"other","on"}));
    }
}
