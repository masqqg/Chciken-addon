package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Sus Chunk Finder V2.
 *
 * Ported to Meteor from the Anubis "Sus Chunk Finder" and "Chunk Scanner" modules:
 *
 *  1. Amethyst finder  - fully grown amethyst (clusters / buds) is a sign that a chunk was
 *     simulated for a long time, i.e. a player was around. Two methods: Classic (block states +
 *     hidden light sources) and ANBS+ (light-data only, works when the amethyst blocks are hidden).
 *     Heat is spread over the simulation distance, chunks next to un-grown buds are vetoed, and only
 *     the hottest chunk of each area is marked.
 *  2. Mined clusters   - large geodes that have been dug out (no growth light left around them).
 *  3. Stone scanner    - vertical stone stacks that cut through granite/diorite/andesite/gravel
 *     blobs, i.e. holes that were patched up with stone.
 *
 * The seed based "Advanced Scanner" and the anti-xray probe of the original are not included.
 */
public class SusChunkFinderV2 extends Module {
    public enum Method { Classic, ANBS }

    // ---- amethyst constants
    private static final int MIN_SCANNED_NEIGHBOURS = 3;
    private static final int NO_PACKET_SENSITIVITY = 1;
    private static final int VISIBLE_REFRESH_TICKS = 20;
    private static final int FULL_RESCAN_TICKS = 1200;
    private static final int VISIBLE_MIN_TICKS = 5;
    private static final int LIGHT_WAIT_LIMIT_TICKS = 200;
    private static final int PACKET_MIN_Y = -16;
    private static final int PACKET_MAX_Y = 30;
    private static final int GLOW_CELLS_PER_POINT = 12;
    private static final int GEODE_TOP_Y = 47;
    private static final int MIN_EXPOSED_BUDDING = 10;
    private static final int MIN_EXPOSED_LINING = 160;
    private static final int GEODE_SETTLE_TICKS = 40;
    private static final int GEODE_CELL_SHIFT = 2;
    private static final int TORCH_LIGHT = 14;
    private static final Direction[] SIDES = Direction.values();

    // ---- stone scanner constants
    private static final byte OTHER = 0;
    private static final byte STONE = 1;
    private static final byte BLOB = 2;
    private static final byte AIR = 3;
    private static final byte MISSING = 4;
    private static final int[] SIDE_X = {1, -1, 0, 0};
    private static final int[] SIDE_Z = {0, 0, 1, -1};
    private static final int[] CORNER_X = {1, 1, -1, -1};
    private static final int[] CORNER_Z = {1, -1, 1, -1};
    private static final int STONE_MIN_Y = 0;
    private static final int STONE_MAX_Y = 32;
    private static final int STONE_RETRY_TICKS = 100;
    private static final Find UNDECIDED = new Find(0L, 0, new long[0]);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAmethyst = settings.createGroup("Amethyst");
    private final SettingGroup sgStone = settings.createGroup("Stone Scanner");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // general
    private final Setting<Integer> scanBudgetMs = sgGeneral.add(new IntSetting.Builder()
        .name("scan-budget-ms").description("Scan time budget per tick in milliseconds.")
        .defaultValue(2).range(1, 20).sliderRange(1, 10).build());

    // amethyst
    private final Setting<Boolean> amethyst = sgAmethyst.add(new BoolSetting.Builder()
        .name("amethyst-finder").description("Find chunks by fully grown amethyst.")
        .defaultValue(true).onChanged(v -> rescan()).build());

    private final Setting<Method> method = sgAmethyst.add(new EnumSetting.Builder<Method>()
        .name("method").description("Classic = block states + hidden light sources. ANBS = light data only.")
        .defaultValue(Method.Classic).onChanged(v -> rescan()).build());

    private final Setting<Integer> spreadRadius = sgAmethyst.add(new IntSetting.Builder()
        .name("simulation-distance").description("Radius in chunks the grown amethyst of a chunk is spread over.")
        .defaultValue(5).range(2, 16).sliderRange(2, 16).build());

    private final Setting<Integer> sensitivity = sgAmethyst.add(new IntSetting.Builder()
        .name("sensitivity").description("Heat a chunk needs before it is marked.")
        .defaultValue(6).range(1, 20).sliderRange(1, 20).onChanged(v -> visibleDirty = true).build());

    private final Setting<Boolean> detectMined = sgAmethyst.add(new BoolSetting.Builder()
        .name("detect-mined-clusters").description("Also mark geodes whose amethyst has been mined out.")
        .defaultValue(false).onChanged(v -> restartGeodes()).build());

    // stone scanner
    private final Setting<Boolean> stoneScanner = sgStone.add(new BoolSetting.Builder()
        .name("stone-scanner").description("Find stone stacks that cut through ore blobs.")
        .defaultValue(true).onChanged(v -> rescan()).build());

    private final Setting<Integer> stackHeight = sgStone.add(new IntSetting.Builder()
        .name("stack-sensitivity").description("Minimum height of a stone stack.")
        .defaultValue(2).range(2, 5).sliderRange(2, 5).build());

    private final Setting<Integer> stoneSpread = sgStone.add(new IntSetting.Builder()
        .name("stone-spread-radius").description("Radius in chunks a stack is spread over.")
        .defaultValue(1).range(0, 16).sliderRange(0, 16).build());

    // render
    private final Setting<SettingColor> chunkColor = sgRender.add(new ColorSetting.Builder()
        .name("amethyst-color").description("Fill color of amethyst chunks.")
        .defaultValue(new SettingColor(255, 4, 4, 96)).build());

    private final Setting<SettingColor> minedColor = sgRender.add(new ColorSetting.Builder()
        .name("mined-color").description("Fill color of mined geodes.")
        .defaultValue(new SettingColor(166, 77, 255, 96)).build());

    private final Setting<SettingColor> stoneColor = sgRender.add(new ColorSetting.Builder()
        .name("stone-color").description("Fill color of stone stack chunks.")
        .defaultValue(new SettingColor(255, 255, 255, 96)).build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").description("Draw a line to every marked area.")
        .defaultValue(true).build());

    private final Setting<Integer> renderY = sgRender.add(new IntSetting.Builder()
        .name("render-y").description("Y level of the bottom of the marker.")
        .defaultValue(63).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
        .name("render-height").description("Height of the marker in blocks.")
        .defaultValue(6).range(1, 64).sliderRange(1, 32).build());

    // ---- amethyst state
    private final GeodeGlowScan glow = new GeodeGlowScan();
    private final Long2IntOpenHashMap heat = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap vetoes = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap chunkScore = new Long2IntOpenHashMap();
    private final Long2BooleanOpenHashMap chunkUngrown = new Long2BooleanOpenHashMap();
    private final Long2BooleanOpenHashMap chunkHasPacket = new Long2BooleanOpenHashMap();
    private final LongOpenHashSet scannedChunks = new LongOpenHashSet();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private final LongOpenHashSet lightDirty = new LongOpenHashSet();
    private final Long2IntOpenHashMap waitingSince = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap bestLightClusters = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap bestLightBuds = new Long2IntOpenHashMap();
    private final LongOpenHashSet geodeChunks = new LongOpenHashSet();
    private Long2IntOpenHashMap visible = new Long2IntOpenHashMap();
    private boolean visibleDirty = true;
    private int visibleAge;
    private int appliedRadius;
    private final Long2ObjectOpenHashMap<GeodeSlice> geodeSlices = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap geodeWaiting = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap geodeScanTicks = new Long2IntOpenHashMap();
    private List<GeodeCluster> geodeClusters = List.of();
    private boolean geodeClustersDirty;
    private LongOpenHashSet minedChunks = new LongOpenHashSet();
    private int minedAge;

    // ---- stone scanner state
    private final Long2ObjectOpenHashMap<Find> flagged = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue stoneQueue = new LongArrayFIFOQueue();
    private final LongOpenHashSet stoneQueued = new LongOpenHashSet();
    private final LongOpenHashSet stoneDone = new LongOpenHashSet();
    private final LongOpenHashSet stoneUndecided = new LongOpenHashSet();
    private final Long2IntOpenHashMap stoneHeat = new Long2IntOpenHashMap();
    private int appliedStoneRadius;
    private int appliedStoneHeight;
    private LongOpenHashSet stoneShown = new LongOpenHashSet();
    private boolean stoneShownDirty = true;
    private final LevelChunk[] around = new LevelChunk[9];
    private final int[] groupX = new int[4];
    private final int[] groupZ = new int[4];
    private int groupSize;
    private boolean guessed;
    private Block wall;
    private Block lastBlock;
    private final int[] stackTop = new int[256];
    private final int[] stackShape = new int[256];
    private final int[] stackAreas = new int[256];
    private final boolean[] stackGuessed = new boolean[256];
    private final Block[] stackWall = new Block[256];
    private int scanX;
    private int scanZ;
    private int keepHeight;
    private long[] keptStacks = new long[16];
    private int keptStackCount;
    private int tallest;
    private long bottom;
    private boolean unsure;

    // ---- tracer centers (cached per chunk set)
    private final AreaCenters visibleCenters = new AreaCenters();
    private final AreaCenters minedCenters = new AreaCenters();
    private final AreaCenters stoneCenters = new AreaCenters();

    private ClientLevel lastLevel;
    private int ticks;

    public SusChunkFinderV2() {
        super(AddonTemplate.CATEGORY, "sus-chunk-finder-v2",
            "Finds chunks with signs of player activity: grown amethyst, mined geodes and patched stone stacks.");
    }

    // =================================================================== lifecycle

    private void rescan() {
        if (!isActive()) return;
        clearAll();
        queueLoadedChunks();
    }

    private void restartGeodes() {
        if (!isActive()) return;
        clearGeodes();
        if (detectMined.get()) queueLoadedChunks();
    }

    @Override
    public void onActivate() {
        clearAll();
        lastLevel = mc.level;
        queueLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        clearAll();
        lastLevel = null;
    }

    private void clearAll() {
        heat.clear();
        vetoes.clear();
        chunkScore.clear();
        chunkUngrown.clear();
        chunkHasPacket.clear();
        scannedChunks.clear();
        queue.clear();
        queued.clear();
        lightDirty.clear();
        waitingSince.clear();
        bestLightClusters.clear();
        bestLightBuds.clear();
        geodeChunks.clear();
        visible = new Long2IntOpenHashMap();
        visibleDirty = true;
        appliedRadius = spreadRadius.get();
        clearGeodes();

        flagged.clear();
        stoneQueue.clear();
        stoneQueued.clear();
        stoneDone.clear();
        stoneUndecided.clear();
        stoneHeat.clear();
        appliedStoneRadius = stoneSpread.get();
        appliedStoneHeight = stackHeight.get();
        stoneShown = new LongOpenHashSet();
        stoneShownDirty = true;
    }

    private void clearGeodes() {
        geodeSlices.clear();
        geodeWaiting.clear();
        geodeScanTicks.clear();
        geodeClusters = List.of();
        geodeClustersDirty = false;
        minedChunks = new LongOpenHashSet();
        minedAge = 0;
    }

    private static boolean loaded(ClientLevel level, int cx, int cz) {
        return level.hasChunk(cx, cz);
    }

    private static boolean isOverworld(ClientLevel level) {
        return level.dimension() == Level.OVERWORLD;
    }

    private boolean stoneDimension(ClientLevel level) {
        return level.dimension() != Level.NETHER && level.dimension() != Level.END;
    }

    /** Queue every loaded chunk around the player that has not been scanned yet. */
    private void queueLoadedChunks() {
        if (mc.level == null || mc.player == null) return;

        int radius = mc.options.renderDistance().get() + 1;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;

        for (int x = pcx - radius; x <= pcx + radius; x++) {
            for (int z = pcz - radius; z <= pcz + radius; z++) {
                if (!loaded(mc.level, x, z)) continue;
                long key = ChunkKey.of(x, z);
                if (amethyst.get()) enqueue(key);
                if (stoneScanner.get() && !stoneDone.contains(key)) stoneEnqueue(key);
            }
        }
    }

    /** Only chunks we have not seen yet - cheap enough to run every few ticks. */
    private void queueNewChunks() {
        if (mc.level == null || mc.player == null) return;

        int radius = mc.options.renderDistance().get() + 1;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;

        for (int x = pcx - radius; x <= pcx + radius; x++) {
            for (int z = pcz - radius; z <= pcz + radius; z++) {
                if (!loaded(mc.level, x, z)) continue;
                long key = ChunkKey.of(x, z);
                if (amethyst.get() && !scannedChunks.contains(key)) enqueue(key);
                if (stoneScanner.get() && !stoneDone.contains(key) && !stoneUndecided.contains(key)) {
                    stoneEnqueue(key);
                    // a new chunk changes what its neighbours can see
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx != 0 || dz != 0) {
                                long near = ChunkKey.of(x + dx, z + dz);
                                if (stoneUndecided.remove(near)) stoneEnqueue(near);
                            }
                        }
                    }
                }
            }
        }
    }

    private void enqueue(long key) {
        if (queued.add(key)) queue.enqueue(key);
    }

    private void stoneEnqueue(long key) {
        if (stoneQueued.add(key)) stoneQueue.enqueue(key);
    }

    // =================================================================== events

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        // network thread: hop onto the client thread before touching our state
        if (event.packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            int x = chunk.getX();
            int z = chunk.getZ();
            mc.execute(() -> markLightDirty(x, z));
        } else if (event.packet instanceof ClientboundLightUpdatePacket light) {
            int x = light.getX();
            int z = light.getZ();
            mc.execute(() -> markLightDirty(x, z));
        }
    }

    private void markLightDirty(int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = ChunkKey.of(chunkX + dx, chunkZ + dz);
                if (geodeChunks.contains(key) || !scannedChunks.contains(key)) lightDirty.add(key);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!isActive() || !stoneScanner.get() || mc.level == null || event.pos == null) return;
        if (!stoneDimension(mc.level)) return;

        int y = event.pos.getY();
        if (y < STONE_MIN_Y - 1 || y > STONE_MAX_Y + 1) return;
        if (!stoneMatters(event.oldState) && !stoneMatters(event.newState)) return;

        int x = event.pos.getX();
        int z = event.pos.getZ();
        for (int cx = (x - 2) >> 4; cx <= (x + 1) >> 4; cx++) {
            for (int cz = (z - 2) >> 4; cz <= (z + 2) >> 4; cz++) {
                stoneEnqueue(ChunkKey.of(cx, cz));
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        if (level != lastLevel) {
            clearAll();
            lastLevel = level;
            queueLoadedChunks();
        }

        ticks++;

        if (spreadRadius.get() != appliedRadius) {
            clearAll();
            queueLoadedChunks();
        }

        if (stoneSpread.get() != appliedStoneRadius || stackHeight.get() != appliedStoneHeight) {
            resetStone();
        }

        if (ticks % FULL_RESCAN_TICKS == 0) {
            lightDirty.clear();
            queueLoadedChunks();
        } else if (ticks % 20 == 0) {
            if (!lightDirty.isEmpty()) {
                LongIterator it = lightDirty.iterator();
                while (it.hasNext()) {
                    long key = it.nextLong();
                    if (loaded(level, ChunkKey.x(key), ChunkKey.z(key))) enqueue(key);
                }
                lightDirty.clear();
            }
            queueNewChunks();
        } else if (ticks % 10 == 0) {
            queueNewChunks();
        }

        if (ticks % STONE_RETRY_TICKS == 0 && !stoneUndecided.isEmpty()) {
            LongIterator it = stoneUndecided.iterator();
            while (it.hasNext()) stoneEnqueue(it.nextLong());
            stoneUndecided.clear();
        }

        long deadline = System.nanoTime() + scanBudgetMs.get() * 1_000_000L;

        // ---- amethyst queue
        while (amethyst.get() && !queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.dequeueLong();
            queued.remove(key);
            int cx = ChunkKey.x(key);
            int cz = ChunkKey.z(key);

            if (!loaded(level, cx, cz)) {
                waitingSince.remove(key);
            } else if (!lightSettled(level, key)) {
                lightDirty.add(key);
            } else {
                waitingSince.remove(key);
                updateChunk(key, level.getChunk(cx, cz), level);
                scannedChunks.add(key);
                visibleDirty = true;
            }
        }

        // ---- stone queue
        if (stoneScanner.get() && stoneDimension(level)) {
            ScanConfig config = new ScanConfig(Math.max(STONE_MIN_Y, level.getMinY()), STONE_MAX_Y, stackHeight.get());
            while (!stoneQueue.isEmpty() && System.nanoTime() < deadline) {
                long key = stoneQueue.dequeueLong();
                stoneQueued.remove(key);
                int cx = ChunkKey.x(key);
                int cz = ChunkKey.z(key);
                if (!loaded(level, cx, cz)) continue;

                Find find = scanStone(level, cx, cz, config);
                if (find == UNDECIDED) {
                    stoneUndecided.add(key);
                    continue;
                }
                stoneUndecided.remove(key);
                stoneDone.add(key);

                Find was = flagged.get(key);
                if (find == null) {
                    if (was == null) continue;
                } else if (was != null && was.sameAs(find)) {
                    continue;
                }
                refile(key, was, find);
            }
            if (stoneShownDirty) {
                stoneShown = computeStoneShown(level);
                stoneShownDirty = false;
            }
        }

        visibleAge++;
        if ((visibleDirty && visibleAge >= VISIBLE_MIN_TICKS) || visibleAge >= VISIBLE_REFRESH_TICKS) {
            visible = amethyst.get() ? computeVisible(level) : new Long2IntOpenHashMap();
            visibleDirty = false;
            visibleAge = 0;
        }

        if (amethyst.get() && detectMined.get() && ++minedAge >= 20) {
            minedChunks = computeMined(level);
            minedAge = 0;
        }
    }

    private void resetStone() {
        flagged.clear();
        stoneQueue.clear();
        stoneQueued.clear();
        stoneDone.clear();
        stoneUndecided.clear();
        stoneHeat.clear();
        appliedStoneRadius = stoneSpread.get();
        appliedStoneHeight = stackHeight.get();
        stoneShown = new LongOpenHashSet();
        stoneShownDirty = true;
        queueLoadedChunks();
    }

    // =================================================================== amethyst finder

    private boolean lightSettled(ClientLevel level, long key) {
        if (!waitingSince.containsKey(key)) waitingSince.put(key, ticks);
        if (ticks - waitingSince.get(key) >= LIGHT_WAIT_LIMIT_TICKS) return true;

        LevelLightEngine engine = level.getLightEngine();
        if (engine.hasLightWork()) return false;

        int cx = ChunkKey.x(key);
        int cz = ChunkKey.z(key);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!loaded(level, cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    private void updateChunk(long key, LevelChunk chunk, ClientLevel level) {
        int r = spreadRadius.get();
        int cx = ChunkKey.x(key);
        int cz = ChunkKey.z(key);
        int oldHeat = chunkScore.getOrDefault(key, 0);
        boolean oldUngrown = chunkUngrown.getOrDefault(key, false);

        if (oldHeat > 0) forArea(cx, cz, r, area -> subtract(heat, area, oldHeat));
        if (oldUngrown) forArea(cx, cz, r, area -> subtract(vetoes, area, 1));

        chunkHasPacket.put(key, hasBlocksInRange(chunk, PACKET_MIN_Y, PACKET_MAX_Y));
        if (hasLining(chunk)) geodeChunks.add(key);
        else geodeChunks.remove(key);

        ChunkScore raw = scoreChunk(chunk, level);
        int bestClusters = bestLightClusters.get(key);
        int bestBuds = bestLightBuds.get(key);
        if (raw.lightClusters() > bestClusters
            || (raw.lightClusters() == bestClusters && raw.lightBuds() > bestBuds)) {
            bestClusters = raw.lightClusters();
            bestBuds = raw.lightBuds();
            bestLightClusters.put(key, bestClusters);
            bestLightBuds.put(key, bestBuds);
        }

        ChunkScore score = new ChunkScore(raw.selfHeat() + bestClusters,
            raw.hasUngrown() || bestBuds > 0, raw.lightClusters(), raw.lightBuds());
        chunkScore.put(key, score.selfHeat());
        chunkUngrown.put(key, score.hasUngrown());

        if (score.hasUngrown()) forArea(cx, cz, r, area -> vetoes.addTo(area, 1));
        if (score.selfHeat() > 0) forArea(cx, cz, r, area -> heat.addTo(area, score.selfHeat()));

        if (detectMined.get()) collectGeode(key, chunk, level);
    }

    private static void forArea(int cx, int cz, int r, LongConsumer action) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                action.accept(ChunkKey.of(cx + dx, cz + dz));
            }
        }
    }

    private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
        int left = map.get(key) - amount;
        if (left > 0) map.put(key, left);
        else map.remove(key);
    }

    private static boolean hasLining(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section != null && !section.hasOnlyAir() && section.maybeHas(SusChunkFinderV2::isLining)) return true;
        }
        return false;
    }

    private static boolean hasBlocksInRange(LevelChunk chunk, int minY, int maxY) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            int baseY = (minSectionY + i) << 4;
            if (baseY + 15 < minY || baseY > maxY) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = baseY + y;
                if (worldY < minY || worldY > maxY) continue;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!section.getBlockState(x, y, z).isAir()) return true;
                    }
                }
            }
        }
        return false;
    }

    private ChunkScore scoreChunk(LevelChunk chunk, ClientLevel level) {
        if (method.get() == Method.ANBS) {
            return new ChunkScore(glow.scan(level, chunk) / GLOW_CELLS_PER_POINT, false, 0, 0);
        }

        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int grown = 0;
        int ungrown = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(SusChunkFinderV2::isAmethystGrowth)) continue;

            int baseY = (minSectionY + i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.is(Blocks.AMETHYST_CLUSTER)) {
                            grown++;
                        } else if (isBud(state) && state.hasProperty(BlockStateProperties.FACING)) {
                            Direction facing = state.getValue(BlockStateProperties.FACING);
                            cursor.set(minX + x, baseY + y, minZ + z).move(facing.getOpposite());
                            if (level.getBlockState(cursor).is(Blocks.BUDDING_AMETHYST)) ungrown++;
                            else grown++;
                        }
                    }
                }
            }
        }

        // hidden light sources: amethyst that is not in the block data but shows in the light
        int[] hidden = new int[2];
        AmethystLight.scanSources(level, chunk, (packed, light) -> hidden[light == 5 ? 0 : 1]++);
        return new ChunkScore(grown, ungrown > 0, hidden[0], hidden[1]);
    }

    // ------------------------------------------------------------------ mined geodes

    private void collectGeode(long key, LevelChunk chunk, ClientLevel level) {
        geodeScanTicks.put(key, ticks);
        if (geodeSlices.remove(key) != null) geodeClustersDirty = true;
        geodeWaiting.remove(key);

        int cx = ChunkKey.x(key);
        int cz = ChunkKey.z(key);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long near = ChunkKey.of(cx + dx, cz + dz);
                if ((dx != 0 || dz != 0) && (geodeWaiting.get(near) & waitBit(-dx, -dz)) != 0) enqueue(near);
            }
        }

        if (!isOverworld(level)) return;

        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        int minX = cx << 4;
        int minZ = cz << 4;
        LongArrayList lining = new LongArrayList();
        LongArrayList budding = new LongArrayList();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int waiting = 0;

        for (int i = 0; i < sections.length; i++) {
            int baseY = (minSectionY + i) << 4;
            if (baseY > GEODE_TOP_Y) break;

            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(SusChunkFinderV2::isLining)) continue;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isLining(state)) continue;

                        int wx = minX + x;
                        int wy = baseY + y;
                        int wz = minZ + z;
                        boolean exposed = false;
                        int missing = 0;

                        for (Direction side : SIDES) {
                            int nx = wx + side.getStepX();
                            int ny = wy + side.getStepY();
                            int nz = wz + side.getStepZ();
                            if (level.isOutsideBuildHeight(ny)) continue;

                            int ncx = nx >> 4;
                            int ncz = nz >> 4;
                            boolean inside = ncx == cx && ncz == cz;
                            if (!inside && !loaded(level, ncx, ncz)) {
                                missing |= waitBit(ncx - cx, ncz - cz);
                            } else {
                                cursor.set(nx, ny, nz);
                                if (isOpen(inside ? chunk.getBlockState(cursor) : level.getBlockState(cursor))) {
                                    exposed = true;
                                    break;
                                }
                            }
                        }

                        if (!exposed) {
                            waiting |= missing;
                        } else {
                            long packed = BlockPos.asLong(wx, wy, wz);
                            lining.add(packed);
                            if (state.is(Blocks.BUDDING_AMETHYST)) budding.add(packed);
                        }
                    }
                }
            }
        }

        if (waiting != 0) geodeWaiting.put(key, waiting);
        if (!lining.isEmpty()) {
            geodeSlices.put(key, new GeodeSlice(lining.toLongArray(), budding.toLongArray()));
            geodeClustersDirty = true;
        }
    }

    private static int waitBit(int dx, int dz) {
        return 1 << ((dx + 1) * 3 + dz + 1);
    }

    private LongOpenHashSet computeMined(ClientLevel level) {
        if (geodeSlices.keySet().removeIf(key -> !loaded(level, ChunkKey.x(key), ChunkKey.z(key)))) {
            geodeClustersDirty = true;
        }
        geodeWaiting.keySet().removeIf(key -> !loaded(level, ChunkKey.x(key), ChunkKey.z(key)));
        geodeScanTicks.keySet().removeIf(key -> !loaded(level, ChunkKey.x(key), ChunkKey.z(key)));

        if (geodeClustersDirty) {
            geodeClusters = clusterGeodes();
            geodeClustersDirty = false;
        }

        LongOpenHashSet mined = new LongOpenHashSet();
        for (GeodeCluster cluster : geodeClusters) {
            boolean large = cluster.budding() > 0
                ? cluster.budding() >= MIN_EXPOSED_BUDDING
                : cluster.lining().length >= MIN_EXPOSED_LINING;
            if (large && settled(level, cluster) && unlit(level, cluster.lining())) mined.add(cluster.chunk());
        }
        return mined;
    }

    private List<GeodeCluster> clusterGeodes() {
        Long2ObjectOpenHashMap<LongArrayList> cells = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet budding = new LongOpenHashSet();

        for (GeodeSlice slice : geodeSlices.values()) {
            for (long packed : slice.lining()) {
                long cell = BlockPos.asLong(
                    BlockPos.getX(packed) >> GEODE_CELL_SHIFT,
                    BlockPos.getY(packed) >> GEODE_CELL_SHIFT,
                    BlockPos.getZ(packed) >> GEODE_CELL_SHIFT);
                LongArrayList members = cells.get(cell);
                if (members == null) cells.put(cell, members = new LongArrayList());
                members.add(packed);
            }
            for (long packed : slice.budding()) budding.add(packed);
        }

        List<GeodeCluster> clusters = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();

        LongIterator starts = cells.keySet().iterator();
        while (starts.hasNext()) {
            long start = starts.nextLong();
            if (!seen.add(start)) continue;

            LongArrayList lining = new LongArrayList();
            frontier.enqueue(start);

            while (!frontier.isEmpty()) {
                long cell = frontier.dequeueLong();
                lining.addAll((LongList) cells.get(cell));
                int x = BlockPos.getX(cell);
                int y = BlockPos.getY(cell);
                int z = BlockPos.getZ(cell);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long next = BlockPos.asLong(x + dx, y + dy, z + dz);
                            if (cells.containsKey(next) && seen.add(next)) frontier.enqueue(next);
                        }
                    }
                }
            }

            clusters.add(toCluster(lining.toLongArray(), budding));
        }
        return clusters;
    }

    private static GeodeCluster toCluster(long[] lining, LongOpenHashSet budding) {
        int count = 0;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        long sumX = 0L;
        long sumZ = 0L;

        for (long packed : lining) {
            if (budding.contains(packed)) count++;
            int x = BlockPos.getX(packed);
            int z = BlockPos.getZ(packed);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
            sumX += x;
            sumZ += z;
        }

        int centerX = (int) Math.floorDiv(sumX, (long) lining.length);
        int centerZ = (int) Math.floorDiv(sumZ, (long) lining.length);
        return new GeodeCluster(lining, count, minX, minZ, maxX, maxZ, ChunkKey.of(centerX >> 4, centerZ >> 4));
    }

    private boolean settled(ClientLevel level, GeodeCluster cluster) {
        for (int cx = (cluster.minX() - 1) >> 4; cx <= (cluster.maxX() + 1) >> 4; cx++) {
            for (int cz = (cluster.minZ() - 1) >> 4; cz <= (cluster.maxZ() + 1) >> 4; cz++) {
                long key = ChunkKey.of(cx, cz);
                if (!loaded(level, cx, cz) || !geodeScanTicks.containsKey(key)) return false;
                if (ticks - geodeScanTicks.get(key) < GEODE_SETTLE_TICKS) return false;
            }
        }
        return true;
    }

    /** True when nothing around the geode still glows like growing amethyst. */
    private static boolean unlit(ClientLevel level, long[] lining) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Long2IntOpenHashMap peaks = new Long2IntOpenHashMap();

        for (long packed : lining) {
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);

            for (Direction side : SIDES) {
                cursor.set(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
                if (isAmethystGrowth(level.getBlockState(cursor))) return false;

                int light = level.getBrightness(LightLayer.BLOCK, cursor);
                if (light > 0 && sourceLight(level, cursor, light, peaks) != TORCH_LIGHT) return false;
            }
        }
        return true;
    }

    /** Walks uphill through the light field to find the brightness of the source feeding this cell. */
    private static int sourceLight(ClientLevel level, BlockPos start, int light, Long2IntOpenHashMap peaks) {
        LongArrayList path = new LongArrayList();
        BlockPos.MutableBlockPos at = start.mutable();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        int peak;
        while (true) {
            long key = at.asLong();
            if (peaks.containsKey(key)) {
                peak = peaks.get(key);
                break;
            }

            path.add(key);
            Direction uphill = null;
            int best = light;

            for (Direction side : SIDES) {
                int value = level.getBrightness(LightLayer.BLOCK, probe.setWithOffset(at, side));
                if (value > best) {
                    best = value;
                    uphill = side;
                }
            }

            if (uphill == null) {
                peak = light;
                break;
            }

            at.move(uphill);
            light = best;
        }

        for (int i = 0; i < path.size(); i++) peaks.put(path.getLong(i), peak);
        return peak;
    }

    // ------------------------------------------------------------------ block helpers

    private static boolean isLining(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }

    /** Neighbour that makes a geode block count as exposed: air, fluid or amethyst growth. */
    private static boolean isOpen(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty() || isAmethystGrowth(state);
    }

    private static boolean isBud(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.SMALL_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD
            || block == Blocks.LARGE_AMETHYST_BUD;
    }

    private static boolean isAmethystGrowth(BlockState state) {
        return state.is(Blocks.AMETHYST_CLUSTER) || isBud(state);
    }

    // ------------------------------------------------------------------ visible set

    private Long2IntOpenHashMap computeVisible(ClientLevel level) {
        Long2IntOpenHashMap eligible = new Long2IntOpenHashMap();

        for (Long2IntMap.Entry entry : heat.long2IntEntrySet()) {
            long key = entry.getLongKey();
            if (vetoes.containsKey(key)) continue;

            int cx = ChunkKey.x(key);
            int cz = ChunkKey.z(key);
            if (loaded(level, cx, cz) && scannedNeighbours(cx, cz) >= MIN_SCANNED_NEIGHBOURS) {
                eligible.put(key, entry.getIntValue());
            }
        }

        int withPacket = sensitivity.get();
        int reach = appliedRadius;
        Long2IntOpenHashMap kept = new Long2IntOpenHashMap();

        for (Long2IntMap.Entry entry : eligible.long2IntEntrySet()) {
            long key = entry.getLongKey();
            int value = entry.getIntValue();
            int threshold = chunkHasPacket.getOrDefault(key, true) ? withPacket : NO_PACKET_SENSITIVITY;
            if (value >= threshold && isPeak(eligible, key, value, reach)) kept.put(key, value);
        }
        return kept;
    }

    private static boolean isPeak(Long2IntOpenHashMap field, long key, int value, int reach) {
        int cx = ChunkKey.x(key);
        int cz = ChunkKey.z(key);

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if ((dx != 0 || dz != 0) && field.get(ChunkKey.of(cx + dx, cz + dz)) > value) return false;
            }
        }
        return true;
    }

    private int scannedNeighbours(int cx, int cz) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && scannedChunks.contains(ChunkKey.of(cx + dx, cz + dz))) count++;
            }
        }
        return count;
    }

    // =================================================================== stone scanner

    private void refile(long key, Find was, Find find) {
        stoneShownDirty = true;
        if (was != null) spread(key, was, -1);

        if (find == null) {
            flagged.remove(key);
        } else {
            flagged.put(key, find);
            spread(key, find, 1);
        }
    }

    private void spread(long key, Find find, int sign) {
        int amount = sign * find.stacks().length;
        int r = appliedStoneRadius;
        int cx = ChunkKey.x(key);
        int cz = ChunkKey.z(key);

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                long area = ChunkKey.of(x, z);
                if (stoneHeat.addTo(area, amount) + amount == 0) stoneHeat.remove(area);
            }
        }
    }

    private LongOpenHashSet computeStoneShown(ClientLevel level) {
        Long2IntOpenHashMap candidates = new Long2IntOpenHashMap();
        for (Long2IntMap.Entry entry : stoneHeat.long2IntEntrySet()) {
            long key = entry.getLongKey();
            if (loaded(level, ChunkKey.x(key), ChunkKey.z(key))) candidates.put(key, entry.getIntValue());
        }
        return appliedStoneRadius == 0 ? new LongOpenHashSet(candidates.keySet()) : hottestPerPatch(candidates);
    }

    /** Keeps only the hottest chunk(s) of every connected patch of candidates. */
    private static LongOpenHashSet hottestPerPatch(Long2IntOpenHashMap candidates) {
        LongOpenHashSet kept = new LongOpenHashSet();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList patch = new LongArrayList();
        LongArrayFIFOQueue open = new LongArrayFIFOQueue();

        LongIterator starts = candidates.keySet().iterator();
        while (starts.hasNext()) {
            long start = starts.nextLong();
            if (!seen.add(start)) continue;

            patch.clear();
            open.enqueue(start);
            int max = 0;

            while (!open.isEmpty()) {
                long key = open.dequeueLong();
                patch.add(key);
                max = Math.max(max, candidates.get(key));
                int cx = ChunkKey.x(key);
                int cz = ChunkKey.z(key);

                for (int side = 0; side < 4; side++) {
                    long next = ChunkKey.of(cx + SIDE_X[side], cz + SIDE_Z[side]);
                    if (candidates.containsKey(next) && seen.add(next)) open.enqueue(next);
                }
            }

            for (int i = 0; i < patch.size(); i++) {
                if (candidates.get(patch.getLong(i)) == max) kept.add(patch.getLong(i));
            }
        }
        return kept;
    }

    /** Returns a Find, null (nothing here) or UNDECIDED (needs neighbours / has no blocks yet). */
    private Find scanStone(ClientLevel level, int chunkX, int chunkZ, ScanConfig config) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                around[(dx + 1) * 3 + dz + 1] = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
            }
        }

        scanX = chunkX << 4;
        scanZ = chunkZ << 4;
        keepHeight = config.stackHeight();
        keptStackCount = 0;
        tallest = 0;
        bottom = 0L;
        unsure = false;

        try {
            LevelChunk chunk = around[4];
            if (chunk == null) return UNDECIDED;

            LevelChunkSection[] sections = chunk.getSections();
            int minSectionY = chunk.getMinSectionY();
            Arrays.fill(stackTop, Integer.MIN_VALUE);
            boolean blank = true;

            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section == null || section.hasOnlyAir()) continue;

                int baseY = (minSectionY + i) << 4;
                if (baseY + 15 < config.minY() || baseY > config.maxY()) continue;

                int fromY = Math.max(config.minY() - baseY, 0);
                int toY = Math.min(config.maxY() - baseY, 15);
                if (blank) blank = !hasBlocks(section, fromY, toY);

                if (!section.maybeHas(st -> st.is(Blocks.STONE)) || !section.maybeHas(st -> isBlob(st.getBlock()))) continue;

                for (int y = fromY; y <= toY; y++) {
                    for (int z = 0; z < 16; z++) {
                        boolean afterStone = false;
                        for (int x = 0; x < 16; x++) {
                            boolean stone = section.getBlockState(x, y, z).getBlock() == Blocks.STONE;
                            if (stone && !afterStone && isArea(x, baseY + y, z)) stack(x, baseY + y, z);
                            afterStone = stone;
                        }
                    }
                }
            }

            for (int i = 0; i < 256; i++) keep(i);

            if (tallest < config.stackHeight()) return (!unsure && !blank) ? null : UNDECIDED;
            return new Find(bottom, tallest, Arrays.copyOf(keptStacks, keptStackCount));
        } finally {
            Arrays.fill(around, null);
        }
    }

    private void stack(int x, int y, int z) {
        int i = z << 4 | x;
        int shape = shape(x, z);
        boolean onTop = stackTop[i] == y - 1 && stackShape[i] == shape && stackWall[i] == wall;
        if (!onTop) keep(i);

        stackTop[i] = y;
        stackShape[i] = shape;
        stackWall[i] = wall;
        stackAreas[i] = onTop ? stackAreas[i] + 1 : 1;
        stackGuessed[i] = guessed || (onTop && stackGuessed[i]);
    }

    private void keep(int i) {
        int height = stackAreas[i];
        int top = stackTop[i];
        if (top == Integer.MIN_VALUE || height < keepHeight) return;

        int shape = stackShape[i];
        if (!goesOn(i & 15, top + 1, i >> 4, shape) || !goesOn(i & 15, top - height, i >> 4, shape)) return;

        if (stackGuessed[i]) {
            unsure = true;
            return;
        }

        int x = scanX + (i & 15);
        int z = scanZ + (i >> 4);
        long lowest = BlockPos.asLong(x, top - height + 1, z);
        if (height > tallest || (height == tallest && before(lowest, bottom))) {
            tallest = height;
            bottom = lowest;
        }

        if (keptStackCount == keptStacks.length) keptStacks = Arrays.copyOf(keptStacks, keptStacks.length * 2);
        keptStacks[keptStackCount++] = lowest;
    }

    private boolean goesOn(int x0, int y, int z0, int shape) {
        for (int bit = 0; bit < 6; bit++) {
            if ((shape >> bit & 1) != 0) {
                kind(x0 + bit / 3, y, z0 + bit % 3 - 1);
                if (!carriesStone(lastBlock)) return false;
            }
        }
        return true;
    }

    private static boolean before(long a, long b) {
        if (BlockPos.getY(a) != BlockPos.getY(b)) return BlockPos.getY(a) < BlockPos.getY(b);
        if (BlockPos.getZ(a) != BlockPos.getZ(b)) return BlockPos.getZ(a) < BlockPos.getZ(b);
        return BlockPos.getX(a) < BlockPos.getX(b);
    }

    private int shape(int x0, int z0) {
        int shape = 0;
        for (int i = 0; i < groupSize; i++) shape |= 1 << (groupX[i] - x0) * 3 + groupZ[i] - z0 + 1;
        return shape;
    }

    /** Grows the 1x1..2x2 stone footprint at this spot; it must be walled in by one kind of blob block. */
    private boolean isArea(int x0, int y, int z0) {
        guessed = false;
        wall = null;

        if (!(seen(x0 - 1, y, z0) == BLOB && sameWall() && seen(x0, y, z0 - 1) == BLOB && sameWall())) return false;

        groupX[0] = x0;
        groupZ[0] = z0;
        int size = 1;
        int maxX = x0;
        int minZ = z0;
        int maxZ = z0;

        for (int i = 0; i < size; i++) {
            for (int side = 0; side < 4; side++) {
                int x = groupX[i] + SIDE_X[side];
                int z = groupZ[i] + SIDE_Z[side];
                if (inGroup(x, z, size)) continue;

                byte kind = seen(x, y, z);
                if (kind == BLOB) {
                    if (!sameWall()) return false;
                } else {
                    if (kind != STONE) return false;
                    if (x < x0 || (x == x0 && z < z0)) return false;

                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                    if (maxX - x0 > 1 || maxZ - minZ > 1) return false;

                    groupX[size] = x;
                    groupZ[size] = z;
                    size++;
                }
            }
        }

        for (int i = 0; i < size; i++) {
            for (int corner = 0; corner < 4; corner++) {
                int x = groupX[i] + CORNER_X[corner];
                int z = groupZ[i] + CORNER_Z[corner];
                if (!inGroup(x, z, size) && (seen(x, y, z) != BLOB || !sameWall())) return false;
            }
        }

        for (int i = 0; i < size; i++) {
            if (kind(groupX[i], y + 1, groupZ[i]) == AIR || kind(groupX[i], y - 1, groupZ[i]) == AIR) return false;
        }

        groupSize = size;
        return true;
    }

    private boolean inGroup(int x, int z, int size) {
        for (int i = 0; i < size; i++) {
            if (groupX[i] == x && groupZ[i] == z) return true;
        }
        return false;
    }

    private byte seen(int x, int y, int z) {
        byte kind = kind(x, y, z);
        if (kind != MISSING) return kind;
        guessed = true; // chunk not loaded: assume it is blob and remember we guessed
        return BLOB;
    }

    private boolean sameWall() {
        if (lastBlock == null) return true;
        if (wall == null) wall = lastBlock;
        return lastBlock == wall;
    }

    private byte kind(int x, int y, int z) {
        LevelChunk chunk = around[((x >> 4) + 1) * 3 + (z >> 4) + 1];
        lastBlock = null;
        if (chunk == null) return MISSING;

        int index = chunk.getSectionIndex(y);
        if (index < 0 || index >= chunk.getSectionsCount()) return OTHER;

        BlockState state = chunk.getSection(index).getBlockState(x & 15, y & 15, z & 15);
        lastBlock = state.getBlock();
        return kindOf(state);
    }

    private static boolean hasBlocks(LevelChunkSection section, int fromY, int toY) {
        if (!section.maybeHas(st -> !st.isAir())) return false;

        for (int y = fromY; y <= toY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!section.getBlockState(x, y, z).isAir()) return true;
                }
            }
        }
        return false;
    }

    private static byte kindOf(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.STONE) return STONE;
        if (isBlob(block)) return BLOB;
        return state.isAir() ? AIR : OTHER;
    }

    /** The natural ore-less blobs a stone shaft shouldn't be able to cut through. */
    private static boolean isBlob(Block block) {
        return block == Blocks.DIORITE || block == Blocks.ANDESITE
            || block == Blocks.GRANITE || block == Blocks.GRAVEL;
    }

    private static boolean carriesStone(Block block) {
        return block == Blocks.STONE || block == Blocks.DEEPSLATE;
    }

    private static boolean stoneMatters(BlockState state) {
        return state != null && (kindOf(state) != OTHER || carriesStone(state.getBlock()));
    }

    // =================================================================== render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null) return;

        if (amethyst.get()) {
            drawChunks(event, visible.keySet(), chunkColor.get());
            if (tracers.get()) drawTracers(event, visibleCenters, visible.keySet(), chunkColor.get());

            if (detectMined.get()) {
                drawChunks(event, minedChunks, minedColor.get());
                if (tracers.get()) drawTracers(event, minedCenters, minedChunks, minedColor.get());
            }
        }

        if (stoneScanner.get()) {
            drawChunks(event, stoneShown, stoneColor.get());
            if (tracers.get()) drawTracers(event, stoneCenters, stoneShown, stoneColor.get());
        }
    }

    /** Filled block over each chunk - sides only, no outline. */
    private void drawChunks(Render3DEvent event, LongSet chunks, SettingColor color) {
        if (chunks.isEmpty()) return;

        double y = renderY.get();
        double h = renderHeight.get();
        LongIterator it = chunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            double x0 = ChunkKey.x(key) * 16.0;
            double z0 = ChunkKey.z(key) * 16.0;
            event.renderer.box(new AABB(x0, y, z0, x0 + 16.0, y + h, z0 + 16.0), color, color, ShapeMode.Sides, 0);
        }
    }

    private void drawTracers(Render3DEvent event, AreaCenters centers, LongSet chunks, Color color) {
        if (chunks.isEmpty()) return;

        List<double[]> list = centers.find(chunks);
        for (double[] c : list) {
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                c[0], renderY.get(), c[1], color);
        }
    }

    // =================================================================== helper types

    private static final class ChunkKey {
        static long of(int x, int z) {
            return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
        }

        static int x(long key) {
            return (int) (key & 0xFFFFFFFFL);
        }

        static int z(long key) {
            return (int) ((key >>> 32) & 0xFFFFFFFFL);
        }
    }

    private record ChunkScore(int selfHeat, boolean hasUngrown, int lightClusters, int lightBuds) {}

    private record GeodeSlice(long[] lining, long[] budding) {}

    private record GeodeCluster(long[] lining, int budding, int minX, int minZ, int maxX, int maxZ, long chunk) {}

    private record ScanConfig(int minY, int maxY, int stackHeight) {}

    private record Find(long pos, int height, long[] stacks) {
        boolean sameAs(Find other) {
            return pos == other.pos && height == other.height && Arrays.equals(stacks, other.stacks);
        }
    }

    /** One tracer target per connected group of marked chunks (the member nearest the group's middle). */
    private static final class AreaCenters {
        private final List<double[]> centers = new ArrayList<>();
        private int size = -1;
        private int hash;

        List<double[]> find(LongSet chunks) {
            int h = chunks.hashCode();
            if (chunks.size() == size && h == hash) return centers;

            size = chunks.size();
            hash = h;
            centers.clear();

            LongOpenHashSet seen = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            LongArrayList area = new LongArrayList();

            LongIterator starts = chunks.iterator();
            while (starts.hasNext()) {
                long start = starts.nextLong();
                if (!seen.add(start)) continue;

                area.clear();
                queue.enqueue(start);
                double sumX = 0.0;
                double sumZ = 0.0;

                while (!queue.isEmpty()) {
                    long key = queue.dequeueLong();
                    area.add(key);
                    int cx = ChunkKey.x(key);
                    int cz = ChunkKey.z(key);
                    sumX += (cx << 4) + 8.0;
                    sumZ += (cz << 4) + 8.0;

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long next = ChunkKey.of(cx + dx, cz + dz);
                            if (chunks.contains(next) && seen.add(next)) queue.enqueue(next);
                        }
                    }
                }

                double mx = sumX / area.size();
                double mz = sumZ / area.size();

                if (!chunks.contains(ChunkKey.of((int) Math.floor(mx) >> 4, (int) Math.floor(mz) >> 4))) {
                    double best = Double.MAX_VALUE;
                    double bx = mx;
                    double bz = mz;
                    for (int i = 0; i < area.size(); i++) {
                        long key = area.getLong(i);
                        double x = (ChunkKey.x(key) << 4) + 8.0;
                        double z = (ChunkKey.z(key) << 4) + 8.0;
                        double d = (x - mx) * (x - mx) + (z - mz) * (z - mz);
                        if (d < best) {
                            best = d;
                            bx = x;
                            bz = z;
                        }
                    }
                    mx = bx;
                    mz = bz;
                }

                centers.add(new double[]{mx, mz});
            }
            return centers;
        }
    }

    // =================================================================== light helpers

    /** Hidden amethyst light sources (reads the raw block-light data, works even if the blocks are hidden). */
    private static final class AmethystLight {
        @FunctionalInterface
        interface Sink {
            void accept(long packed, int light);
        }

        static void scanSources(ClientLevel level, LevelChunk chunk, Sink out) {
            LevelChunkSection[] sections = chunk.getSections();
            int minSectionY = chunk.getMinSectionY();
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            LightCache light = new LightCache(level, minX >> 4, minZ >> 4, minSectionY, sections.length);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section == null || section.hasOnlyAir() || light.quiet(i)
                    || !section.maybeHas(SusChunkFinderV2::isLining)) continue;

                int baseY = (minSectionY + i) * 16;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getLightEmission() > 0) continue;

                            int wx = minX + x;
                            int wy = baseY + y;
                            int wz = minZ + z;
                            int value = light.get(wx, wy, wz);
                            int brightest = light.brightestAround(wx, wy, wz);
                            int source;

                            if (value == 0) {
                                if ((brightest != 4 && brightest != 3) || !isOpenCell(state)) continue;
                                cursor.set(wx, wy, wz);
                                if (!touchesLining(level, cursor) || !litOpening(level, light, cursor, brightest)) continue;
                                source = brightest == 4 ? 5 : 4;
                            } else {
                                if (!isGrowthLight(value) || brightest >= value) continue;
                                cursor.set(wx, wy, wz);
                                if (!touchesLining(level, cursor) || (value == 4 && besideDarkOpening(level, light, cursor))) continue;
                                source = value;
                            }

                            out.accept(cursor.asLong(), source);
                        }
                    }
                }
            }
        }

        private static boolean litOpening(ClientLevel level, LightCache light, BlockPos pos, int brightest) {
            BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
            for (Direction direction : SIDES) {
                next.setWithOffset(pos, direction);
                if (light.get(next.getX(), next.getY(), next.getZ()) == brightest
                    && isOpenCell(level.getBlockState(next))) return true;
            }
            return false;
        }

        private static boolean besideDarkOpening(ClientLevel level, LightCache light, BlockPos pos) {
            BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
            for (Direction direction : SIDES) {
                next.setWithOffset(pos, direction);
                if (light.get(next.getX(), next.getY(), next.getZ()) == 0) {
                    BlockState state = level.getBlockState(next);
                    if (isOpenCell(state) && !state.is(Blocks.CAVE_AIR)) return true;
                }
            }
            return false;
        }

        private static boolean isOpenCell(BlockState state) {
            return state.isAir() || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.WATER);
        }

        static boolean isGrowthLight(int light) {
            return light == 1 || light == 2 || light == 4 || light == 5;
        }

        static boolean touchesLining(ClientLevel level, BlockPos pos) {
            for (Direction direction : SIDES) {
                if (isLining(level.getBlockState(pos.relative(direction)))) return true;
            }
            return false;
        }
    }

    /** Lazily fetched block-light layers for a chunk and its 8 neighbours (one slot per section). */
    private static final class LightCache {
        private final LayerLightEventListener listener;
        private final int chunkX;
        private final int chunkZ;
        private final int minSectionY;
        private final int sectionCount;
        private final DataLayer[] layers;
        private final boolean[] fetched;

        LightCache(ClientLevel level, int chunkX, int chunkZ, int minSectionY, int sectionCount) {
            this.listener = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minSectionY = minSectionY - 1;
            this.sectionCount = sectionCount + 2;
            this.layers = new DataLayer[9 * this.sectionCount];
            this.fetched = new boolean[this.layers.length];
        }

        private DataLayer layer(int dx, int dz, int section) {
            int slot = ((dx + 1) * 3 + dz + 1) * sectionCount + section;
            if (!fetched[slot]) {
                fetched[slot] = true;
                DataLayer data = listener.getDataLayerData(SectionPos.of(chunkX + dx, minSectionY + section, chunkZ + dz));
                layers[slot] = (data != null && !data.isEmpty()) ? data : null;
            }
            return layers[slot];
        }

        int get(int x, int y, int z) {
            int dx = (x >> 4) - chunkX;
            int dz = (z >> 4) - chunkZ;
            int section = (y >> 4) - minSectionY;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1 || section < 0 || section >= sectionCount) return 0;

            DataLayer data = layer(dx, dz, section);
            return data == null ? 0 : data.get(x & 15, y & 15, z & 15);
        }

        int brightestAround(int x, int y, int z) {
            int best = get(x + 1, y, z);
            best = Math.max(best, get(x - 1, y, z));
            best = Math.max(best, get(x, y + 1, z));
            best = Math.max(best, get(x, y - 1, z));
            best = Math.max(best, get(x, y, z + 1));
            return Math.max(best, get(x, y, z - 1));
        }

        boolean quiet(int sectionIndex) {
            int section = sectionIndex + 1;
            return layer(0, 0, section) == null && layer(0, 0, section - 1) == null && layer(0, 0, section + 1) == null
                && layer(-1, 0, section) == null && layer(1, 0, section) == null
                && layer(0, -1, section) == null && layer(0, 1, section) == null;
        }
    }

    /** ANBS+ method: counts cells where the light data shows a geode edge, even if the blocks are hidden. */
    private static final class GeodeGlowScan {
        private static final int MIN_Y = -58;
        private static final int MAX_Y = 30;
        private static final int EDGE_LIGHT = 4;
        private static final int LOW_SECTION = -4;
        private static final int HIGH_SECTION = 1;
        private static final int FIRST_CACHED = -5;
        private static final int CACHED = 8;

        private final DataLayer[] layers = new DataLayer[72];
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int chunkX;
        private int chunkZ;

        int scan(ClientLevel level, LevelChunk chunk) {
            chunkX = chunk.getPos().getMinBlockX() >> 4;
            chunkZ = chunk.getPos().getMinBlockZ() >> 4;
            if (!cacheLight(level)) return 0;

            LevelChunkSection[] sections = chunk.getSections();
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            int count = 0;

            for (int sy = LOW_SECTION; sy <= HIGH_SECTION; sy++) {
                int index = chunk.getSectionIndexFromSectionY(sy);
                if (index < 0 || index >= sections.length || sections[index] == null || !nearLight(sy)) continue;

                LevelChunkSection section = sections[index];
                int from = Math.max(MIN_Y, sy << 4);
                int to = Math.min(MAX_Y, (sy << 4) + 15);

                for (int y = from; y <= to; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (!open(section.getBlockState(x, y & 15, z))) continue;
                            int wx = minX + x;
                            int wz = minZ + z;
                            if (lightAt(wx, y, wz) == 0 && edge(level, wx, y, wz)) count++;
                        }
                    }
                }
            }
            return count;
        }

        private boolean cacheLight(ClientLevel level) {
            LayerLightEventListener light = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
            boolean any = false;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int s = 0; s < CACHED; s++) {
                        DataLayer layer = light.getDataLayerData(SectionPos.of(chunkX + dx, FIRST_CACHED + s, chunkZ + dz));
                        if (layer != null && layer.isEmpty()) layer = null;
                        layers[slot(dx, dz, s)] = layer;
                        any |= layer != null;
                    }
                }
            }
            return any;
        }

        private boolean nearLight(int sy) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int s = sy - 1; s <= sy + 1; s++) {
                        int cached = s - FIRST_CACHED;
                        if (cached >= 0 && cached < CACHED && layers[slot(dx, dz, cached)] != null) return true;
                    }
                }
            }
            return false;
        }

        private boolean edge(ClientLevel level, int x, int y, int z) {
            boolean litOpening = false;
            int brightest = 0;

            for (Direction side : SIDES) {
                int nx = x + side.getStepX();
                int ny = y + side.getStepY();
                int nz = z + side.getStepZ();
                int value = lightAt(nx, ny, nz);
                if (value > EDGE_LIGHT) return false;

                brightest = Math.max(brightest, value);
                if (value == EDGE_LIGHT && !litOpening && open(level.getBlockState(cursor.set(nx, ny, nz)))) {
                    litOpening = true;
                }
            }
            return brightest == EDGE_LIGHT && litOpening;
        }

        private int lightAt(int x, int y, int z) {
            int s = (y >> 4) - FIRST_CACHED;
            if (s < 0 || s >= CACHED) return 0;

            DataLayer layer = layers[slot((x >> 4) - chunkX, (z >> 4) - chunkZ, s)];
            return layer == null ? 0 : layer.get(x & 15, y & 15, z & 15);
        }

        private static int slot(int dx, int dz, int s) {
            return ((dx + 1) * 3 + dz + 1) * CACHED + s;
        }

        private static boolean open(BlockState state) {
            return state.isAir() || state.is(Blocks.AMETHYST_CLUSTER);
        }
    }
}
