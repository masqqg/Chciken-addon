package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sweeps ALL loaded chunks (nearest first, a few chunks per tick) and fills every
 * completely sealed air pocket of at most "max-size" blocks with stone
 * (deepslate under Y 0). Client side only. The sweep repeats forever, so blocks
 * the server resends get cleaned again.
 */
public class ClearRender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range").description("Sweep loaded chunks within this many chunks of you.")
        .defaultValue(16).range(2, 32).sliderRange(2, 32).build());

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick").description("How many chunks to process per tick (higher = faster, more lag).")
        .defaultValue(3).range(1, 16).sliderRange(1, 8).build());

    private final Setting<Integer> maxSize = sgGeneral.add(new IntSetting.Builder()
        .name("max-size").description("Largest sealed pocket (in blocks) that gets filled.")
        .defaultValue(5).range(1, 32).sliderRange(1, 16).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Lowest Y to scan.")
        .defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Highest Y to scan.")
        .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Print how many blocks were filled per chunk.")
        .defaultValue(false).build());

    private final List<int[]> queue = new ArrayList<>();
    private int cursor;

    public ClearRender() {
        super(AddonTemplate.CATEGORY, "clear-render",
            "Fills small sealed air pockets in all loaded chunks with stone on your client.");
    }

    @Override
    public void onActivate() {
        queue.clear();
        cursor = 0;
    }

    @Override
    public void onDeactivate() {
        queue.clear();
        cursor = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        if (cursor >= queue.size()) rebuildQueue();
        if (queue.isEmpty()) return;

        int budget = chunksPerTick.get();
        while (budget > 0 && cursor < queue.size()) {
            int[] c = queue.get(cursor++);
            if (!mc.level.hasChunk(c[0], c[1])) continue;
            processChunk(c[0], c[1]);
            budget--;
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
    }

    private void processChunk(int cx, int cz) {
        LevelChunk chunk = mc.level.getChunk(cx, cz);
        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);
        int max = maxSize.get();
        int bx = cx << 4;
        int bz = cz << 4;

        Set<Long> visited = new HashSet<>();
        Set<Long> queued = new HashSet<>();
        List<BlockPos> toFill = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = lo; y <= hi; y++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            if (section.hasOnlyAir()) {
                y = ((y >> 4) << 4) + 15; // skip the rest of this empty section
                continue;
            }

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(bx + x, y, bz + z);
                    long key = pos.asLong();
                    if (visited.contains(key)) continue;
                    if (!chunk.getBlockState(pos).isAir()) continue;

                    List<BlockPos> pocket = floodFill(pos.immutable(), max, visited);
                    if (pocket == null) continue;

                    for (BlockPos p : pocket) {
                        if (queued.add(p.asLong())) toFill.add(p);
                    }
                }
            }
        }

        for (BlockPos p : toFill) {
            Block block = p.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
            mc.level.setBlock(p, block.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        if (debug.get() && !toFill.isEmpty()) {
            info("chunk %d,%d: filled %d blocks", cx, cz, toFill.size());
        }
    }

    /**
     * Returns the pocket if it is sealed and small enough, otherwise null.
     * A pocket is "open" if it grows past max or touches an unloaded chunk.
     */
    private List<BlockPos> floodFill(BlockPos start, int max, Set<Long> visited) {
        List<BlockPos> region = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        boolean open = false;

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            region.add(p);

            if (region.size() > max) {
                open = true;
                break;
            }

            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                long key = n.asLong();
                if (seen.contains(key)) continue;

                // above or below the world counts as a wall
                if (n.getY() < mc.level.getMinY() || n.getY() >= mc.level.getMaxY()) continue;

                if (!mc.level.hasChunkAt(n)) {
                    open = true;
                    break;
                }

                if (mc.level.getBlockState(n).isAir()) {
                    seen.add(key);
                    queue.add(n);
                }
            }

            if (open) break;
        }

        visited.addAll(seen);
        return open ? null : region;
    }
}
