package com.encounterledger;
import com.google.gson.Gson;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class LiveUploaderTest {
 private final EncounterLedgerConfig enabled=new EncounterLedgerConfig(){public boolean allowNetworking(){return true;}};
 private final String key="zy_"+String.join("",Collections.nCopies(43,"a"));
 private Map<String,Object> log(){List<Map<String,Object>> ticks=new ArrayList<>();for(int i=0;i<10;i++)ticks.add(new LinkedHashMap<>(Map.of("tick",i)));return new LinkedHashMap<>(Map.of("id","recording","boss",Map.of("id","yama"),"ticks",ticks));}
 @Test public void defaultOffAndRegularBatchesDoNotExposeKey()throws Exception{
 List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),s->{},(endpoint,k,json)->sent.add(endpoint+":"+json),enabled);
 u.tick(log());u.awaitIdle();assertTrue(sent.isEmpty());u.configure(key,true,false);u.tick(log());u.awaitIdle();assertEquals(1,sent.size());assertFalse(sent.get(0).contains(key));assertTrue(sent.get(0).contains("\"from\":0"));u.tick(log());u.awaitIdle();assertEquals(1,sent.size());u.configure(key,false,false);u.awaitIdle();assertTrue(sent.get(1).contains("stop"));u.close();}
 @Test public void autoUploadIsSeparateAndFailedLiveDoesNotPreventIt()throws Exception{
 List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),s->{},(endpoint,k,json)->{sent.add(endpoint);if(endpoint.equals("live"))throw new java.io.IOException();},enabled);
 u.configure(key,true,true);u.tick(log());u.awaitIdle();u.completed("{}","recording");u.awaitIdle();assertTrue(sent.contains("logs"));u.close();}
 @Test public void invalidKeysNeverSend()throws Exception{List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),s->{},(e,k,j)->sent.add(e),enabled);u.configure("invalid",true,true);u.tick(log());u.completed("{}","recording");u.awaitIdle();assertTrue(sent.isEmpty());u.close();}
 @Test public void injectedRuneLiteClientIsUsedWithFixedEndpointAndNoRedirects()throws Exception{
 List<okhttp3.Request> requests=new ArrayList<>();
 okhttp3.OkHttpClient shared=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{requests.add(chain.request());return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),"{}")).build();}).build();
 LiveUploader uploader=new LiveUploader(new Gson(),s->{},shared,enabled);
 java.lang.reflect.Field field=LiveUploader.class.getDeclaredField("http");field.setAccessible(true);okhttp3.OkHttpClient scoped=(okhttp3.OkHttpClient)field.get(uploader);
 assertFalse(scoped.followRedirects());assertFalse(scoped.followSslRedirects());assertFalse(scoped.retryOnConnectionFailure());assertSame(shared.connectionPool(),scoped.connectionPool());
 uploader.configure(key,false,true);uploader.completed("{}","recording");uploader.awaitIdle();
 assertEquals(2,requests.size());assertEquals("https://zenyte.gg/api/plugin/logs",requests.get(1).url().toString());assertEquals("Bearer "+key,requests.get(1).header("Authorization"));uploader.close();
 }

 @Test public void networkConsentDefaultsOffWithExactReviewerWarning()throws Exception{
 EncounterLedgerConfig config=new EncounterLedgerConfig(){};
 assertFalse(config.allowNetworking());
 assertEquals("This feature submits your IP address and various account data to a 3rd-party server not controlled or verified by Runelite developers.",EncounterLedgerConfig.class.getMethod("allowNetworking").getAnnotation(net.runelite.client.config.ConfigItem.class).warning());
 List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),m->{},(e,k,j)->sent.add(e),config);
 try{u.configure(key,true,true);u.tick(log());u.completed("{}","recording");u.configure(key,false,false);u.awaitIdle();assertTrue(sent.isEmpty());}finally{u.close();}
 }
 @Test public void disablingConsentBlocksAlreadyQueuedUploadAndStop()throws Exception{
 java.util.concurrent.atomic.AtomicBoolean allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
 EncounterLedgerConfig config=new EncounterLedgerConfig(){public boolean allowNetworking(){return allowed.get();}};
 java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
 List<String> sent=new ArrayList<>();LiveUploader u=new LiveUploader(new Gson(),m->{},(e,k,j)->{sent.add(e);entered.countDown();if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("Test timed out");},config);
 try{u.configure(key,true,true);u.tick(log());assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));u.completed("{}","recording");allowed.set(false);release.countDown();u.awaitIdle();assertEquals(Collections.singletonList("live"),sent);}finally{release.countDown();u.close();}
 }
 @Test public void actualHttpBoundaryChecksCurrentConsentBeforeEveryCall()throws Exception{
 java.util.concurrent.atomic.AtomicBoolean allowed=new java.util.concurrent.atomic.AtomicBoolean(false);
 EncounterLedgerConfig config=new EncounterLedgerConfig(){public boolean allowNetworking(){return allowed.get();}};
 List<okhttp3.Request> sent=new ArrayList<>();okhttp3.OkHttpClient client=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{sent.add(chain.request());return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),"{}")).build();}).build();
 LiveUploader u=new LiveUploader(new Gson(),m->{},client,config);
 java.lang.reflect.Method post=LiveUploader.class.getDeclaredMethod("post",String.class,String.class,String.class);post.setAccessible(true);
 try{for(String endpoint:Arrays.asList("live","logs")){
 try{post.invoke(u,endpoint,key,"{}");fail("Disabled HTTP must be rejected");}catch(java.lang.reflect.InvocationTargetException ex){assertTrue(ex.getCause() instanceof java.io.IOException);}
 }assertTrue(sent.isEmpty());allowed.set(true);post.invoke(u,"logs",key,"{}");assertEquals(1,sent.size());allowed.set(false);
 try{post.invoke(u,"live",key,"{\"stop\":true}");fail("Revoked HTTP must be rejected");}catch(java.lang.reflect.InvocationTargetException ex){assertTrue(ex.getCause() instanceof java.io.IOException);}assertEquals(1,sent.size());}finally{u.close();}
 }
}
