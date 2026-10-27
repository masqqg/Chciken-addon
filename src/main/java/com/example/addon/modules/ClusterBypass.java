package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClusterBypass - çift method'lu amethyst tespit (1.21.11 / Meteor)
 *
 * METHOD 1 (cluster + small/medium bud):
 *   Pass 1: light 5 + 4 yatay kapalı taş = cluster
 *   Pass 2: cluster'ın N blok çevresinde light 1/2 + kapalı taş = small/medium bud
 *   Large bud (light 4) ELENDI çünkü cluster'dan gelen light 4 ile ayırt edilemez.
 *
 * METHOD 2 (light-5 gradient doğrulama):
 *   light 5 + 4 yatay kapalı + en az bir komşuda light > 0 = cluster
 *   Komşu light kontrolü gerçek gradient'i doğrular, sahte light-5'leri eler.
 *
 * Her iki method da aynı Set'e ekler, duplicate'ler Set ile elenir.
 * Toplam puan = union. min-buds eşik.
 */
public class ClusterBypass extends Module {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ===================== General =====================

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
            .name("chunk-range").description("Scan loaded chunks within this many chunks of you.")
            .defaultValue(16).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
            .name("min-buds").description("Total candidates from both methods needed to mark a chunk.")
            .defaultValue(12).range(1, 200).sliderRange(1, 60).build());

    private final Setting<Boolean> useMethod1 = sgGeneral.add(new BoolSetting.Builder()
            .name("method-1-cluster-bud").description("Find clusters (light 5) then search nearby for " +
                    "small/medium buds (light 1/2). Large buds excluded.")
            .defaultValue(true).build());

    private final Setting<Boolean> useMethod2 = sgGeneral.add(new BoolSetting.Builder()
            .name("method-2-gradient").description("Find light-5 stones with 4 closed horizontal sides " +
                    "AND at least one neighbor carrying light (confirms real gradient).")
            .defaultValue(true).build());

    private final Setting<Integer> budSearchRadius = sgGeneral.add(new IntSetting.Builder()
            .name("bud-search-radius").description("Method 1: how far around each cluster to search for buds.")
            .defaultValue(3).range(1, 6).sliderRange(1, 6).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
            .name("min-y").description("Lowest Y to scan.")
            .defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
            .name("max-y").description("Highest Y to scan.")
            .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> sweepTicks = sgGeneral.add(new IntSetting.Builder()
            .name("sweep-ticks").description("Rescan loaded chunks every N ticks.")
            .defaultValue(20).range(1, 200).sliderRange(5, 100).build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("ms-per-tick").description("Scan time budget per tick in milliseconds.")
            .defaultValue(8).range(1, 40).sliderRange(1, 40).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
            .name("debug").description("Print candidate counts in chat when a chunk changes.")
            .defaultValue(false).build());

    // ===================== Render: area =====================

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
            .name("marker-y").description("Y level of the chunk marker.")
            .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("Marker fill color.")
            .defaultValue(new SettingColor(170, 0, 255, 55)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("Marker outline color.")
            .defaultValue(new SettingColor(255, 80, 255, 180)).build());

    private final Setting<Boolean> showIcon = sgRender.add(new BoolSetting.Builder()
            .name("show-icon").description("Draw amethyst cluster item icons over the marked chunk.")
            .defaultValue(true).build());

    private final Setting<Integer> iconSize = sgRender.add(new IntSetting.Builder()
            .name("icon-size").description("Icon size in pixels.")
            .defaultValue(24).range(8, 64).sliderRange(8, 48).build());

    private final Setting<Boolean> showGrowthArea = sgRender.add(new BoolSetting.Builder()
            .name("show-growth-area").description("Also mark the square of chunks around a found chunk.")
            .defaultValue(true).build());

    private final Setting<Integer> growthRadius = sgRender.add(new IntSetting.Builder()
            .name("growth-radius").description("Radius in chunks - square side = 2*radius+1.")
            .defaultValue(1).range(1, 16).sliderRange(1, 10).build());

    private final Setting<SettingColor> growthSideColor = sgRender.add(new ColorSetting.Builder()
            .name("growth-side-color").description("Growth-area square fill color.")
            .defaultValue(new SettingColor(255, 150, 220, 25)).build());

    private final Setting<SettingColor> growthLineColor = sgRender.add(new ColorSetting.Builder()
            .name("growth-line-color").description("Growth-area square outline color.")
            .defaultValue(new SettingColor(255, 180, 230, 90)).build());

    // ===================== Render: buds =====================

    private final Setting<Boolean> showBudBoxes = sgRender.add(new BoolSetting.Builder()
            .name("show-bud-boxes").description("Draw a box on each candidate position.")
            .defaultValue(true).build());

    private final Setting<SettingColor> budSideColor = sgRender.add(new ColorSetting.Builder()
            .name("bud-side-color").description("Bud box fill color.")
            .defaultValue(new SettingColor(200, 60, 255, 60)).build());

    private final Setting<SettingColor> budLineColor = sgRender.add(new ColorSetting.Builder()
            .name("bud-line-color").description("Bud box outline color.")
            .defaultValue(new SettingColor(230, 120, 255, 220)).build());

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
            .name("show-tracers").description("Draw a line from you to each candidate position.")
            .defaultValue(false).build());

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
            .name("tracer-color").description("Tracer line color.")
            .defaultValue(new SettingColor(230, 120, 255, 160)).build());

    /** chunk key -> candidate positions (union of both methods) */
    private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();

    private final List<int[]> queue = new ArrayList<>();
    private int cursor;
    private int sinceSweep;

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass",
                "Marks chunks when amethyst light-data candidates reach the configured minimum.");
    }

    // ===================== key helpers =====================

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int chunkX(long k) {
        return (int) (k >> 32);
    }

    private static int chunkZ(long k) {
        return (int) k;
    }

    /** Deprecated hasChunkAt(BlockPos) yerine uyarısız yüklü-chunk kontrolü. */
    private boolean loaded(BlockPos pos) {
        return mc.level != null && mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    // ===================== lifecycle =====================

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
                    info("chunk %d,%d: candidates=%d", c[0], c[1], found.size());
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

                if (mc.level.hasChunk(cx, cz)) queue.add(new int[]{cx, cz, dx * dx + dz * dz});
            }
        }

        queue.sort((a, b) -> Integer.compare(a[2], b[2]));

        // Long -> int cast hatası olmaması için önce primitive long'a unbox.
        chunks.keySet().removeIf(k -> {
            long key = k;
            int cx = chunkX(key);
            int cz = chunkZ(key);
            return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !mc.level.hasChunk(cx, cz);
        });
    }

    // ===================== scan dispatcher =====================

    private Set<BlockPos> scanLightData(int cx, int cz) {
        Set<BlockPos> result = new HashSet<>();

        if (useMethod1.get()) result.addAll(scanMethod1(cx, cz));
        if (useMethod2.get()) result.addAll(scanMethod2(cx, cz));

        return result;
    }

    // ===================== METHOD 1 =====================

    /**
     * METHOD 1 - cluster + small/medium bud:
     *
     * Pass 1: light 5 + 4 yatay kapalı taş = cluster.
     * Pass 2: cluster'ın budSearchRadius blok çevresinde light 1 veya 2 + kapalı taş
     *         = small/medium bud (bud'lar cluster'ın dibinde olur).
     *
     * Large bud (light 4) ELENDI: cluster'dan gelen light 4 ile aynı değerde,
     * ayırt edilemez.
     */
    private Set<BlockPos> scanMethod1(int cx, int cz) {
        Set<BlockPos> result = new HashSet<>();

        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        int bx = cx << 4;
        int bz = cz << 4;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Pass 1: cluster'ları bul (light 5 + 4 yatay kapalı)
        Set<BlockPos> clusters = new HashSet<>();

        for (int y = lo; y <= hi; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(bx + x, y, bz + z);

                    int light = mc.level.getBrightness(LightLayer.BLOCK, pos);
                    if (light != 5) continue;

                    boolean closed = true;
                    for (Direction dir : HORIZONTAL) {
                        BlockPos n = pos.relative(dir);
                        if (!loaded(n) || !mc.level.getBlockState(n).canOcclude()) {
                            closed = false;
                            break;
                        }
                    }

                    if (closed) clusters.add(pos.immutable());
                }
            }
        }

        if (clusters.isEmpty()) return result;

        // Pass 2: cluster çevresinde small/medium bud ara
        int radius = budSearchRadius.get();

        for (BlockPos cluster : clusters) {
            result.add(cluster); // cluster = 1 puan

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos check = cluster.offset(dx, dy, dz);

                        if (check.getY() < lo || check.getY() > hi) continue;
                        if (!loaded(check)) continue;

                        int light = mc.level.getBrightness(LightLayer.BLOCK, check);

                        // Sadece small bud (1) veya medium bud (2).
                        // Large bud (4) ELENDI.
                        if (light != 1 && light != 2) continue;

                        boolean closed = true;
                        for (Direction dir : HORIZONTAL) {
                            BlockPos n = check.relative(dir);
                            if (!loaded(n) || !mc.level.getBlockState(n).canOcclude()) {
                                closed = false;
                                break;
                            }
                        }

                        if (closed) result.add(check.immutable()); // bud = 1 puan
                    }
                }
            }
        }

        return result;
    }

    // ===================== METHOD 2 =====================

    /**
     * METHOD 2 - light-5 gradient doğrulama:
     *
     * light 5 olan taşları topla (cluster adayları).
     * Her birinin 4 yatay komşusuna bak:
     *   - hepsi dolu/kapalı (occluding) olmalı
     *   - en az birinin light seviyesi > 0 olmalı (gradient doğrulama)
     *
     * Komşu light kontrolü: gerçek cluster'ın yanındaki taş cluster'dan ışık alır
     * (light 4), yani komşularda light > 0 olması gerçek bir gradient olduğunu
     * doğrular. Sahte/tekil light-5 bloklarının komşuları genelde light 0 olur.
     */
    private Set<BlockPos> scanMethod2(int cx, int cz) {
        Set<BlockPos> result = new HashSet<>();

        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        int bx = cx << 4;
        int bz = cz << 4;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = lo; y <= hi; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(bx + x, y, bz + z);

                    int light = mc.level.getBrightness(LightLayer.BLOCK, pos);
                    if (light != 5) continue; // sadece cluster

                    boolean allClosed = true;
                    boolean anyNeighborHasLight = false;

                    for (Direction dir : HORIZONTAL) {
                        BlockPos n = pos.relative(dir);

                        if (!loaded(n)) {
                            allClosed = false;
                            break;
                        }

                        BlockState ns = mc.level.getBlockState(n);

                        if (!ns.canOcclude()) {
                            allClosed = false;
                            break;
                        }

                        // komşunun light seviyesine bak (gradient doğrulama)
                        if (mc.level.getBrightness(LightLayer.BLOCK, n) > 0) {
                            anyNeighborHasLight = true;
                        }
                    }

                    if (allClosed && anyNeighborHasLight) {
                        result.add(pos.immutable()); // cluster = 1 puan
                    }
                }
            }
        }

        return result;
    }

    // ===================== render 3D =====================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || chunks.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();

        double y = markerY.get();

        // growth area (altta)
        if (showGrowthArea.get()) {
            int gr = growthRadius.get();
            Set<Long> drawn = new HashSet<>();

            for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
                if (e.getValue().size() < minimum.get()) continue;

                long k = e.getKey();
                int cx = chunkX(k);
                int cz = chunkZ(k);

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

        // bölge marker'ı (üstte)
        for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
            long k = e.getKey();
            int cx = chunkX(k);
            int cz = chunkZ(k);

            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;
            if (e.getValue().size() < minimum.get()) continue;

            AABB marker = new AABB(
                    cx * 16.0, y, cz * 16.0,
                    cx * 16.0 + 16.0, y + 0.5, cz * 16.0 + 16.0
            );

            event.renderer.box(marker, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }

        // bud box + tracer
        if (showBudBoxes.get() || showTracers.get()) {
            double ex = mc.player.getX();
            double ey = mc.player.getEyeY();
            double ez = mc.player.getZ();

            for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
                long k = e.getKey();
                int cx = chunkX(k);
                int cz = chunkZ(k);

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

    // ===================== render 2D: ikon =====================

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showIcon.get()) return;
        if (mc.level == null || mc.player == null || chunks.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();

        double y = markerY.get() + 0.3;

        ItemStack icon = new ItemStack(Items.AMETHYST_CLUSTER);
        int size = Math.max(8, iconSize.get());

        for (Map.Entry<Long, Set<BlockPos>> e : chunks.entrySet()) {
            long k = e.getKey();
            int cx = chunkX(k);
            int cz = chunkZ(k);

            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;
            if (e.getValue().size() < minimum.get()) continue;

            double[][] corners = {
                    {cx * 16.0, cz * 16.0}, {cx * 16.0 + 16.0, cz * 16.0},
                    {cx * 16.0, cz * 16.0 + 16.0}, {cx * 16.0 + 16.0, cz * 16.0 + 16.0}
            };

            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minYS = Double.MAX_VALUE, maxYS = -Double.MAX_VALUE;
            boolean visible = false;

            for (double[] c : corners) {
                Vector3d p = new Vector3d(c[0], y, c[1]);
                if (!NametagUtils.to2D(p, 1.5)) continue;
                visible = true;
                minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                minYS = Math.min(minYS, p.y); maxYS = Math.max(maxYS, p.y);
            }

            if (!visible) continue;

            for (double px = minX; px < maxX; px += size) {
                for (double py = minYS; py < maxYS; py += size) {
                    event.drawContext.renderItem(icon, (int) px, (int) py);
                }
            }
        }
    }
}
