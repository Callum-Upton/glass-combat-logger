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
 @Test public void injectedRuneLiteClientIsUsedWithFixedEndpointAndNoRedirects()throws Exception{
 List<okhttp3.Request> requests=new ArrayList<>();
 okhttp3.OkHttpClient shared=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{requests.add(chain.request());return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),"{}")).build();}).build();
 LiveUploader uploader=new LiveUploader(new Gson(),s->{},shared);
 java.lang.reflect.Field field=LiveUploader.class.getDeclaredField("http");field.setAccessible(true);okhttp3.OkHttpClient scoped=(okhttp3.OkHttpClient)field.get(uploader);
 assertFalse(scoped.followRedirects());assertFalse(scoped.followSslRedirects());assertFalse(scoped.retryOnConnectionFailure());assertSame(shared.connectionPool(),scoped.connectionPool());
 uploader.configure(key,false,true);uploader.completed("{}","recording");uploader.awaitIdle();
 assertEquals(2,requests.size());assertEquals("https://zenyte.gg/api/plugin/logs",requests.get(1).url().toString());assertEquals("Bearer "+key,requests.get(1).header("Authorization"));uploader.close();
 }
}
