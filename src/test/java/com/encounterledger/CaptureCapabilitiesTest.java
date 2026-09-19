package com.encounterledger;
import com.google.gson.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class CaptureCapabilitiesTest {
 @Test public void publishedCapabilitiesMatchRegisteredProfiles()throws Exception{
 Set<String> actual=new TreeSet<>();for(int id=0;id<65536;id++){BossProfile p=BossProfile.forNpc(id);if(p!=null)actual.add(p.id());}actual.add("cox");
 try(InputStream in=getClass().getResourceAsStream("/capture-capabilities.json")){assertNotNull(in);JsonObject value=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),JsonObject.class);Set<String> declared=new TreeSet<>();for(JsonElement id:value.getAsJsonArray("autoCapture"))declared.add(id.getAsString());assertEquals(actual,declared);assertTrue(value.get("liveLogging").getAsBoolean());assertTrue(value.get("autoUpload").getAsBoolean());}
 }
}
