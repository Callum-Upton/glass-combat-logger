package com.encounterledger;
import java.util.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecordingFolderTest {
 @Test public void readableFoldersRetainUniqueIdentityAndUseSameDestination(){
  String id=UUID.randomUUID().toString(),folder=RecordingFolder.name("<col=fff>Yama</col>",id);
  assertTrue(folder.startsWith("Yama_"));assertTrue(folder.endsWith(id));
  Map<String,Object> log=new HashMap<>();log.put("researchSessionId",id);log.put("researchFolder",folder);
  assertEquals(Paths.get("logs","research",folder),EncounterLedgerPlugin.encounterDirectory(Paths.get("logs"),log));
  assertEquals(id,RecordingFolder.validate(id,id));
 }
 @Test(expected=IllegalArgumentException.class) public void pathTraversalRejected(){
  String id=UUID.randomUUID().toString();RecordingFolder.validate("../"+id,id);
 }
}
