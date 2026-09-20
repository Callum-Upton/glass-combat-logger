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
    private volatile boolean busy,failed;
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
            key=next;live=stream;upload=save;failed=false;recording="";sent=0;
            if((stream||save)&&!validKey(next))notify.accept("Add a valid plugin connection key in settings. Local capture continues.");
        }
    }
    @SuppressWarnings("unchecked") void tick(Map<String,Object> encounter){
        if(!config.allowNetworking()||!live||failed||busy||!validKey(key)||encounter==null||!encounter.containsKey("boss"))return;
        String id=(String)encounter.get("id");if(!id.equals(recording)){recording=id;sent=0;}
        List<Map<String,Object>> ticks=(List<Map<String,Object>>)encounter.get("ticks");if(ticks.size()-sent<5)return;
        int end=Math.min(ticks.size(),sent+30);Map<String,Object> meta=new LinkedHashMap<>(encounter);meta.remove("ticks");
        Map<String,Object> batch=new LinkedHashMap<>();batch.put("meta",meta);batch.put("from",sent);batch.put("ticks",ticks.subList(sent,end));
        String json=gson.toJson(batch),credential=key;
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024*1024){failed=true;notify.accept("Live batch too large. Local capture continues; upload the finished log.");return;}
        busy=true;
        if(submit(()->{try{if(!live||!key.equals(credential))return;post("live",credential,json);if(key.equals(credential)&&recording.equals(id))sent=end;}catch(Exception e){if(key.equals(credential)&&recording.equals(id))failed=true;notify.accept("Live connection stopped. Local recording continues. Toggle live logging to reconnect.");}finally{busy=false;}})==false)busy=false;
    }
    void completed(String json,String id){
        String credential=key;boolean shouldUpload=upload;
        if(!config.allowNetworking()||!validKey(credential))return;
        submit(()->{
            if(!key.equals(credential))return;
            sendStop(credential,id);
            if(!shouldUpload||!upload)return;
            try{post("logs",credential,"{\"researchConsent\":true,\"log\":"+json+"}");notify.accept("Fight uploaded to Zenyte. Local log kept.");}
            catch(Exception e){notify.accept("Automatic upload failed. Your local log is safe; upload it through My logs.");}
        });
    }
    private void sendStop(String credential,String id){try{post("live",credential,gson.toJson(Map.of("stop",true,"recording",id)));}catch(Exception ignored){}}
    private boolean submit(Runnable task){try{queue.execute(task);return true;}catch(RejectedExecutionException e){notify.accept("Upload queue full. Local logs are safe; upload through My logs.");return false;}}
    private void post(String endpoint,String credential,String json)throws Exception{
        if(transport!=null){sendTestTransport(endpoint,credential,json);return;}
        Request request=new Request.Builder().url("https://zenyte.gg/api/plugin/"+endpoint)
            .header("Authorization","Bearer "+credential)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),json)).build();
        Call call=http.newCall(request);
        call.timeout().timeout(endpoint.equals("logs")?60:10,TimeUnit.SECONDS);
        try(Response response=executeRequest(call)){
            if(!response.isSuccessful())throw new java.io.IOException("Upload rejected");
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
    void close(){queue.shutdown();}
}
