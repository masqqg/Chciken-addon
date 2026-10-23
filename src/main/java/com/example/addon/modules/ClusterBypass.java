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
 * Finds amethyst geodes by directly scanning loaded chunk block data for
 * BUDDING_AMETHYST blocks (the "seed" block a cluster grows from - only 1-2 of
 * its faces are amethyst, the rest of its neighbors are deepslate). For every
 * budding amethyst block found, its DEEPSLATE neighbors are collected into a
 * per-chunk set (so the same deepslate block is never counted twice even if two
 * budding amethyst blocks share it). A chunk whose deepslate count reaches
 * "minimum-deepslate" gets a flat purple marker.
 *
 * This replaced the old light=5 heuristic, which only worked for clusters the
 * server was actively hiding and gave wrong counts on normal, visible geodes.
 * Scanning the block data directly is exact: it counts what is really there.
 */
public class ClusterBypass extends Module {
    private static final Predicate<BlockState> BUDDING = st -> st.is(Blocks.BUDDING_AMETHYST);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range").description("Scan loaded chunks within this many chunks of you.")
        .defaultValue(16).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-deepslate").description("Deepslate blocks touching budding amethyst needed to mark a chunk.")
        .defaultValue(12).range(1, 200).sliderRange(1, 60).build());

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
        .name("debug").description("Print deepslate counts in chat when a chunk changes.")
        .defaultValue(false).build());

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
        .name("marker-y").description("Y level of the chunk marker.")
        .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").description("Marker fill color.")
        .defaultValue(new SettingColor(170, 0, 255, 55)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Marker outline color.")
        .defaultValue(new SettingColor(255, 80, 255, 180)).build());

    /** chunk key -> deepslate blocks touching a budding amethyst block in that chunk */
    private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
    private final List<int[]> queue = new ArrayList<>();
    private int cursor;
    private int sinceSweep;

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass",
            "Marks chunks with amethyst geodes by counting deepslate next to budding amethyst.");
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
            Set<BlockPos> found = scanChunk(c[0], c[1]);

            if (debug.get()) {
                Set<BlockPos> old = chunks.get(k);
                int oldSize = old == null ? 0 : old.size();
                if (found.size() != oldSize) {
                    info("chunk %d,%d: deepslate=%d", c[0], c[1], found.size());
                }
            }

            if (found.isEmpty()) chunks.remove(k);
            else chunks.put(k, found); // replaced as a whole = no flicker
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

        chunks.keySet().removeIf(k -> {
            int cx = (int) (k >> 32);
            int cz = (int) (long) k;
            return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !mc.level.hasChunk(cx, cz);
        });
    }

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
                            if (mc.level.getBlockState(n).is(Blocks.DEEPSLATE)) {
                                deepslate.add(n.immutable());
                            }
                        }
                    }
                }
            }
        }
        return deepslate;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || chunks.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();
        double y = markerY.get();

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
    }
}
