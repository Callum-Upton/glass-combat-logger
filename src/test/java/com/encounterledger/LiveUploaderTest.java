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

 @Test public void stopReturnsImmediatelyWhenNetworkingIsDisabled()throws Exception{
 java.util.concurrent.atomic.AtomicInteger checks=new java.util.concurrent.atomic.AtomicInteger();
 EncounterLedgerConfig config=new EncounterLedgerConfig(){public boolean allowNetworking(){checks.incrementAndGet();return false;}};
 LiveUploader u=new LiveUploader(new Gson(),m->{},(e,k,j)->fail("Disabled stop must not send"),config);
 java.lang.reflect.Method stop=LiveUploader.class.getDeclaredMethod("sendStop",String.class,String.class);stop.setAccessible(true);
 try{stop.invoke(u,key,"recording");assertEquals("Stop must check consent before calling post",1,checks.get());}finally{u.close();}
 }
 @Test public void configureStopQueuedBeforeRevocationDoesNotReachHttp()throws Exception{
 java.util.concurrent.atomic.AtomicBoolean allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
 EncounterLedgerConfig config=new EncounterLedgerConfig(){public boolean allowNetworking(){return allowed.get();}};
 java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
 List<okhttp3.Request> requests=new ArrayList<>();
 okhttp3.OkHttpClient client=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{requests.add(chain.request());entered.countDown();try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("Test timed out");}catch(InterruptedException e){throw new java.io.IOException(e);}return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),"{}")).build();}).build();
 LiveUploader u=new LiveUploader(new Gson(),m->{},client,config);
 try{u.configure(key,true,false);u.tick(log());assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));u.configure(key,false,false);allowed.set(false);release.countDown();u.awaitIdle();assertEquals("Only the already-started batch may execute",1,requests.size());}finally{release.countDown();u.close();}
 }
 @Test public void outageRecoversWithoutToggleAndUploadsRetry()throws Exception{
 java.util.concurrent.atomic.AtomicInteger liveCalls=new java.util.concurrent.atomic.AtomicInteger(),uploads=new java.util.concurrent.atomic.AtomicInteger();
 List<String> messages=new java.util.concurrent.CopyOnWriteArrayList<>();
 LiveUploader u=new LiveUploader(new Gson(),messages::add,(e,k,j)->{if(e.equals("live")&&!j.contains("stop")&&liveCalls.incrementAndGet()==1)throw new java.io.IOException();if(e.equals("logs")&&uploads.incrementAndGet()==1)throw new LiveUploader.HttpFailure(503,false);},enabled);
 u.retryBaseMillis=1;
 try{u.configure(key,true,true);u.tick(log());u.awaitIdle();Thread.sleep(10);u.tick(log());u.awaitIdle();assertEquals(2,liveCalls.get());
 u.completed("{}","recording");for(int i=0;i<100&&uploads.get()<2;i++)Thread.sleep(10);u.awaitIdle();assertEquals(2,uploads.get());assertTrue(messages.contains("Live connection restored."));assertTrue(messages.stream().anyMatch(m->m.contains("Fight uploaded")));}finally{u.close();}
 }
 @Test public void serverRestartReplaysFromZeroAndBusyKeepsSameBatch()throws Exception{
 List<Integer> offsets=new java.util.concurrent.CopyOnWriteArrayList<>();
 LiveUploader u=new LiveUploader(new Gson(),m->{},(e,k,j)->{if(j.contains("stop"))return;int from=new Gson().fromJson(j,com.google.gson.JsonObject.class).get("from").getAsInt();offsets.add(from);if(offsets.size()==2)throw new LiveUploader.HttpFailure(409,true);if(offsets.size()==3)throw new LiveUploader.HttpFailure(429,false);},enabled);u.retryBaseMillis=1;
 try{u.configure(key,true,false);Map<String,Object> data=log();u.tick(data);u.awaitIdle();
 @SuppressWarnings("unchecked") List<Map<String,Object>> ticks=(List<Map<String,Object>>)data.get("ticks");for(int i=10;i<20;i++)ticks.add(new LinkedHashMap<>(Map.of("tick",i)));
 for(int i=0;i<3;i++){Thread.sleep(10);u.tick(data);u.awaitIdle();}assertEquals(Arrays.asList(0,10,0,0),offsets);}finally{u.close();}
 }
 @Test public void permanentRejectionsDoNotRetryAndTransientRetriesAreBounded()throws Exception{
 for(int status:new int[]{401,400,409,503}){java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();List<String> messages=new java.util.concurrent.CopyOnWriteArrayList<>();
 LiveUploader u=new LiveUploader(new Gson(),messages::add,(e,k,j)->{if(e.equals("logs")){calls.incrementAndGet();throw new LiveUploader.HttpFailure(status,false);}},enabled);u.retryBaseMillis=1;
 try{u.configure(key,false,true);u.completed("{}","recording");for(int i=0;i<100&&messages.stream().noneMatch(m->m.contains("Automatic upload failed"));i++)Thread.sleep(10);u.awaitIdle();assertEquals(status==503?6:1,calls.get());assertTrue(messages.stream().anyMatch(m->m.contains("Automatic upload failed")));}finally{u.close();}}
 }
 @Test public void disablingUploadCancelsScheduledRetry()throws Exception{
 java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();LiveUploader u=new LiveUploader(new Gson(),m->{},(e,k,j)->{if(e.equals("logs")){calls.incrementAndGet();throw new java.io.IOException();}},enabled);u.retryBaseMillis=100;
 try{u.configure(key,false,true);u.completed("{}","recording");u.awaitIdle();u.configure(key,false,false);Thread.sleep(200);u.awaitIdle();assertEquals(1,calls.get());}finally{u.close();}
 }
 @Test public void liveToggleDoesNotCancelPendingUploadAndDuplicateCompletionIsIgnored()throws Exception{
 java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 LiveUploader u=new LiveUploader(new Gson(),m->{},(e,k,j)->{if(e.equals("logs")&&calls.incrementAndGet()==1)throw new java.io.IOException();},enabled);u.retryBaseMillis=100;
 try{u.configure(key,true,true);u.completed("{}","recording");u.awaitIdle();u.completed("{}","recording");u.configure(key,false,true);
 for(int i=0;i<100&&calls.get()<2;i++)Thread.sleep(10);u.awaitIdle();assertEquals(2,calls.get());}finally{u.close();}
 }
 @Test public void httpRestartCodeIsRecognizedButOtherConflictsArePermanent()throws Exception{
 for(String body:Arrays.asList("{\"code\":\"LIVE_RESTART_REQUIRED\"}","{\"error\":\"Conflicting live batch.\"}")){
 okhttp3.OkHttpClient client=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(409).message("Conflict").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),body)).build()).build();
 LiveUploader u=new LiveUploader(new Gson(),m->{},client,enabled);
 java.lang.reflect.Method post=LiveUploader.class.getDeclaredMethod("post",String.class,String.class,String.class);post.setAccessible(true);
 try{post.invoke(u,"live",key,"{}");fail("Expected conflict");}catch(java.lang.reflect.InvocationTargetException e){LiveUploader.HttpFailure error=(LiveUploader.HttpFailure)e.getCause();assertEquals(409,error.status);assertEquals(body.contains("LIVE_RESTART_REQUIRED"),error.restart);}finally{u.close();}
 }
 }
}
