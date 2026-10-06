package com.encounterledger;
import org.junit.Test;import static org.junit.Assert.*;
public class RaidLifecycleMessagesTest {
 @Test public void verifiedExistingFamilies(){
  assertTrue(RaidLifecycleMessages.genericStart("<col=ef20ff>The raid has begun!</col>"));
  assertTrue(RaidLifecycleMessages.genericCompletion("Congratulations - your raid is complete!<br>Team size: 2 players Duration: 25:46.20"));
  assertTrue(RaidLifecycleMessages.namedEntry("You enter the Theatre of Blood (Normal Mode)...","Theatre of Blood"));
  assertTrue(RaidLifecycleMessages.namedCompletion("Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00","Theatre of Blood"));
  assertTrue(RaidLifecycleMessages.namedCompletion("Tombs of Amascut: Expert Mode challenge completion time: <col=ef1020>9:40</col>. Personal best: 8:31","Tombs of Amascut"));
  assertTrue(RaidLifecycleMessages.namedCompletion("Your completed Tombs of Amascut count is: <col=ff0000>2</col>.","Tombs of Amascut"));
 }
 @Test public void completionCanFollowRoomMessage(){
  assertTrue(RaidLifecycleMessages.namedCompletion("Challenge complete: The Wardens. Duration: 8:30<br>Tombs of Amascut challenge completion time: 25:00. Personal best: 24:00", "Tombs of Amascut"));
 }
 @Test public void rejectsRoomClearsOtherRaidsAndPlayerQuotations(){
  for(String s:new String[]{"Challenge complete: The Wardens. Duration: 8:30","Upper level complete! Duration: 4:00","Wave 'The Maiden of Sugadinti' complete!","Player: The raid has begun!","Fight duration: 2:30. Personal best: 1:00"}){
   assertFalse(RaidLifecycleMessages.genericStart(s));assertFalse(RaidLifecycleMessages.genericCompletion(s));assertFalse(RaidLifecycleMessages.namedCompletion(s,"The Fractured Archive"));
  }
  assertFalse(RaidLifecycleMessages.namedCompletion("Your completed Tombs of Amascut count is: 2.","The Fractured Archive"));
  assertFalse(RaidLifecycleMessages.namedCompletion("The Fractured Archive completion time: 2:99","The Fractured Archive"));
 }
}
