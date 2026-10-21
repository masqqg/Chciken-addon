package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class ClearRender extends Module {
    public enum DisplayMode {
        DeepslateOverlay,
        HideOverlay
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius").description("Radius to scan around the player.")
        .defaultValue(5).range(1, 16).sliderRange(1, 10).build());

    private final Setting<DisplayMode> mode = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("mode").description("Render a deepslate-colored overlay or skip drawing the overlay.")
        .defaultValue(DisplayMode.DeepslateOverlay).build());

    private final Setting<SettingColor> overlayColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("overlay-color").description("Color for the deepslate overlay.")
        .defaultValue(new SettingColor(70, 70, 78, 180)).build());

    public ClearRender() {
        super(AddonTemplate.CATEGORY, "clear-render",
            "Client-side visual overlay for solid blocks near air gaps. Does not change world blocks.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || mode.get() == DisplayMode.HideOverlay) return;

        BlockPos origin = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = radius.get();

        for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
            for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
                for (int y = Math.max(-64, origin.getY() - r); y <= Math.min(0, origin.getY() + r); y++) {
                    pos.set(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;
                    if (mc.level.getBlockState(pos).isAir()) continue;

                    boolean nearAir = false;
                    BlockPos[] neighbors = {pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()};
                    for (BlockPos neighbor : neighbors) {
                        if (mc.level.getBlockState(neighbor).isAir()) {
                            nearAir = true;
                            break;
                        }
                    }

                    if (nearAir) {
                        AABB box = new AABB(pos);
                        SettingColor c = overlayColor.get();
                        event.renderer.box(box, c, c, ShapeMode.Lines, 0);
                    }
                }
            }
        }
    }
}
