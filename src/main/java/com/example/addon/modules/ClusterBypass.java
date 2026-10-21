package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ClusterBypass extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Radius around the player to scan.")
        .defaultValue(16).range(4, 48).sliderRange(4, 32).build());

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-threshold").description("Flag chunks with at least this many qualifying clusters.")
        .defaultValue(12).range(1, 64).sliderRange(1, 32).build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("flag-color").description("Chunk flag color.").defaultValue(new SettingColor(85, 20, 145, 85)).build());

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass",
            "Counts exposed amethyst clusters in loaded chunks and flags dense chunks.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;
        BlockPos player = mc.player.blockPosition();
        int r = radius.get();
        int minY = Math.max(-64, mc.level.getMinY());
        int maxY = Math.min(0, mc.level.getMaxY() - 1);

        int startChunkX = Math.floorDiv(player.getX() - r, 16);
        int endChunkX = Math.floorDiv(player.getX() + r, 16);
        int startChunkZ = Math.floorDiv(player.getZ() - r, 16);
        int endChunkZ = Math.floorDiv(player.getZ() + r, 16);

        for (int cx = startChunkX; cx <= endChunkX; cx++) {
            for (int cz = startChunkZ; cz <= endChunkZ; cz++) {
                int count = 0;
                int x0 = cx * 16, z0 = cz * 16;
                for (int x = Math.max(x0, player.getX() - r); x < Math.min(x0 + 16, player.getX() + r + 1); x++) {
                    for (int z = Math.max(z0, player.getZ() - r); z < Math.min(z0 + 16, player.getZ() + r + 1); z++) {
                        for (int y = minY; y <= maxY; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (!mc.level.hasChunkAt(pos)) continue;
                            if (mc.level.getBlockState(pos).is(Blocks.AMETHYST_CLUSTER)
                                && isClusterEnclosed(pos)) count++;
                        }
                    }
                }

                if (count >= threshold.get()) {
                    AABB chunkBox = new AABB(x0, minY, z0, x0 + 16, maxY + 1, z0 + 16);
                    event.renderer.box(chunkBox, color.get(), color.get(), ShapeMode.Sides, 0);
                    event.renderer.box(chunkBox, color.get(), color.get(), ShapeMode.Lines, 0);
                }
            }
        }
    }

    private boolean isClusterEnclosed(BlockPos pos) {
        // This heuristic checks the six adjacent blocks. It is a client-side estimate,
        // not proof of a server-side exploit or of a specific budding-amethyst mechanic.
        int solidSides = 0;
        BlockPos[] neighbors = {
            pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()
        };
        for (BlockPos neighbor : neighbors) {
            BlockState state = mc.level.getBlockState(neighbor);
            if (!state.isAir() && !state.is(Blocks.AMETHYST_CLUSTER)
                && !state.is(Blocks.SMALL_AMETHYST_BUD)
                && !state.is(Blocks.MEDIUM_AMETHYST_BUD)
                && !state.is(Blocks.LARGE_AMETHYST_BUD)) solidSides++;
        }
        return solidSides >= 3;
    }
}
