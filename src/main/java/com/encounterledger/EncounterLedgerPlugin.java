package com.encounterledger;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "Glass Combat Logger", description = "Local tick-by-tick PvM encounter recording", tags = {"combat", "pvm"})
public class EncounterLedgerPlugin extends Plugin
{
    private static final Logger LOG = LoggerFactory.getLogger(EncounterLedgerPlugin.class);
    @Inject private Client client;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    private RecordedTickOverlay recordedTickOverlay;
    @Inject private net.runelite.client.ui.ClientToolbar clientToolbar;
    private net.runelite.client.ui.NavigationButton logsNavigation;
    private volatile int displayedRecordedTick = -1;
    private volatile boolean tickRecording;
    int displayedRecordedTick() { return displayedRecordedTick; }
    boolean tickRecording() { return tickRecording; }
    @Inject private Gson gson;
    @Inject private EncounterLedgerConfig config;
    @Inject private ClientThread clientThread;
    @Inject private net.runelite.client.eventbus.EventBus eventBus;
    private ResearchRecorder research;
    private ThreadPoolExecutor writer;
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private Map<String, Object> encounter;
    private Map<String,Object> awaitingTiming;
    private int timingDeadline;
    private final Set<String> observedParticipants=new TreeSet<>();
    private void flushTiming() { if(awaitingTiming!=null){Map<String,Object> completed=awaitingTiming;awaitingTiming=null;saveEncounter(completed);if(completed.containsKey("researchSessionId")&&research!=null)research.releaseEncounter();} }
    private List<Map<String, Object>> ticks;
    private final java.util.Deque<Map<String,Object>> preCombat=new ArrayDeque<>();
    private int previousHp = -1, idle = 0, sequence = 0;
    private volatile boolean storageFailed;
    // Yama's adds have different IDs (Judge 14180, Void Flare 14179).
    private static final int YAMA_ID = 14176;
    private NPC encounterBoss;
    private boolean bossDeathPending, awaitingNextFight;
    private String deathEvidence;
    private final ConsumableTracker consumables = new ConsumableTracker();
    private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ActorSpotAnim, Integer> seenGraphics = new IdentityHashMap<>();
    private final SpellTracker spells = new SpellTracker();
    private final EffectLifecycleRecorder effectLifecycle=new EffectLifecycleRecorder();
    private final PositionRecorder positions = new PositionRecorder();
    private final HazardRecorder hazards = new HazardRecorder();
    private final ProjectileRecorder projectilePositions = new ProjectileRecorder();
    private final Map<ActorSpotAnim,Integer> seenSpellGraphics = new IdentityHashMap<>();
    private Player vengeanceTarget;
    private int vengeanceTargetTick = -100, previousVengeance = -1;
    private boolean targetVengeanceConfirmed, vengeanceOverhead;
    private Map<String,Object> previousSpellState;

    private void spellEvent(String[] spell,String evidence,int id,Actor recipient) {
        if (spell == null || storageFailed) return;
        pending.add(object("kind","player_spell","spellId",spell[0],"label",spell[1],"stage",spell[2],
            "category","spell","evidence",evidence,"evidenceId",id,"recipient",actor(recipient),"sequence",sequence++));
    }

    private void spellGraphic(Actor source) {
        if (source == null || source.getSpotAnims() == null || storageFailed) return;
        boolean self=source==client.getLocalPlayer();
        if (!self && source != vengeanceTarget) return;
        for(ActorSpotAnim spot:source.getSpotAnims()) {
            String[] spell=SpellTracker.graphic(spot.getId());
            if(spell==null || (!self && spot.getId()!=726))continue;
            if(Objects.equals(seenSpellGraphics.put(spot,spot.getStartCycle()),spot.getStartCycle()))continue;
            if(!self) {
                if(client.getTickCount()-vengeanceTargetTick>3) {targetVengeanceConfirmed=false;continue;}
                targetVengeanceConfirmed=true;
                spellEvent(new String[]{"vengeance_other","Vengeance Other cast on target","cast"},"target_graphic_after_local_cast_action",spot.getId(),source);
            } else {
                spellEvent(spell,"local_player_graphic",spot.getId(),source);
                if(spell[0].equals("mark_of_darkness")) {
                    ItemContainer worn=client.getItemContainer(InventoryID.EQUIPMENT);
                    Item weapon=worn==null?null:worn.getItem(3);
                    spells.markCast(client.getTickCount(),client.getBoostedSkillLevel(Skill.MAGIC),weapon!=null && weapon.getId()==29594);
                }
            }
        }
    }

    private BossProfile profile() {
        if (awaitingNextFight || storageFailed) return null;
        if (encounterBoss == null && client.getLocalPlayer() != null && client.getLocalPlayer().getInteracting() instanceof NPC) {
            NPC target = (NPC) client.getLocalPlayer().getInteracting();
            if (BossProfile.forNpc(target.getId()) != null) encounterBoss = target;
        }
        return encounterBoss == null ? null : BossProfile.forNpc(encounterBoss.getId());
    }

    private void mechanic(String[] ability, String evidence, int evidenceId, Actor source) {
        BossProfile profile = profile();
        if (ability == null || profile == null) return;
        pending.add(object("kind", "boss_ability", "bossId", profile.id(), "abilityId", ability[0],
            "label", ability[1], "category", ability[2], "evidence", evidence, "evidenceId", evidenceId,
            "recipient", actor(source), "clientTick", client.getTickCount(), "clientCycle", client.getGameCycle(), "sequence", sequence++));
    }

    @Subscribe public void onProjectileMoved(ProjectileMoved event) {
        BossProfile profile = profile();
        Projectile projectile = event.getProjectile();
        if (profile == null || profile.projectile(projectile.getId()) == null || !seenProjectiles.add(projectile)) return;
        mechanic(profile.projectile(projectile.getId()), "projectile_observed", projectile.getId(), null);
    }

    @Subscribe public void onNpcSpawned(NpcSpawned event) {
        BossProfile profile = profile();
        if (profile == null) return;
        int id = event.getNpc().getId();
        mechanic(profile.spawn(id), "npc_spawn", id, event.getNpc());
    }

    @Subscribe public void onOverheadTextChanged(OverheadTextChanged event) {
        if("Taste vengeance!".equalsIgnoreCase(event.getOverheadText())) {
            if(event.getActor()==client.getLocalPlayer()) vengeanceOverhead=true;
            else if(event.getActor()==vengeanceTarget && targetVengeanceConfirmed) {
                spellEvent(new String[]{"vengeance_other","Vengeance triggered on your target","trigger"},"tracked_target_overhead_observed",-1,vengeanceTarget);
                targetVengeanceConfirmed=false;
            }
        }
        if (profile() == null || event.getActor() != encounterBoss) return;
        mechanic(profile().overhead(event.getOverheadText()), "boss_overhead", -1, encounterBoss);
    }

    @Subscribe public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        if(encounter!=null && bossDeathPending && encounterBoss!=null && encounterBoss.getId()==YAMA_ID) {
            Map<String,Object> timing=KillTiming.parse(net.runelite.client.util.Text.removeTags(event.getMessage()));
            if(timing!=null)encounter.put("officialTiming",timing);
        }
        if(awaitingTiming!=null && client.getTickCount()<=timingDeadline) {
            String candidate=net.runelite.client.util.Text.removeTags(event.getMessage());
            if(candidate.startsWith("Fight duration:"))awaitingTiming.put("timingMessageObserved",candidate);
            Map<String,Object> timing=KillTiming.parse(net.runelite.client.util.Text.removeTags(event.getMessage()));
            if(timing!=null){awaitingTiming.put("officialTiming",timing);flushTiming();}
        }
        BossProfile profile = profile();
        if (profile != null) mechanic(profile.announcement(net.runelite.client.util.Text.removeTags(event.getMessage())),
            "game_announcement", -1, encounterBoss);
    }

    @Subscribe public void onGraphicChanged(GraphicChanged event) {
        spellGraphic(event.getActor());
        BossProfile profile = profile();
        Actor source = event.getActor();
        if (encounter != null && !storageFailed && source != null &&
            (source == client.getLocalPlayer() || source == encounterBoss ||
             (source instanceof NPC && profile != null && profile.observesNpcDamage(((NPC)source).getId())))) {
            pending.add(object("kind", "actor_visuals", "recipient", actor(source),
                "visuals", ActorVisualSnapshot.capture(source, client.getGameCycle()),
                "clientCycle", client.getGameCycle(), "clientTick", client.getTickCount(), "sequence", sequence++));
        }
        if (profile == null || (source != encounterBoss && source != client.getLocalPlayer()) || source.getSpotAnims() == null) return;
        for (ActorSpotAnim spot : source.getSpotAnims()) {
            String[] ability = profile.graphic(spot.getId(), source == client.getLocalPlayer());
            if (ability != null && !Objects.equals(seenGraphics.put(spot, spot.getStartCycle()), spot.getStartCycle()))
                mechanic(ability, "actor_graphic", spot.getId(), source);
        }
    }

    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event) {
        if(!event.isConsumed() && event.getMenuAction()==MenuAction.WIDGET_TARGET_ON_PLAYER
            && net.runelite.client.util.Text.removeTags(event.getMenuTarget()).toLowerCase(Locale.ROOT).contains("vengeance other")) {
            vengeanceTarget=null;targetVengeanceConfirmed=false;
            for(Player player:client.getPlayers()) if(player.getId()==event.getId()) {vengeanceTarget=player;vengeanceTargetTick=client.getTickCount();break;}
        }
        if (storageFailed || awaitingNextFight || event.isConsumed() || event.getItemId() < 0) return;
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        int slot = event.getParam0();
        if (container == null || slot < 0 || slot >= container.getItems().length) return;
        Item item = container.getItems()[slot];
        if (item.getId() != event.getItemId()) return;
        consumables.click(slot, item.getId(), item.getQuantity(), client.getItemDefinition(item.getId()).getName(),
            event.getMenuOption(), client.getTickCount());
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId() || storageFailed || awaitingNextFight) return;
        Item[] items = event.getItemContainer().getItems();
        for (int slot = 0; slot < 28; slot++) {
            int id = slot < items.length ? items[slot].getId() : -1;
            int quantity = slot < items.length ? items[slot].getQuantity() : 0;
            String name = id < 0 ? "" : client.getItemDefinition(id).getName();
            ConsumableTracker.Use use = consumables.changed(slot, id, quantity, name, client.getTickCount());
            if (use != null) pending.add(object("kind", "item_use", "label", use.name, "category", use.category,
                "itemId", use.id, "slot", use.slot, "evidence", "menu_and_inventory_change", "confidence", "corroborated",
                "sequence", sequence++));
        }
    }

    @Provides EncounterLedgerConfig provideConfig(ConfigManager manager) { return manager.getConfig(EncounterLedgerConfig.class); }

    @Override protected void startUp()
    {
        logsNavigation = net.runelite.client.ui.NavigationButton.builder()
            .tooltip("Glass Combat Logger").icon(LogsPanel.icon()).priority(8)
            .panel(new LogsPanel(RuneLite.RUNELITE_DIR.toPath().resolve("encounter-ledger"))).build();
        clientToolbar.addNavigation(logsNavigation);
        storageFailed = false;
        displayedRecordedTick = -1; tickRecording = false;
        recordedTickOverlay = new RecordedTickOverlay(this, config);
        overlayManager.add(recordedTickOverlay);
        spells.reset(); seenSpellGraphics.clear(); previousVengeance=-1;vengeanceTarget=null;targetVengeanceConfirmed=false;previousSpellState=null;
        encounterBoss = null; bossDeathPending = false; awaitingNextFight = false;
        writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), r -> {
            Thread thread = new Thread(r, "encounter-ledger-writer"); thread.setDaemon(true); return thread;
        });
        research=new ResearchRecorder(client,config,gson,writer,this::notifyChat,clientThread);
        eventBus.register(research);
    }

    @Override protected void shutDown()
    {
        if (logsNavigation != null) { clientToolbar.removeNavigation(logsNavigation); logsNavigation = null; }
        if (recordedTickOverlay != null) { overlayManager.remove(recordedTickOverlay); recordedTickOverlay = null; }
        if(research!=null){eventBus.unregister(research);research.stop("plugin_disabled");research=null;}
        finish("plugin_disabled");
        flushTiming();
        if (writer != null) writer.shutdown();
        consumables.clear(); seenProjectiles.clear(); seenGraphics.clear();
        pending.clear(); preCombat.clear(); previousHp = -1;
        encounterBoss = null; bossDeathPending = false; awaitingNextFight = false;
    }

    private static Map<String, Object> object(Object... values)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private Map<String, Object> actor(Actor actor)
    {
        if (actor == null) return null;
        if (actor == client.getLocalPlayer()) return object("kind", "player", "name", "You");
        if (actor instanceof NPC)
        {
            NPC npc = (NPC) actor;
            return object("kind", "npc", "id", npc.getId(), "index", npc.getIndex(), "name", npc.getName(),
                "healthRatio", npc.getHealthRatio(), "healthScale", npc.getHealthScale());
        }
        return object("kind", "other_player");
    }

    @Subscribe(priority=200) public void onHitsplatApplied(HitsplatApplied event)
    {
        if (storageFailed || client.getLocalPlayer() == null) return;
        Actor recipient = event.getActor();
        boolean self = recipient == client.getLocalPlayer();
        Hitsplat hit = event.getHitsplat();
        if (!self && (!(recipient instanceof NPC) || (!hit.isMine() && recipient != client.getLocalPlayer().getInteracting() && (profile()==null || !profile().observesNpcDamage(((NPC)recipient).getId()))))) return;
        if (recipient instanceof NPC && hit.isMine())
        {
            NPC npc = (NPC) recipient;
            if (awaitingNextFight && npc == encounterBoss) return;
            if (awaitingNextFight && !npc.isDead() && npc.getId() != 14179 && npc.getId() != 14180)
            {
                awaitingNextFight = false; encounterBoss = null;
            }
            if (!awaitingNextFight && BossProfile.forNpc(npc.getId()) != null) encounterBoss = npc;
        }
        if (awaitingNextFight) return;
        int type = hit.getHitsplatType();
        String kind = type == HitsplatID.HEAL ? "healing_hitsplat" :
            CombatMath.isHpDamage(type, hit.isMine(), hit.isOthers()) ? (self ? "damage_taken" : hit.isMine() ? "damage_done" : "other_damage") : "unclassified_hitsplat";
        if(encounter==null&&client.getLocalPlayer().isDead())return;
        if(research!=null&&config.combinedCapture()&&research.activeSessionId()==null&&("damage_taken".equals(kind)||"damage_done".equals(kind)))
            research.beginEncounter(encounterBoss!=null?encounterBoss.getName():recipient instanceof NPC?recipient.getName():client.getLocalPlayer().getInteracting() instanceof NPC?client.getLocalPlayer().getInteracting().getName():"PvM");
        Map<String,Object> damageEvent=object("kind", kind, "amount", hit.getAmount(), "hitsplatType", type, "recipient", actor(recipient),
            "ownership", self ? "incoming_source_unknown" : hit.isMine() ? "local_player" : "unattributed", "sequence", sequence++);
        if("damage_taken".equals(kind)) {
            damageEvent.put("positionAtHit",positions.position(client,client.getLocalPlayer()));
            damageEvent.put("clientCycle",client.getGameCycle());
            damageEvent.put("clientTick",client.getTickCount());
            damageEvent.put("actorVisualsAtHit",ActorVisualSnapshot.capture(recipient,client.getGameCycle()));
            damageEvent.put("hazardObservationsAtHit",hazards.atHit(client,client.getLocalPlayer(),profile()));
            List<String> candidates=hazards.possibleSources(client,client.getLocalPlayer(),profile());
            if(!candidates.isEmpty()) {
                damageEvent.put("possibleSources",candidates);
                damageEvent.put("sourceEvidence","visual_anchor_overlap_at_hitsplat");
                damageEvent.put("sourceConfidence","possible");
            }
        }
        pending.add(damageEvent);
    }

    @Subscribe public void onActorDeath(ActorDeath event)
    {
        if(encounter!=null && event.getActor()==client.getLocalPlayer()) playerDeathPending=true;
        if (!awaitingNextFight && event.getActor() == encounterBoss) markBossDeath("actor_death");
    }

    @Subscribe public void onNpcDespawned(NpcDespawned event)
    {
        // A despawn alone may mean a phase transition, teleport or disconnect, not a kill.
        if (!awaitingNextFight && event.getNpc() == encounterBoss
            && (encounterBoss.isDead() || encounterBoss.getHealthRatio() == 0)) markBossDeath("dead_npc_despawn");
    }

    private void markBossDeath(String evidence)
    {
        if (bossDeathPending || encounterBoss == null) return;
        bossDeathPending = true; deathEvidence = evidence;
        pending.add(object("kind", "boss_death", "recipient", actor(encounterBoss), "sequence", sequence++));
    }

    private int outsideEncounterTicks;
    private boolean playerDeathPending;
    // Null means no reliable area evidence: retain the generic idle fallback.
    Boolean encounterArea(Player player,BossProfile profile) {
        if(profile==null || !profile.hasEncounterArea() || player.getWorldView()==null)return null;
        net.runelite.api.coords.WorldPoint tile=positions.normalise(client,player.getWorldView(),player.getWorldLocation());
        if(tile==null)return null;
        return profile.containsEncounterTile(tile,player.getWorldView().isInstance());
    }

    private int lastAttackCycle=-1,lastAttackAnimation=-1;
    private void recordAttackCue(Player player) {
        int animation=player.getAnimation(),cycle=client.getGameCycle();
        if(storageFailed||!(player.getInteracting() instanceof NPC))return;
        if(cycle==lastAttackCycle&&animation==lastAttackAnimation)return;
        ItemContainer gear=client.getItemContainer(InventoryID.EQUIPMENT);
        Item weapon=gear==null?null:gear.getItem(3);
        int id=weapon==null?-1:weapon.getId();String name=id<0?"Unarmed":client.getItemDefinition(id).getName();
        if(!AttackTiming.cue(animation,name))return;
        lastAttackCycle=cycle;lastAttackAnimation=animation;
        int delay=AttackTiming.delay(animation,name);
        WeaponProfiles.Attack weaponAttack=WeaponProfiles.find(name,animation);
        Map<String,Object> cue=object("kind","attack_cue","label",name+" attack cue","category","attack","animationId",animation,"clientCycle",cycle,"clientTick",client.getTickCount(),"weaponName",name,"attackStyle",client.getVarpValue(43),"recipient",actor(player.getInteracting()),"evidence","local_attack_animation_start","confidence","observed_animation","sequence",sequence++);
        if(id>=0)cue.put("itemId",id);
        if(weaponAttack!=null)cue.put("label",name+" "+weaponAttack.kind);
        if(delay>0)cue.put("attackDelayTicks",delay);
        pending.add(cue);
    }
    @Subscribe public void onAnimationChanged(AnimationChanged event)
    {
        if (event.getActor() == client.getLocalPlayer()) {
            pending.add(object("kind", "animation", "animationId", event.getActor().getAnimation(), "sequence", sequence++));
            recordAttackCue(client.getLocalPlayer());
        }
        else if (event.getActor() instanceof NPC && profile() != null) {
            NPC npc = (NPC) event.getActor();
            if (profile().matches(npc.getId()) && npc != encounterBoss) return;
            mechanic(profile().animation(npc.getId(), npc.getAnimation()), "npc_animation", npc.getAnimation(), npc);
        }
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.CONNECTION_LOST)
        {
            finish(event.getGameState().name().toLowerCase(Locale.ROOT)); flushTiming(); pending.clear(); preCombat.clear(); previousHp = -1;
            consumables.clear(); seenProjectiles.clear(); seenGraphics.clear();
            spells.reset(); seenSpellGraphics.clear(); previousVengeance=-1;vengeanceTarget=null;targetVengeanceConfirmed=false;vengeanceOverhead=false;previousSpellState=null;
            encounterBoss = null; bossDeathPending = false; awaitingNextFight = false;
        }
    }

    private List<Map<String, Object>> items(InventoryID inventory)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        ItemContainer container = client.getItemContainer(inventory);
        if (container == null) return result;
        Item[] entries = container.getItems();
        for (int slot = 0; slot < entries.length; slot++)
        {
            Item item = entries[slot];
            if (item.getId() >= 0) result.add(object("slot", slot, "id", item.getId(), "quantity", item.getQuantity(), "name", client.getItemDefinition(item.getId()).getName()));
        }
        return result;
    }

    @Subscribe public void onClientTick(ClientTick event) {
        if(encounter!=null&&!storageFailed)effectLifecycle.scan(client,client.getLocalPlayer());
    }
    @Subscribe public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if(encounter!=null&&!storageFailed)effectLifecycle.observe(client,client.getLocalPlayer(),event.getGraphicsObject(),true);
    }
    @Subscribe public void onGameTick(GameTick event)
    {
        if(awaitingTiming!=null && client.getTickCount()>timingDeadline)flushTiming();
        Player player = client.getLocalPlayer();
        consumables.expire(client.getTickCount());
        seenProjectiles.removeIf(projectile -> projectile.getEndCycle() < client.getGameCycle());
        seenGraphics.values().removeIf(start -> start < client.getGameCycle() - 1200);
        if (player == null || client.getGameState() != GameState.LOGGED_IN || storageFailed) { pending.clear(); preCombat.clear(); return; }
        int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int vengeance=client.getVarbitValue(2450);
        if(vengeanceOverhead && (previousVengeance>0 || pending.stream().anyMatch(e->"player_spell".equals(e.get("kind")) && ("vengeance".equals(e.get("spellId")) || "vengeance_other".equals(e.get("spellId"))))))
            spellEvent(new String[]{"vengeance","Vengeance triggered","trigger"},"local_overhead_with_buff_evidence",-1,player);
        previousVengeance=vengeance;vengeanceOverhead=false;
        Map<String,Object> spellState=spells.snapshot(client.getTickCount(),vengeance,client.getVarbitValue(12411),client.getVarbitValue(12413));
        if(previousSpellState!=null) for(String id:Arrays.asList("vengeance","death_charge","thrall","mark_of_darkness")) {
            if(Boolean.TRUE.equals(previousSpellState.get(id)) && Boolean.FALSE.equals(spellState.get(id)))
                spellEvent(new String[]{id,id.replace('_',' ')+" no longer active","ended"},id.equals("mark_of_darkness")?"estimated_timer_elapsed":"active_varbit_cleared",-1,player);
        }
        previousSpellState=spellState;
        seenSpellGraphics.values().removeIf(start->start<client.getGameCycle()-1200);
        if (awaitingNextFight && player.getInteracting() instanceof NPC
            && player.getInteracting() != encounterBoss && !player.getInteracting().isDead()
            && ((NPC) player.getInteracting()).getId() != 14179 && ((NPC) player.getInteracting()).getId() != 14180)
        {
            awaitingNextFight = false; encounterBoss = null;
        }
        if (awaitingNextFight) { capturePreCombat(player,hp,spellState); previousHp = hp; pending.clear(); sequence = 0; return; }
        if (encounterBoss == null && player.getInteracting() instanceof NPC
            && BossProfile.forNpc(((NPC) player.getInteracting()).getId()) != null) encounterBoss = (NPC) player.getInteracting();
        if (encounterBoss != null && (encounter != null || !pending.isEmpty())
            && (encounterBoss.isDead() || encounterBoss.getHealthRatio() == 0)) markBossDeath("zero_hp_snapshot");
        boolean combatEvent = pending.stream().anyMatch(e -> "damage_done".equals(e.get("kind")) || "damage_taken".equals(e.get("kind")));
        boolean engaged = player.getInteracting() instanceof NPC && !player.getInteracting().isDead();
        if (encounter == null && combatEvent)
        {
            flushTiming();observedParticipants.clear();
            ticks = new ArrayList<>();
            for(Map<String,Object> buffered:preCombat){buffered.put("tick",ticks.size());ticks.add(buffered);}
            preCombat.clear(); idle = 0;
            outsideEncounterTicks=0;playerDeathPending=false;
            positions.reset(); projectilePositions.reset(); effectLifecycle.reset();
            encounter = object("schemaVersion", 1, "id", UUID.randomUUID().toString(), "startedAt", Instant.now().toString(),
                "recorder", object("playerName", player.getName()),
                "tickDurationMs", 600, "label", encounterBoss != null ? encounterBoss.getName() : player.getInteracting() instanceof NPC ? player.getInteracting().getName() : "PvM encounter", "ticks", ticks,
                "combatStartTick",ticks.size(), "preCombatTicks",ticks.size(),
                "timingBasis", "first_observed_damage_tick", "captureFeatures", Arrays.asList("attack_cues_v1", "shared_buffs_v1", "boss_events_v1", "item_use_v1", "player_spells_v1", "positions_v1", "glyph_positions_v1", "ground_hazards_v1", "projectile_paths_v1", "damage_cues_v1", "effect_lifecycle_v1", "actor_visual_snapshots_v1", "hazard_observations_at_hit_v1"));
            if(!ticks.isEmpty())encounter.put("startedAt",ticks.get(0).get("observedAt"));
            notifyChat("Recording started.");
        }
        if (encounter != null)
        {
            if(encounterBoss!=null && encounterBoss.getId()==YAMA_ID) {
                if(client.getPlayers()!=null)for(Player nearby:client.getPlayers())if(nearby.getName()!=null && nearby.getWorldLocation()!=null && encounterBoss.getWorldLocation()!=null && nearby.getWorldLocation().distanceTo(encounterBoss.getWorldLocation())<=32)observedParticipants.add(nearby.getName());
                encounter.put("rankingCapture",object("version",1,"observedParticipants",new ArrayList<>(observedParticipants),"teamEvidence","nearby_players_only"));
            }
            if(config.combinedCapture() && research!=null && !encounter.containsKey("researchSessionId")) {
                if(research.activeSessionId()==null)research.beginEncounter(String.valueOf(encounter.get("label")));
                String sessionId=research.activeSessionId();
                if(sessionId!=null) {
                    research.retainForEncounter();
                    encounter.put("researchSessionId",sessionId);
                    encounter.put("researchFolder",research.activeFolder());
                    encounter.put("combinedCaptureFromTick",ticks.size());
                }
            }
            BossProfile profile = profile();
            if (profile != null) {
                encounter.put("boss", object("id", profile.id(), "name", profile.name(), "npcId", encounterBoss.getId(), "profileVersion", 1));
                encounter.put("label", profile.name());
            }
            ticks.add(snapshot(player,hp,spellState,profile,ticks.size()));
            displayedRecordedTick = ticks.size() - 1;
            tickRecording = true;
            Boolean inArea = encounterArea(player,profile);
            if(Boolean.FALSE.equals(inArea))outsideEncounterTicks++;
            else if(Boolean.TRUE.equals(inArea))outsideEncounterTicks=0;
            idle = combatEvent || engaged || Boolean.TRUE.equals(inArea) ? 0 : idle + 1;
            if (playerDeathPending || hp <= 0) finish("player_death");
            else if (bossDeathPending)
            {
                encounter.put("endTick", ticks.size() - 1);
                encounter.put("endEvidence", deathEvidence);
                finish("boss_death");
                awaitingNextFight = true;
            }
            else if (outsideEncounterTicks>=3) finish("left_encounter");
            else if (idle >= config.idleTicks() && !Boolean.FALSE.equals(inArea)) finish("idle_timeout");
            else if (ticks.size() >= 2000) finish("length_limit");
        }
        else {
            capturePreCombat(player,hp,spellState);
        }
        previousHp = hp; pending.clear(); sequence = 0;
        if (encounter == null && !awaitingNextFight) { encounterBoss = null; bossDeathPending = false; }
    }


    private void capturePreCombat(Player player,int hp,Map<String,Object> spellState) {
            Map<String,Object> buffered=snapshot(player,hp,spellState,null,0);
            // Pre-roll is player state and actions; arena identities begin with the fight.
            buffered.remove("spatial");buffered.remove("groundHazards");buffered.remove("projectilePaths");buffered.remove("effectLifecycle");
            preCombat.addLast(buffered);while(preCombat.size()>15)preCombat.removeFirst();
    }

    private Map<String,Object> snapshot(Player player,int hp,Map<String,Object> spellState,BossProfile profile,int tickIndex) {
            int damage = pending.stream().filter(e -> "damage_taken".equals(e.get("kind"))).mapToInt(e -> (Integer)e.get("amount")).sum();
            List<String> prayers = new ArrayList<>();
            for (Prayer prayer : Prayer.values()) if (client.isPrayerActive(prayer)) prayers.add(prayer.name());
            Map<String, Object> skills = new LinkedHashMap<>();
            for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) skills.put(skill.name(), object("base", client.getRealSkillLevel(skill), "boosted", client.getBoostedSkillLevel(skill)));
            effectLifecycle.scan(client,player);
            return object("tick", tickIndex, "clientTick", client.getTickCount(), "observedAt", Instant.now().toString(),
                "effectLifecycle",effectLifecycle.drain(), "hp", hp, "projectilePaths",projectilePositions.snapshot(client,player,profile), "observedHazardProtection",hazards.protections(client,player,profile), "groundHazards",hazards.snapshot(client,player,profile),"spatial",positions.snapshot(client,player,profile),"spellState",spellState,"maxHp", client.getRealSkillLevel(Skill.HITPOINTS), "prayer", client.getBoostedSkillLevel(Skill.PRAYER),
                "runEnergy", client.getEnergy(), "specialAttack", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT),
                "animationId", player.getAnimation(), "position", object("x", player.getWorldLocation().getX(), "y", player.getWorldLocation().getY(), "plane", player.getWorldLocation().getPlane()),
                "target", actor(player.getInteracting()), "equipment", items(InventoryID.EQUIPMENT), "inventory", items(InventoryID.INVENTORY),
                "buffState", SharedBuffs.snapshot(client), "skills", skills, "prayers", prayers, "healingEstimate", CombatMath.healingEstimate(previousHp, hp, damage), "events", new ArrayList<>(pending));
    }

    private void finish(String reason)
    {
        if (encounter == null) return;
        if(reason.equals("player_death")) {spells.reset();previousSpellState=null;vengeanceTarget=null;targetVengeanceConfirmed=false;}
        encounter.put("endReason", reason); encounter.put("endedAt", Instant.now().toString());
        // Events since the last complete GameTick are retained separately, never assigned a fabricated tick.
        if (!pending.isEmpty() && !reason.equals("idle_timeout") && !reason.equals("player_death") && !reason.equals("length_limit") && !reason.equals("boss_death"))
            encounter.put("trailingEvents", new ArrayList<>(pending));
        Map<String, Object> completed = encounter; encounter = null; ticks = null;
        tickRecording = false;
        completed.put("trailingEffectLifecycle", effectLifecycle.drain());
        effectLifecycle.reset();
        bossDeathPending = false;
        consumables.clear(); seenProjectiles.clear(); seenGraphics.clear();
        if (!reason.equals("boss_death")) encounterBoss = null;
        notifyChat("Recording stopped (" + reason.replace('_', ' ') + "). Saving "
            + ((List<?>) completed.get("ticks")).size() + " ticks...");
        if(reason.equals("boss_death") && !completed.containsKey("officialTiming") && completed.get("boss") instanceof Map && "yama".equals(((Map<?,?>)completed.get("boss")).get("id"))) {
            flushTiming();awaitingTiming=completed;timingDeadline=client.getTickCount()+15;
        } else saveEncounter(completed);
        if(awaitingTiming!=completed && completed.containsKey("researchSessionId")&&research!=null)research.releaseEncounter();
    }

    void saveEncounter(Map<String, Object> completed)
    {
        try
        {
            writer.execute(() -> {
                try
                {
                    Path directory = encounterDirectory(RuneLite.RUNELITE_DIR.toPath().resolve("encounter-ledger"),completed);
                    Files.createDirectories(directory);
                    Path temporary = directory.resolve(completed.get("id") + ".tmp");
                    Path destination = directory.resolve(completed.get("id") + ".json");
                    Files.write(temporary, gson.toJson(completed).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                    try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination); }
                    notifyChat("Log saved: " + (completed.containsKey("researchSessionId") ? "research/"+completed.getOrDefault("researchFolder",completed.get("researchSessionId"))+"/" : "") + destination.getFileName() + ". Ready to import into OSRS Glass.");
                }
                catch (Exception ex) { storageFailed = true; LOG.error("Glass Combat Logger could not save a log; recording paused until plugin restart", ex); notifyChat("Could not save the log. Recording paused; check your RuneLite log and restart the plugin after fixing storage."); }
            });
        }
        catch (RejectedExecutionException ex) { storageFailed = true; LOG.error("Glass Combat Logger save queue full; recording paused", ex); notifyChat("Save queue full. This log could not be saved; recording paused."); }
    }

    static Path encounterDirectory(Path root,Map<String,Object> completed) {
        Object session=completed.get("researchSessionId");
        return session instanceof String ? root.resolve("research").resolve(RecordingFolder.validate(String.valueOf(completed.getOrDefault("researchFolder",session)),(String)session)) : root;
    }

    private void notifyChat(String message)
    {
        // Save callbacks run off-thread; all client access must return to the client thread.
        Runnable notify = () -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Glass Combat Logger] " + message, null);
        if (client.isClientThread()) notify.run();
        else clientThread.invokeLater(notify);
    }
}

