package com.encounterledger;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class SharedBuffsTest {
 @Test public void keepsCooldownBoundAndRawEvidence(){Map<String,Object> s=SharedBuffs.state(true,4,10);assertEquals(40,s.get("remainingTicksUpperBound"));assertEquals(4,s.get("rawValue"));}
 @Test public void noInventedTimerForProtection(){assertFalse(SharedBuffs.state(true,-40,0).containsKey("remainingTicksUpperBound"));}
 @Test public void combinedDivineKeepsLongerPerStatTimer(){Map<String,Object> out=new HashMap<>();out.put("divine_magic",SharedBuffs.state(true,10,1));SharedBuffs.merge(out,"divine_magic",50);assertEquals(50,((Map)out.get("divine_magic")).get("rawValue"));}
}
