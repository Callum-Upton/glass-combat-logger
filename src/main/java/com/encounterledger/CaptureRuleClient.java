package com.encounterledger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import okhttp3.*;

/** Fixed-origin, consent-gated public rule data. No account key or telemetry is sent. */
final class CaptureRuleClient implements AutoCloseable {
    private final EncounterLedgerConfig config;
    private final OkHttpClient http;
    private final Path cache;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"zenyte-capture-rules");t.setDaemon(true);return t;});
    private volatile CaptureRules current;
    CaptureRuleClient(EncounterLedgerConfig config,OkHttpClient injected,Path cache){
        this.config=config;this.cache=cache;
        http=injected.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    }
    private boolean allowed(){return config.allowNetworking()&&config.downloadCaptureRules()&&LiveUploader.validKey(config.connectionKey());}
    CaptureRules current(){CaptureRules r=current;return allowed()&&r!=null&&(!r.diagnostic||config.captureTriggerTest())&&r.expiresAt>java.time.Instant.now().getEpochSecond()?r:null;}
    void start(){worker.scheduleWithFixedDelay(this::refresh,0,60,TimeUnit.SECONDS);}
    void refresh(){
        if(!allowed())return;
        long now=java.time.Instant.now().getEpochSecond();
        if(current==null)try{if(Files.size(cache)<=CaptureRules.MAX_BYTES)current=CaptureRules.parse(Files.readString(cache),now);}catch(Exception ignored){}
        try {
            Request request=new Request.Builder().url("https://zenyte.gg/api/plugin/capture-rules").get().build();
            Call call=http.newCall(request);call.timeout().timeout(10,TimeUnit.SECONDS);
            try(Response response=execute(call)){
                if(!response.isSuccessful()||response.body()==null)return;
                byte[] bytes=response.body().byteStream().readNBytes(CaptureRules.MAX_BYTES+1);
                if(bytes.length>CaptureRules.MAX_BYTES)return;
                String json=new String(bytes,StandardCharsets.UTF_8);
                CaptureRules candidate=CaptureRules.parse(json,now);
                if(!allowed()||(current!=null&&candidate.revision<current.revision))return;
                current=candidate;
                Files.createDirectories(cache.getParent());
                Path temp=cache.resolveSibling(cache.getFileName()+".tmp");
                Files.writeString(temp,json,StandardCharsets.UTF_8);
                Files.move(temp,cache,StandardCopyOption.REPLACE_EXISTING);
            }
        } catch(Exception ignored){/* Valid cached rules remain usable until expiry; local recording is unaffected. */}
    }
    // Keep the live consent check immediately beside the network operation for Plugin Hub review.
    private Response execute(Call call)throws java.io.IOException {
        if(!config.allowNetworking() || !config.downloadCaptureRules() || !LiveUploader.validKey(config.connectionKey()))throw new java.io.IOException("Capture rule downloads disabled");
        return call.execute();
    }
    public void close(){worker.shutdownNow();}
}
