package com.encounterledger;
import com.google.gson.Gson;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class LiveUploaderTest {
 private final String key="zy_"+String.join("",Collections.nCopies(43,"a"));
 private Map<String,Object> log(){List<Map<String,Object>> ticks=new ArrayList<>();for(int i=0;i<10;i++)ticks.add(new LinkedHashMap<>(Map.of("tick",i)));return new LinkedHashMap<>(Map.of("id","recording","boss",Map.of("id","yama"),"ticks",ticks));}
 @Test public void defaultOffAndRegularBatchesDoNotExposeKey()throws Exception{
 List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),s->{},(endpoint,k,json)->sent.add(endpoint+":"+json));
 u.tick(log());u.awaitIdle();assertTrue(sent.isEmpty());u.configure(key,true,false);u.tick(log());u.awaitIdle();assertEquals(1,sent.size());assertFalse(sent.get(0).contains(key));assertTrue(sent.get(0).contains("\"from\":0"));u.tick(log());u.awaitIdle();assertEquals(1,sent.size());u.configure(key,false,false);u.awaitIdle();assertTrue(sent.get(1).contains("stop"));u.close();}
 @Test public void autoUploadIsSeparateAndFailedLiveDoesNotPreventIt()throws Exception{
 List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),s->{},(endpoint,k,json)->{sent.add(endpoint);if(endpoint.equals("live"))throw new java.io.IOException();});
 u.configure(key,true,true);u.tick(log());u.awaitIdle();u.completed("{}","recording");u.awaitIdle();assertTrue(sent.contains("logs"));u.close();}
 @Test public void invalidKeysNeverSend()throws Exception{List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),s->{},(e,k,j)->sent.add(e));u.configure("invalid",true,true);u.tick(log());u.completed("{}","recording");u.awaitIdle();assertTrue(sent.isEmpty());u.close();}
}
