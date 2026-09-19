package com.encounterledger;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Opt-in, bounded outbound regular replay data. Never submits research files or config. */
final class LiveUploader {
    private final Gson gson;
    private final Consumer<String> notify;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ThreadPoolExecutor queue=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"zenyte-upload");t.setDaemon(true);return t;});
    private volatile boolean busy,failed;
    private volatile String key="";
    private volatile boolean live,upload;
    private volatile String recording="";private volatile int sent;
    interface Transport {void send(String endpoint,String key,String json)throws Exception;}
    private Transport transport;
    LiveUploader(Gson gson,Consumer<String> notify){this.gson=gson;this.notify=notify;}
    LiveUploader(Gson gson,Consumer<String> notify,Transport transport){this(gson,notify);this.transport=transport;}
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
        if(!live||failed||busy||!validKey(key)||encounter==null||!encounter.containsKey("boss"))return;
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
        if(!validKey(credential))return;
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
        if(transport!=null){transport.send(endpoint,credential,json);return;}
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://zenyte.gg/api/plugin/"+endpoint)).timeout(Duration.ofSeconds(endpoint.equals("logs")?60:10))
            .header("Authorization","Bearer "+credential).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
        if(response.statusCode()<200||response.statusCode()>=300)throw new java.io.IOException("Upload rejected");
    }
    void close(){queue.shutdown();}
}
