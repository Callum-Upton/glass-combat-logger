package com.encounterledger;

import com.google.gson.Gson;
import okhttp3.*;


import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Opt-in, bounded outbound regular replay data. Never submits research files or config. */
final class LiveUploader {
    private final EncounterLedgerConfig config;
    private final Gson gson;
    private final Consumer<String> notify;
    private final OkHttpClient http;
    private final ThreadPoolExecutor queue=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"zenyte-upload");t.setDaemon(true);return t;});
    private final ScheduledExecutorService retries=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"zenyte-retry");t.setDaemon(true);return t;});
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong pendingBytes=new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean busy,failed,closed;
    private volatile long generation,uploadGeneration,nextLiveAttempt;
    private int liveFailures;
    long retryBaseMillis=1000;
    static final class HttpFailure extends java.io.IOException {
        final int status;final boolean restart;
        HttpFailure(int status,boolean restart){super("Upload rejected: "+status);this.status=status;this.restart=restart;}
    }
    private boolean retryable(Exception e){return !(e instanceof HttpFailure)||((HttpFailure)e).status==429||((HttpFailure)e).status>=500;}
    private long retryDelay(int failures){return Math.min(30000,retryBaseMillis*(1L<<Math.min(5,Math.max(0,failures-1))));}
    private volatile String key="";
    private volatile boolean live,upload;
    private volatile String recording="";private volatile int sent;
    interface Transport {void send(String endpoint,String key,String json)throws Exception;}
    private Transport transport;
    LiveUploader(Gson gson,Consumer<String> notify,OkHttpClient client,EncounterLedgerConfig config){this.config=config;this.gson=gson;this.notify=notify;this.http=client.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(8,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(60,TimeUnit.SECONDS).build();}
    LiveUploader(Gson gson,Consumer<String> notify,Transport transport,EncounterLedgerConfig config){this.config=config;this.gson=gson;this.notify=notify;this.http=null;this.transport=transport;}
    void awaitIdle()throws Exception {queue.submit(()->{}).get(5,TimeUnit.SECONDS);}
    static boolean validKey(String key){return key!=null&&key.matches("zy_[A-Za-z0-9_-]{43}");}
    void configure(String next,boolean stream,boolean save){
        next=next==null?"":next.trim();
        if(!next.equals(key)||live!=stream||upload!=save){
            String old=key,id=recording;
            if(live&&validKey(old)&&(!stream||!old.equals(next)))submit(()->sendStop(old,id));
            if(!next.equals(key)||upload!=save)uploadGeneration++;
            generation++;key=next;live=stream;upload=save;failed=false;recording="";sent=0;nextLiveAttempt=0;liveFailures=0;
            if((stream||save)&&!validKey(next))notify.accept("Add a valid plugin connection key in settings. Local capture continues.");
        }
    }
    @SuppressWarnings("unchecked") void tick(Map<String,Object> encounter){
        if(!config.allowNetworking()||!live||closed||busy||!validKey(key)||encounter==null||!encounter.containsKey("boss"))return;
        String id=(String)encounter.get("id");if(!id.equals(recording)){recording=id;sent=0;failed=false;liveFailures=0;nextLiveAttempt=0;}
        if(failed||System.currentTimeMillis()<nextLiveAttempt)return;
        List<Map<String,Object>> ticks=(List<Map<String,Object>>)encounter.get("ticks");if(ticks.size()-sent<5)return;
        int end=Math.min(ticks.size(),sent+30);Map<String,Object> meta=new LinkedHashMap<>(encounter);meta.remove("ticks");
        Map<String,Object> batch=new LinkedHashMap<>();batch.put("meta",meta);batch.put("from",sent);batch.put("ticks",ticks.subList(sent,end));
        String json=gson.toJson(batch),credential=key;long epoch=generation;
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024*1024){failed=true;notify.accept("Live batch too large. Local capture continues; upload the finished log.");return;}
        busy=true;
        if(submit(()->{try{
            if(!live||generation!=epoch||!key.equals(credential))return;
            post("live",credential,json);
            if(generation==epoch&&recording.equals(id)){sent=end;if(liveFailures>0)notify.accept("Live connection restored.");liveFailures=0;nextLiveAttempt=0;}
        }catch(Exception e){
            if(generation==epoch&&recording.equals(id)){
                boolean restart=e instanceof HttpFailure&&((HttpFailure)e).restart;
                if(restart)sent=0;
                if(restart||retryable(e)){
                    nextLiveAttempt=System.currentTimeMillis()+retryDelay(++liveFailures);
                    if(liveFailures==1)notify.accept("Live connection interrupted. Reconnecting automatically; local recording continues.");
                }else{failed=true;notify.accept("Live request rejected. Check your connection settings; local recording continues.");}
            }
        }finally{busy=false;}})==false)busy=false;
    }
    void completed(String json,String id){
        String credential=key;boolean shouldUpload=upload;long epoch=uploadGeneration;
        if(closed||!config.allowNetworking()||!validKey(credential))return;
        if(!shouldUpload){submit(()->{if(uploadGeneration==epoch)sendStop(credential,id);});return;}
        long bytes=2L*json.length();
        synchronized(pending){
            if(pending.contains(id))return;
            if(pending.size()>=2||pendingBytes.get()+bytes>192L*1024*1024){notify.accept("Upload queue full. Local logs are safe; upload through My logs.");return;}
            pending.add(id);pendingBytes.addAndGet(bytes);
        }
        retryUpload(json,id,credential,epoch,bytes,0);
    }
    private void retryUpload(String json,String id,String credential,long epoch,long bytes,int attempt){
        Runnable release=()->{synchronized(pending){if(pending.remove(id))pendingBytes.addAndGet(-bytes);}};
        if(closed||uploadGeneration!=epoch||!upload||!config.allowNetworking()||!key.equals(credential)){release.run();return;}
        Runnable work=()->{
            if(closed||uploadGeneration!=epoch||!upload||!config.allowNetworking()||!key.equals(credential)){release.run();return;}
            try{
                if(attempt==0)sendStop(credential,id);
                if(uploadGeneration!=epoch||!upload||!config.allowNetworking()){release.run();return;}
                post("logs",credential,"{\"researchConsent\":true,\"log\":"+json+"}");
                release.run();notify.accept("Fight uploaded to Zenyte. Local log kept.");
            }catch(Exception e){
                if(!closed&&uploadGeneration==epoch&&upload&&config.allowNetworking()&&retryable(e)&&attempt<5){
                    if(attempt==0)notify.accept("Upload interrupted. Retrying automatically; local log kept.");
                    try{retries.schedule(()->retryUpload(json,id,credential,epoch,bytes,attempt+1),retryDelay(attempt+1),TimeUnit.MILLISECONDS);}
                    catch(RejectedExecutionException stopped){release.run();}
                }else{release.run();notify.accept("Automatic upload failed. Your local log is safe; upload it through My logs.");}
            }
        };
        if(!submit(work)){release.run();}
    }
    private void sendStop(String credential,String id){
        if(!config.allowNetworking())return;
        try{post("live",credential,gson.toJson(Map.of("stop",true,"recording",id)));}catch(Exception ignored){}
    }
    private boolean submit(Runnable task){try{queue.execute(task);return true;}catch(RejectedExecutionException e){notify.accept("Upload queue full. Local logs are safe; upload through My logs.");return false;}}
    private void post(String endpoint,String credential,String json)throws Exception{
        if(!config.allowNetworking())throw new java.io.IOException("Zenyte networking is disabled");
        if(transport!=null){sendTestTransport(endpoint,credential,json);return;}
        Request request=new Request.Builder().url("https://zenyte.gg/api/plugin/"+endpoint)
            .header("Authorization","Bearer "+credential)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),json)).build();
        Call call=http.newCall(request);
        call.timeout().timeout(endpoint.equals("logs")?60:10,TimeUnit.SECONDS);
        try(Response response=executeRequest(call)){
            if(!response.isSuccessful()){
                boolean restart=false;
                if(response.code()==409){try{restart="LIVE_RESTART_REQUIRED".equals(gson.fromJson(response.peekBody(4096).string(),com.google.gson.JsonObject.class).get("code").getAsString());}catch(Exception ignored){}}
                throw new HttpFailure(response.code(),restart);
            }
        }
    }
    // Keep this boundary limited to the live consent check and network operation.
    private Response executeRequest(Call call)throws java.io.IOException{
        if(!config.allowNetworking())throw new java.io.IOException("Zenyte networking is disabled");
        return call.execute();
    }
    private void sendTestTransport(String endpoint,String credential,String json)throws Exception{
        if(!config.allowNetworking())throw new java.io.IOException("Zenyte networking is disabled");
        transport.send(endpoint,credential,json);
    }
    void close(){closed=true;generation++;uploadGeneration++;retries.shutdownNow();queue.shutdownNow();synchronized(pending){pending.clear();pendingBytes.set(0);}}
}
