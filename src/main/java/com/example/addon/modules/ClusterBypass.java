package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * DÜZELTILMIŞ VERSIYON
 *
 * 1. dosyadaki çalışan light-data tespit mantığını kullanır:
 * - Block light seviyesi 1, 2, 4 veya 5 olan pozisyonlar aranır.
 * - Pozisyonun 4 yatay komşusu da kapalı/occluding ise bud adayı sayılır.
 *
 * 2. dosyadaki render özellikleri korunur:
 * - Chunk marker
 * - Growth area
 * - Bud box
 * - Tracer
 */
public class ClusterBypass extends Module {

    private static final Predicate<BlockState> BUDDING = st -> st.is(Blocks.BUDDING_AMETHYST);
    private static final int MIN_OPEN_SIDES = 2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // =========================
    // General
    // =========================

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
            .name("chunk-range")
            .description("Scan loaded chunks within this many chunks of you.")
            .defaultValue(16)
            .range(1, 32)
            .sliderRange(1, 32)
            .build());

    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
            .name("min-buds")
            .description("Light-data bud candidates needed to mark a chunk.")
            .defaultValue(17)
            .range(1, 200)
            .sliderRange(1, 60)
            .build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
            .name("min-y")
            .description("Lowest Y to scan.")
            .defaultValue(-64)
            .range(-64, 320)
            .sliderRange(-64, 128)
            .build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
            .name("max-y")
            .description("Highest Y to scan.")
            .defaultValue(64)
            .range(-64, 320)
            .sliderRange(-64, 128)
            .build());

    private final Setting<Integer> sweepTicks = sgGeneral.add(new IntSetting.Builder()
            .name("sweep-ticks")
            .description("Rescan loaded chunks every N ticks.")
            .defaultValue(20)
            .range(1, 200)
            .sliderRange(5, 100)
            .build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("ms-per-tick")
            .description("Scan time budget per tick in milliseconds.")
            .defaultValue(8)
            .range(1, 40)
            .sliderRange(1, 40)
            .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
            .name("debug")
            .description("Print bud candidate counts in chat when a chunk changes.")
            .defaultValue(false)
            .build());

    // =========================
    // Render
    // =========================

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
            .name("marker-y")
            .description("Y level of the chunk marker.")
            .defaultValue(64)
            .range(-64, 320)
            .sliderRange(-64, 128)
            .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Marker fill color.")
            .defaultValue(new SettingColor(170, 0, 255, 55))
            .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Marker outline color.")
            .defaultValue(new SettingColor(255, 80, 255, 180))
            .build());

    private final Setting<Boolean> showGrowthArea = sgRender.add(new BoolSetting.Builder()
            .name("show-growth-area")
            .description("Also mark the full square of chunks around a found chunk that fall within simulation distance.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> growthRadius = sgRender.add(new IntSetting.Builder()
            .name("growth-radius")
            .description("Radius in chunks - square side = 2*radius+1.")
            .defaultValue(1)
            .range(1, 16)
            .sliderRange(1, 10)
            .build());

    private final Setting<SettingColor> growthSideColor = sgRender.add(new ColorSetting.Builder()
            .name("growth-side-color")
            .description("Growth-area square fill color.")
            .defaultValue(new SettingColor(255, 150, 220, 25))
            .build());

    private final Setting<SettingColor> growthLineColor = sgRender.add(new ColorSetting.Builder()
            .name("growth-line-color")
            .description("Growth-area square outline color.")
            .defaultValue(new SettingColor(255, 180, 230, 90))
            .build());

    private final Setting<Boolean> showBudBoxes = sgRender.add(new BoolSetting.Builder()
            .name("show-bud-boxes")
            .description("Draw a box on each individual estimated bud position.")
            .defaultValue(true)
            .build());

    private final Setting<SettingColor> budSideColor = sgRender.add(new ColorSetting.Builder()
            .name("bud-side-color")
            .description("Per-block bud box fill color.")
            .defaultValue(new SettingColor(200, 60, 255, 60))
            .build());

    private final Setting<SettingColor> budLineColor = sgRender.add(new ColorSetting.Builder()
            .name("bud-line-color")
            .description("Per-block bud box outline color.")
            .defaultValue(new SettingColor(230, 120, 255, 220))
            .build());

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
            .name("show-tracers")
            .description("Draw a line from you to each estimated bud position.")
            .defaultValue(false)
            .build());

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
            .name("tracer-color")
            .description("Tracer line color.")
            .defaultValue(new SettingColor(230, 120, 255, 160))
            .build());

    /** chunk key -> unique positions classified as light-data bud candidates */
    private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();

    private final List<int[]> queue = new ArrayList<>();
    private int cursor;
    private int sinceSweep;

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass",
                "Marks chunks when block-light bud candidates reach the configured minimum.");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public void onActivate() {
        chunks.clear();
        queue.clear();
        cursor = 0;
        sinceSweep = Integer.MAX_VALUE / 2;
    }

    @Override
    public void onDeactivate() {
        chunks.clear();
        queue.clear();
        cursor = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        sinceSweep++;

        if (cursor >= queue.size()) {
            if (sinceSweep < sweepTicks.get()) return;

            sinceSweep = 0;
            rebuildQueue();

            if (queue.isEmpty()) return;
        }

        long deadline = System.nanoTime() + msPerTick.get() * 1_000_000L;

        while (cursor < queue.size() && System.nanoTime() < deadline) {
            int[] c = queue.get(cursor++);

            if (!mc.level.hasChunk(c[0], c[1])) continue;

            long k = key(c[0], c[1]);

            Set<BlockPos> found = scanLightData(c[0], c[1]);

            if (debug.get()) {
                Set<BlockPos> old = chunks.get(k);
                int oldSize = old == null ? 0 : old.size();

                if (found.size() != oldSize) {
                    info("chunk %d,%d: bud-candidates=%d", c[0], c[1], found.size());
                }
            }

            if (found.isEmpty()) chunks.remove(k);
            else chunks.put(k, found);
        }
    }

    private void rebuildQueue() {
        queue.clear();
        cursor = 0;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;

                if (mc.level.hasChunk(cx, cz)) {
                    queue.add(new int[]{cx, cz, dx * dx + dz * dz});
                }
            }
        }

        queue.sort((a, b) -> Integer.compare(a[2], b[2]));

        chunks.keySet().removeIf(k -> {
            int cx = (int) (k >> 32);
            int cz = (int) k;

            return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !mc.level.hasChunk(cx, cz);
        });
    }

    /**
     * DÜZELTILMIŞ LIGHT DATA TARAMASI
     *
     * 1. dosyadaki çalışan mantık:
     * - Block light 1, 2, 4 veya 5 olan pozisyonlar aranır.
     * - 4 yatay komşu da occluding/closed ise pozisyon bud adayı sayılır.
     *
     * Bu yöntem kesin kanıt değildir, light leak heuristicidir.
     * Ama 1. dosyada ametist alanını gösteren mantık buydu.
     */
    private Set<BlockPos> scanLightData(int cx, int cz) {
        Set<BlockPos> buds = new HashSet<>();

        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        int bx = cx << 4;
        int bz = cz << 4;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int y = lo; y <= hi; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(bx + x, y, bz + z);

                    int light = mc.level.getBrightness(LightLayer.BLOCK, pos);

                    // Amethyst growth light levels:
                    // small bud = 1, medium bud = 2, large bud = 4, cluster = 5
                    if (light != 1 && light != 2 && light != 4 && light != 5) continue;

                    boolean closed = true;

                    for (Direction dir : horizontal) {
                        BlockPos neighbor = pos.relative(dir);

                        if (!mc.level.hasChunkAt(neighbor)
                                || !mc.level.getBlockState(neighbor).canOcclude()) {
                            closed = false;
                            break;
                        }
                    }

                    if (closed) {
                        buds.add(pos.immutable());
                    }
                }
            }
        }

        return buds;
    }

    /**
     * Eski block-state/deepslate tabanlı tarama.
     * Referans olarak bırakıldı, aktif tespit scanLightData() içindedir.
     */
    private Set<BlockPos> scanChunk(int cx, int cz) {
        Set<BlockPos> deepslate = new HashSet<>();

        LevelChunk chunk = mc.level.getChunk(cx, cz);

        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        int minSection = mc.level.getMinY() >> 4;

        int bx = cx << 4;
        int bz = cz << 4;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            int secMin = (minSection + i) << 4;
            int secMax = secMin + 15;

            if (secMax < lo || secMin > hi) continue;

            LevelChunkSection section = chunk.getSection(i);

            if (section.hasOnlyAir() || !section.maybeHas(BUDDING)) continue;

            int yStart = Math.max(secMin, lo);
            int yEnd = Math.min(secMax, hi);

            for (int y = yStart; y <= yEnd; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState st = section.getBlockState(x, y - secMin, z);

                        if (!st.is(Blocks.BUDDING_AMETHYST)) continue;

                        pos.set(bx + x, y, bz + z);

                        for (Direction dir : Direction.values()) {
                            BlockPos n = pos.relative(dir);

                            if (n.getY() < lo || n.getY() > hi) continue;
                            if (!mc.level.hasChunkAt(n)) continue;
                            if (!mc.level.getBlockState(n).is(Blocks.DEEPSLATE)) continue;

                            BlockPos imm = n.immutable();

                            if (!deepslate.contains(imm) && countOpenSides(imm) < MIN_OPEN_SIDES) continue;

                            deepslate.add(imm);
                        }
                    }
                }
            }
        }

        return deepslate;
    }

    /** Number of the 6 faces of this block that are NOT solid. */
    private int countOpenSides(BlockPos pos) {
        int open = 0;

        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);

            if (!mc.level.hasChunkAt(n)) continue;

            if (!mc.level.getBlockState(n).canOcclude()) open++;
        }

        return open;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || chunks.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();

        double y = markerY.get();

        // Growth area
        if (showGrowthArea.get()) {
            int gr = growthRadius.get();
            Set<Long> drawn = new HashSet<>();

            for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
                if (e.getValue().size() < minimum.get()) continue;

                long k = e.getKey();
                int cx = (int) (k >> 32);
                int cz = (int) k;

                if (Math.abs(cx - pcx) > r + gr || Math.abs(cz - pcz) > r + gr) continue;

                for (int dx = -gr; dx <= gr; dx++) {
                    for (int dz = -gr; dz <= gr; dz++) {
                        int ncx = cx + dx;
                        int ncz = cz + dz;

                        long nk = key(ncx, ncz);

                        if (!drawn.add(nk)) continue;

                        AABB area = new AABB(
                                ncx * 16.0, y - 0.05, ncz * 16.0,
                                ncx * 16.0 + 16.0, y - 0.05 + 0.3, ncz * 16.0 + 16.0
                        );

                        event.renderer.box(area, growthSideColor.get(), growthLineColor.get(), ShapeMode.Both, 0);
                    }
                }
            }
        }

        // Main chunk marker
        for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k >> 32);
            int cz = (int) k;

            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;
            if (e.getValue().size() < minimum.get()) continue;

            AABB marker = new AABB(
                    cx * 16.0, y, cz * 16.0,
                    cx * 16.0 + 16.0, y + 0.5, cz * 16.0 + 16.0
            );

            event.renderer.box(marker, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }

        // Bud boxes + tracers
        if (showBudBoxes.get() || showTracers.get()) {
            double ex = mc.player.getX();
            double ey = mc.player.getEyeY();
            double ez = mc.player.getZ();

            for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
                long k = e.getKey();
                int cx = (int) (k >> 32);
                int cz = (int) k;

                if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;
                if (e.getValue().size() < minimum.get()) continue;

                for (BlockPos pos : e.getValue()) {
                    if (showBudBoxes.get()) {
                        event.renderer.box(new AABB(pos), budSideColor.get(), budLineColor.get(), ShapeMode.Both, 0);
                    }

                    if (showTracers.get()) {
                        event.renderer.line(
                                ex, ey, ez,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                tracerColor.get()
                        );
                    }
                }
            }
        }
    }
}
