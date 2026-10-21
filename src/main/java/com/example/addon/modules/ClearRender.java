package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes fake (anti-xray) ores around the player on the client only.
 * Fake ores are dense and mixed: lots of ore neighbours, many of a different ore type.
 * Real veins are sparse and single-type. Ores are replaced with stone / deepslate.
 */
public class ClearRender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius").description("Blocks around the player to clean (also up and down).")
        .defaultValue(5).range(1, 16).sliderRange(1, 10).build());

    private final Setting<Integer> minDifferent = sgGeneral.add(new IntSetting.Builder()
        .name("min-different-neighbors")
        .description("Ore is fake if this many neighbors are a DIFFERENT ore type.")
        .defaultValue(2).range(1, 26).sliderRange(1, 10).build());

    private final Setting<Integer> minDense = sgGeneral.add(new IntSetting.Builder()
        .name("min-ore-neighbors")
        .description("Ore is also fake if this many of its 26 neighbors are any ore.")
        .defaultValue(8).range(1, 26).sliderRange(1, 26).build());

    public ClearRender() {
        super(AddonTemplate.CATEGORY, "clear-render",
            "Replaces fake ores around you with stone on your client only.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        BlockPos origin = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = radius.get();
        List<BlockPos> fake = new ArrayList<>();

        // Pass 1: decide using the untouched world so counts stay accurate
        for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
            for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
                for (int y = origin.getY() - r; y <= origin.getY() + r; y++) {
                    pos.set(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;

                    int type = oreType(mc.level.getBlockState(pos));
                    if (type == 0) continue;

                    if (isFake(pos, type)) fake.add(pos.immutable());
                }
            }
        }

        // Pass 2: replace
        for (BlockPos p : fake) {
            Block replacement = p.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
            mc.level.setBlock(p, replacement.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private boolean isFake(BlockPos center, int type) {
        int total = 0;
        int different = 0;
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    n.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    int t = oreType(mc.level.getBlockState(n));
                    if (t == 0) continue;
                    total++;
                    if (t != type) different++;
                }
            }
        }

        return different >= minDifferent.get() || total >= minDense.get();
    }

    private int oreType(BlockState state) {
        if (state.is(BlockTags.COAL_ORES)) return 1;
        if (state.is(BlockTags.IRON_ORES)) return 2;
        if (state.is(BlockTags.COPPER_ORES)) return 3;
        if (state.is(BlockTags.GOLD_ORES)) return 4;
        if (state.is(BlockTags.REDSTONE_ORES)) return 5;
        if (state.is(BlockTags.LAPIS_ORES)) return 6;
        if (state.is(BlockTags.DIAMOND_ORES)) return 7;
        if (state.is(BlockTags.EMERALD_ORES)) return 8;
        return 0;
    }
}
