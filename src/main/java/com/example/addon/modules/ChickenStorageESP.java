package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ChickenStorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Horizontal scan radius around you.")
        .defaultValue(16).range(4, 64).sliderRange(4, 32).build());

    private final Setting<Boolean> includeBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("barrels").description("Highlight barrels.").defaultValue(true).build());

    private final Setting<Boolean> includeEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chests").description("Highlight ender chests.").defaultValue(true).build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color").description("ESP color.").defaultValue(new SettingColor(180, 70, 255, 255)).build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").description("Draw a line from your camera to each found storage block.")
        .defaultValue(true).build());

    public ChickenStorageESP() {
        super(AddonTemplate.CATEGORY, "chicken-storage-esp",
            "Highlights storage blocks in loaded chunks from Y -64 to 0.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        BlockPos origin = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = radius.get();
        int minY = Math.max(-64, mc.level.getMinY());
        int maxY = Math.min(0, mc.level.getMaxY() - 1);

        for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
            for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;
                    BlockState state = mc.level.getBlockState(pos);
                    Block block = state.getBlock();

                    boolean found = block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                        || (includeBarrels.get() && block == Blocks.BARREL)
                        || (includeEnderChests.get() && block == Blocks.ENDER_CHEST);
                    if (!found) continue;

                    AABB box = new AABB(pos);
                    event.renderer.box(box, color.get(), color.get(), ShapeMode.Lines, 0);
                    if (tracers.get()) {
                        event.renderer.line(
                            mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                            x + 0.5, y + 0.5, z + 0.5, color.get()
                        );
                    }
                }
            }
        }
    }
}
