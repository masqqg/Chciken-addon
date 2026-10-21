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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class ClearRender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius").description("Blocks around the player to clean (also up and down).")
        .defaultValue(5).range(1, 16).sliderRange(1, 10).build());

    private final Setting<Boolean> onlyEnclosed = sgGeneral.add(new BoolSetting.Builder()
        .name("only-enclosed").description("Only replace ores that touch no air or fluid (fake ores).")
        .defaultValue(true).build());

    private final Setting<Boolean> deepslateBelowZero = sgGeneral.add(new BoolSetting.Builder()
        .name("deepslate-below-zero").description("Use deepslate under Y 0, stone above.")
        .defaultValue(true).build());

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

        for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
            for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
                for (int y = origin.getY() - r; y <= origin.getY() + r; y++) {
                    pos.set(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;

                    BlockState state = mc.level.getBlockState(pos);
                    if (!isOre(state)) continue;
                    if (onlyEnclosed.get() && isExposed(pos)) continue;

                    Block replacement = (deepslateBelowZero.get() && y < 0) ? Blocks.DEEPSLATE : Blocks.STONE;
                    mc.level.setBlock(pos.immutable(), replacement.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
            || state.is(BlockTags.IRON_ORES)
            || state.is(BlockTags.COPPER_ORES)
            || state.is(BlockTags.GOLD_ORES)
            || state.is(BlockTags.REDSTONE_ORES)
            || state.is(BlockTags.LAPIS_ORES)
            || state.is(BlockTags.DIAMOND_ORES)
            || state.is(BlockTags.EMERALD_ORES);
    }

    private boolean isExposed(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState neighbor = mc.level.getBlockState(pos.relative(dir));
            if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) return true;
        }
        return false;
    }
}
