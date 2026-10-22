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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds air pockets that are completely sealed on every side and are at most
 * "max-size" blocks big, then fills them with stone (deepslate under Y 0).
 * Client side only.
 */
public class ClearRender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius").description("Scan radius around you in blocks.")
        .defaultValue(16).range(4, 48).sliderRange(4, 32).build());

    private final Setting<Integer> maxSize = sgGeneral.add(new IntSetting.Builder()
        .name("max-size").description("Largest sealed pocket (in blocks) that gets filled.")
        .defaultValue(5).range(1, 32).sliderRange(1, 16).build());

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval").description("Scan every N ticks.")
        .defaultValue(5).range(1, 40).sliderRange(1, 20).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Print how many blocks were filled.")
        .defaultValue(false).build());

    private int ticks;

    public ClearRender() {
        super(AddonTemplate.CATEGORY, "clear-render",
            "Fills small sealed air pockets around you with stone on your client.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (++ticks % interval.get() != 0) return;

        BlockPos origin = mc.player.blockPosition();
        int r = radius.get();
        int max = maxSize.get();

        Set<Long> visited = new HashSet<>();
        List<BlockPos> toFill = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
            for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
                for (int y = origin.getY() - r; y <= origin.getY() + r; y++) {
                    if (y < mc.level.getMinY() || y >= mc.level.getMaxY()) continue;

                    pos.set(x, y, z);
                    if (visited.contains(pos.asLong())) continue;
                    if (!mc.level.hasChunkAt(pos)) continue;
                    if (!mc.level.getBlockState(pos).isAir()) continue;

                    List<BlockPos> pocket = floodFill(pos.immutable(), max, visited);
                    if (pocket != null) toFill.addAll(pocket);
                }
            }
        }

        for (BlockPos p : toFill) {
            Block block = p.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
            mc.level.setBlock(p, block.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        if (debug.get() && !toFill.isEmpty()) info("filled %d blocks", toFill.size());
    }

    /**
     * Returns the pocket if it is sealed and small enough, otherwise null.
     * A pocket is "open" if it grows past max, touches an unloaded chunk, or
     * is bigger than max.
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
