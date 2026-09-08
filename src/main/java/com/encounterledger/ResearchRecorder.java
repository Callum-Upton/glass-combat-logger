package com.encounterledger;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/** Bounded, opt-in raw observations; intentionally independent of every boss profile. */
final class ResearchRecorder {
    static final int MAX_EVENTS=30000, MAX_TICKS=1000;
    private final Path outputRoot;
    private final Client client;
    private final EncounterLedgerConfig config;
    private final Gson gson;
    private final Executor writer;
    private final Consumer<String> notify;
    private final ClientThread clientThread;
    private final PositionRecorder positions=new PositionRecorder();
    private final Set<GraphicsObject> trackedGraphics=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<GraphicsObject,WorldPoint> graphicPositions=new IdentityHashMap<>();
    private final Map<TileObject,Integer> objectStates=new IdentityHashMap<>();
    private final Map<Object,Integer> identities=new IdentityHashMap<>();
    private Map<String,Object> session;
    private List<Map<String,Object>> events;
    private int startTick,nextIdentity,partNumber,partStartTick;
    private long sequence;
    private String sessionId,sessionStartedAt;
    private String sessionFolder, encounterLabel="Research";
    private volatile boolean writeFailed;
    private boolean arenaPending=true;
    private int arenaNumber;
    private String sceneKey;
    private boolean latched;
    private boolean combinedEncounter;
    void retainForEncounter() { combinedEncounter=true; }
    void beginEncounter(String label) {
        if(!config.combinedCapture())return;
        encounterLabel=label;
        combinedEncounter=true;
        onGameTick(new GameTick());
    }
    String activeFolder() {return session==null?null:sessionFolder;}
    void releaseEncounter() {
        combinedEncounter=false;
        stop("encounter_ended");latched=false;
    }
    ResearchRecorder(Client client,EncounterLedgerConfig config,Gson gson,Executor writer,Consumer<String> notify,ClientThread clientThread) {
        this(client,config,gson,writer,notify,clientThread,RuneLite.RUNELITE_DIR.toPath().resolve("encounter-ledger").resolve("research"));
    }
    ResearchRecorder(Client client,EncounterLedgerConfig config,Gson gson,Executor writer,Consumer<String> notify,ClientThread clientThread,Path outputRoot) {
        this.outputRoot=outputRoot;
        this.client=client;this.config=config;this.gson=gson;this.writer=writer;this.notify=notify;this.clientThread=clientThread;
    }
    static Map<String,Object> map(Object... fields) {
        Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<fields.length;i+=2)m.put((String)fields[i],fields[i+1]);return m;
    }
    private String text(String s) {return s==null?null:s.substring(0,Math.min(240,s.length()));}
    private int identity(Object object) {return identities.computeIfAbsent(object,k->++nextIdentity);}
    private boolean nearby(WorldPoint point) {
        Player p=client.getLocalPlayer();return p!=null && point!=null && p.getWorldLocation()!=null && point.distanceTo(p.getWorldLocation())<=48;
    }
    private boolean nearby(Actor a) {return a!=null && client.getLocalPlayer()!=null && a.getWorldView()==client.getLocalPlayer().getWorldView() && nearby(a.getWorldLocation());}
    private Map<String,Object> actor(Actor a) {
        if(a==null)return null;
        return map("identity",identity(a),"kind",a==client.getLocalPlayer()?"local_player":a instanceof NPC?"npc":"other_player",
            "npcId",a instanceof NPC?((NPC)a).getId():null,"index",a instanceof NPC?((NPC)a).getIndex():null,
            "name",a instanceof NPC?text(a.getName()):null,"position",positions.position(client,a),"animationId",a.getAnimation(),
            "healthRatio",a.getHealthRatio(),"healthScale",a.getHealthScale());
    }
    private Map<String,Object> point(WorldView view,WorldPoint world) {
        WorldPoint p=positions.normalise(client,view,world);return p==null?null:map("x",p.getX(),"y",p.getY(),"plane",p.getPlane());
    }
    private void emit(String kind,Map<String,Object> data) {
        if(session==null)return;
        if(writeFailed){stop("storage_failure");return;}
        events.add(map("kind",kind,"tick",client.getTickCount()-startTick,"clientTick",client.getTickCount(),"clientCycle",client.getGameCycle(),"observedAt",Instant.now().toString(),"sequence",sequence++,"data",data));
        if(events.size()>=MAX_EVENTS)finishPart("event_limit",true);
    }
    @Subscribe public void onConfigChanged(ConfigChanged event) {
        if("encounterledger".equals(event.getGroup()) && ("researchMode".equals(event.getKey())||"combinedCapture".equals(event.getKey())))clientThread.invoke(()->{
            if(!config.researchMode()&&!config.combinedCapture()&&!combinedEncounter){stop("manual_stop");latched=false;}
        });
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if(event.getGameState()==GameState.LOADING)arenaPending=true;
        if(event.getGameState()==GameState.LOGIN_SCREEN || event.getGameState()==GameState.HOPPING || event.getGameState()==GameState.CONNECTION_LOST)stop("game_state_"+event.getGameState().name());
    }
    String activeSessionId() { return session==null?null:sessionId; }
    @Subscribe(priority=100) public void onGameTick(GameTick event) {
        if(config.combinedCapture()&&!combinedEncounter)return;
        if(!config.researchMode()&&!config.combinedCapture()&&!combinedEncounter){stop("manual_stop");latched=false;return;}
        Player player=client.getLocalPlayer();
        if(player==null || client.getGameState()!=GameState.LOGGED_IN)return;
        if(session==null && !latched) {
            latched=true;identities.clear();objectStates.clear();trackedGraphics.clear();graphicPositions.clear();nextIdentity=0;startTick=client.getTickCount();
            sessionId=UUID.randomUUID().toString();sessionStartedAt=Instant.now().toString();partNumber=0;sequence=0;writeFailed=false;arenaPending=true;arenaNumber=0;sceneKey=null;
            sessionFolder=combinedEncounter?RecordingFolder.name(encounterLabel,sessionId):sessionId;
            newPart();
            notify.accept("Research recording started. Folder: research/"+sessionFolder+(config.combinedCapture()?". Combined capture follows automatic encounter boundaries.":". Disable Research mode to save."));
            // Baseline includes pre-existing objects and inventory, not only subsequent changes.
            baselineObjects(player);
            inventory(InventoryID.INVENTORY.getId());inventory(InventoryID.EQUIPMENT.getId());
            int[] varps=client.getVarps();
            if(varps!=null)emit("varp_baseline",map("values",Arrays.copyOf(varps,Math.min(varps.length,10000)),"truncated",varps.length>10000));
        }
        if(session==null)return;
        WorldView world=player.getWorldView();
        String key=world==null?null:world.getId()+":"+world.getBaseX()+":"+world.getBaseY()+":"+player.getWorldLocation().getPlane()+":"+Arrays.deepHashCode(world.getInstanceTemplateChunks());
        if(world!=null && (arenaPending || !Objects.equals(key,sceneKey))) {
            Map<String,Object> arena=new ArenaSnapshot().capture(client,player);
            if(arena!=null) {
                String filename=String.format(Locale.ROOT,"arena-%04d.json",++arenaNumber);
                arena.put("sessionId",sessionId);arena.put("clientTick",client.getTickCount());arena.put("observedAt",Instant.now().toString());
                save(filename,arena);
                emit("arena_snapshot",map("file",filename));arenaPending=false;sceneKey=key;
            }
        }
        List<String> prayers=new ArrayList<>();for(Prayer prayer:Prayer.values())if(client.isPrayerActive(prayer))prayers.add(prayer.name());
        emit("player_tick",map("actor",actor(player),"target",actor(player.getInteracting()),"hp",client.getBoostedSkillLevel(Skill.HITPOINTS),"prayer",client.getBoostedSkillLevel(Skill.PRAYER),
            "runEnergy",client.getEnergy(),"specialAttack",client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT),"prayers",prayers));
        if(client.getGraphicsObjects()!=null)for(GraphicsObject g:client.getGraphicsObjects()) {
            if(session==null)break;
            graphic("scene_graphic_tick",g);
        }
        baselineObjects(player);
        int count=0;
        if(client.getNpcs()!=null)for(NPC npc:client.getNpcs())if(nearby(npc)) {
            if(count++>=128){emit("snapshot_truncated",map("collection","npcs","limit",128));break;}
            NPCComposition composition=npc.getTransformedComposition();
            emit("npc_tick",map("actor",actor(npc),"transformedId",composition==null?null:composition.getId(),"size",composition==null?null:composition.getSize(),"dead",npc.isDead()));
        }
        if(session!=null && client.getTickCount()-partStartTick>=MAX_TICKS-1)finishPart("tick_limit",true);
        pruneIdentities();
    }
    private void inventory(int id) {
        if(session==null)return;
        ItemContainer container=client.getItemContainer(id);if(container==null)return;
        List<Map<String,Object>> items=new ArrayList<>();Item[] all=container.getItems();
        for(int i=0;i<Math.min(all.length,64);i++)if(all[i].getId()>=0)items.add(map("slot",i,"id",all[i].getId(),"quantity",all[i].getQuantity()));
        emit("inventory",map("containerId",id,"items",items));
    }
    @Subscribe public void onItemContainerChanged(ItemContainerChanged e) {if(e.getContainerId()==InventoryID.INVENTORY.getId() || e.getContainerId()==InventoryID.EQUIPMENT.getId())inventory(e.getContainerId());}
    @Subscribe public void onNpcSpawned(NpcSpawned e) {if(session!=null && nearby(e.getNpc()))emit("npc_spawn",actor(e.getNpc()));}
    @Subscribe public void onNpcDespawned(NpcDespawned e) {if(session!=null && nearby(e.getNpc()))emit("npc_despawn",actor(e.getNpc()));identities.remove(e.getNpc());}
    @Subscribe public void onAnimationChanged(AnimationChanged e) {if(session!=null && nearby(e.getActor()))emit("animation",actor(e.getActor()));}
    @Subscribe public void onActorDeath(ActorDeath e) {if(session!=null && nearby(e.getActor()))emit("actor_death",actor(e.getActor()));}
    @Subscribe public void onHitsplatApplied(HitsplatApplied e) {
        if(session==null || !nearby(e.getActor()))return;
        Hitsplat h=e.getHitsplat();emit("hitsplat",map("actor",actor(e.getActor()),"type",h.getHitsplatType(),"amount",h.getAmount(),"mine",h.isMine(),"others",h.isOthers()));
    }
    @Subscribe public void onGraphicChanged(GraphicChanged e) {
        if(session==null || !nearby(e.getActor()) || e.getActor().getSpotAnims()==null)return;
        List<Map<String,Object>> graphics=new ArrayList<>();for(ActorSpotAnim s:e.getActor().getSpotAnims())if(graphics.size()<32)graphics.add(map("id",s.getId(),"startCycle",s.getStartCycle()));
        emit("actor_graphics",map("actor",actor(e.getActor()),"graphics",graphics));
    }
    @Subscribe public void onProjectileMoved(ProjectileMoved e) {
        if(session==null || client.getLocalPlayer()==null || client.getLocalPlayer().getWorldView()!=client.getTopLevelWorldView())return;
        Projectile p=e.getProjectile();WorldView view=client.getLocalPlayer().getWorldView();
        WorldPoint current=WorldPoint.fromLocal(view,(int)p.getX(),(int)p.getY(),p.getSourceLevel());
        if(!nearby(current) && !nearby(p.getTargetPoint()))return;
        emit("projectile_moved",map("identity",identity(p),"id",p.getId(),"position",point(view,current),"source",point(view,p.getSourcePoint()),"target",point(view,p.getTargetPoint()),
            "startCycle",p.getStartCycle(),"endCycle",p.getEndCycle(),"sourceActor",actor(p.getSourceActor()),"targetActor",actor(p.getTargetActor())));
    }
    @Subscribe public void onGraphicsObjectCreated(GraphicsObjectCreated e) {
        graphic("scene_graphic_created",e.getGraphicsObject());
    }
    private void graphic(String kind,GraphicsObject g) {
        if(session==null || client.getLocalPlayer()==null)return;
        if(g.getWorldView()!=client.getLocalPlayer().getWorldView() || g.getLocation()==null)return;
        WorldPoint world=WorldPoint.fromLocal(g.getWorldView(),g.getLocation().getX(),g.getLocation().getY(),g.getLevel());
        if(nearby(world)) {
            WorldPoint previous=graphicPositions.put(g,world);
            if("scene_graphic_tick".equals(kind) && world.equals(previous))return;
            if(!g.finished() && trackedGraphics.size()<256)trackedGraphics.add(g);
            emit(kind,map("identity",identity(g),"id",g.getId(),"position",point(g.getWorldView(),world),"startCycle",g.getStartCycle(),"frame",g.getAnimationFrame(),"finished",g.finished()));
        }
    }
    @Subscribe public void onClientTick(ClientTick event) {
        if(session==null)return;
        // Copy because reaching the event limit clears tracking during emit.
        for(GraphicsObject g:new ArrayList<>(trackedGraphics))if(g.finished()) {
            emit("scene_graphic_finished",map("identity",identity(g),"id",g.getId()));trackedGraphics.remove(g);graphicPositions.remove(g);
        }
    }
    @Subscribe public void onStatChanged(StatChanged e){if(session!=null)emit("stat_changed",map("skill",e.getSkill().name(),"base",e.getLevel(),"boosted",e.getBoostedLevel()));}
    @Subscribe public void onOverheadTextChanged(OverheadTextChanged e){if(session!=null && nearby(e.getActor()) && e.getActor() instanceof NPC)emit("npc_overhead",map("actor",actor(e.getActor()),"text",text(e.getOverheadText())));}
    private void object(String kind,TileObject o) {
        if(session==null || o==null || !nearby(o.getWorldLocation()))return;
        emit(kind,map("identity",identity(o),"id",o.getId(),"position",point(client.getLocalPlayer().getWorldView(),o.getWorldLocation())));
    }
    private void baselineObjects(Player player) {
        Scene scene=player.getWorldView().getScene();if(scene==null)return;
        Tile[][][] tiles=scene.getTiles();int plane=player.getWorldLocation().getPlane();if(tiles==null || plane>=tiles.length)return;
        Set<TileObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());int count=0;
        for(Tile[] row:tiles[plane])if(row!=null)for(Tile t:row)if(t!=null && nearby(t.getWorldLocation())) {
            List<TileObject> list=new ArrayList<>();list.add(t.getGroundObject());list.add(t.getWallObject());list.add(t.getDecorativeObject());if(t.getGameObjects()!=null)Collections.addAll(list,t.getGameObjects());
            for(TileObject o:list)if(o!=null && seen.add(o)) {
                if(count++>=2048){objectStates.keySet().retainAll(seen);emit("snapshot_truncated",map("collection","baseline_objects","limit",2048));return;}
                ObjectComposition definition=client.getObjectDefinition(o.getId());
                if(definition!=null && definition.getImpostorIds()!=null)definition=definition.getImpostor();
                int transformed=definition==null?-1:definition.getId();
                Integer previous=objectStates.put(o,transformed);
                if(previous==null || previous!=transformed)emit(previous==null?"object_snapshot":"object_transform",map("identity",identity(o),"id",o.getId(),"transformedId",transformed,"position",point(player.getWorldView(),o.getWorldLocation())));
            }
        }
        objectStates.keySet().retainAll(seen);
    }
    @Subscribe public void onWallObjectSpawned(WallObjectSpawned e){object("wall_spawn",e.getWallObject());}
    @Subscribe public void onWallObjectDespawned(WallObjectDespawned e){object("wall_despawn",e.getWallObject());}
    @Subscribe public void onDecorativeObjectSpawned(DecorativeObjectSpawned e){object("decorative_spawn",e.getDecorativeObject());}
    @Subscribe public void onDecorativeObjectDespawned(DecorativeObjectDespawned e){object("decorative_despawn",e.getDecorativeObject());}
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned e){object("object_spawn",e.getGameObject());}
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned e){object("object_despawn",e.getGameObject());}
    @Subscribe public void onGroundObjectSpawned(GroundObjectSpawned e){object("ground_spawn",e.getGroundObject());}
    @Subscribe public void onGroundObjectDespawned(GroundObjectDespawned e){object("ground_despawn",e.getGroundObject());}
    @Subscribe public void onMenuOptionClicked(MenuOptionClicked e){if(session!=null)emit("action",map("option",text(e.getMenuOption()),"action",e.getMenuAction().name(),"id",e.getId(),"param0",e.getParam0(),"param1",e.getParam1()));}
    @Subscribe public void onVarbitChanged(VarbitChanged e){if(session!=null)emit("variable_changed",map("varpId",e.getVarpId(),"varbitId",e.getVarbitId(),"value",e.getValue()));}
    @Subscribe public void onChatMessage(ChatMessage e){if(session!=null && (e.getType()==ChatMessageType.GAMEMESSAGE || e.getType()==ChatMessageType.SPAM))emit("game_message",map("text",text(net.runelite.client.util.Text.removeTags(e.getMessage()))));}
    private void newPart() {
        partStartTick=client.getTickCount();events=new ArrayList<>();
        session=map("format","encounter-ledger-research","schemaVersion",2,"id",UUID.randomUUID().toString(),"sessionId",sessionId,
            "part",++partNumber,"startedAt",sessionStartedAt,"partStartedAt",Instant.now().toString(),"sessionStartClientTick",startTick,
            "recorder",map("playerName",client.getLocalPlayer()==null?null:text(client.getLocalPlayer().getName())),"tickDurationMs",600,
            "maxEvents",MAX_EVENTS,"maxTicks",MAX_TICKS,"radiusTiles",48,"coordinateSystem","instance_template","events",events);
    }
    void stop(String reason) {finishPart(reason,false);}
    private void finishPart(String reason,boolean continuing) {
        if(session==null)return;
        Map<String,Object> completed=session;
        completed.put("endedAt",Instant.now().toString());completed.put("endReason",reason);
        completed.put("continues",continuing);completed.put("limitReached",reason.endsWith("_limit"));
        String filename=String.format(Locale.ROOT,"part-%04d.research.json",partNumber);
        if(continuing)newPart();
        else {session=null;events=null;identities.clear();objectStates.clear();trackedGraphics.clear();graphicPositions.clear();}
        save(filename,completed);
        if(!continuing)notify.accept("Research recording ended ("+reason.replace('_',' ')+"). Saving final part...");
    }
    private void save(String filename,Map<String,Object> completed) {
        final String folder=sessionFolder;
        try {writer.execute(()->{
            try {
                Path directory=outputRoot.resolve(folder);Files.createDirectories(directory);
                Path target=directory.resolve(filename),temporary=directory.resolve(filename+".tmp");
                Files.write(temporary,gson.toJson(completed).getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
                try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,target);}
                notify.accept("Research saved: "+filename);
            }catch(Exception e){writeFailed=true;notify.accept("Research save failed for "+filename+"; recording will pause. "+e.getClass().getSimpleName());}
        });}catch(java.util.concurrent.RejectedExecutionException e){writeFailed=true;notify.accept("Research writer busy; "+filename+" could not be queued. Recording will pause.");}
    }
    private void pruneIdentities() {
        if(session==null)return;
        Set<Object> live=Collections.newSetFromMap(new IdentityHashMap<>());
        live.add(client.getLocalPlayer());live.addAll(objectStates.keySet());
        if(client.getNpcs()!=null)live.addAll(client.getNpcs());
        if(client.getPlayers()!=null)live.addAll(client.getPlayers());
        if(client.getProjectiles()!=null)for(Projectile p:client.getProjectiles())live.add(p);
        Set<GraphicsObject> graphics=Collections.newSetFromMap(new IdentityHashMap<>());
        if(client.getGraphicsObjects()!=null)for(GraphicsObject g:client.getGraphicsObjects())graphics.add(g);
        live.addAll(graphics);trackedGraphics.retainAll(graphics);graphicPositions.keySet().retainAll(graphics);
        identities.keySet().retainAll(live);
    }
}
