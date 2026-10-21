package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerBypassV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> yawThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-change").description("Minimum sudden head-yaw change in degrees.")
        .defaultValue(100.0).range(20.0, 180.0).sliderRange(20.0, 180.0).build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("flag-color").description("Color for chunks where a mob had a sudden head turn.")
        .defaultValue(new SettingColor(255, 135, 35, 100)).build());

    private final Map<UUID, Float> previousYaw = new HashMap<>();
    private final Map<Long, Integer> flaggedChunks = new HashMap<>();

    public PlayerBypassV2() {
        super(AddonTemplate.CATEGORY, "player-bypass-v2",
            "Flags loaded underground chunks when a loaded mob's head yaw changes abruptly.");
    }

    @Override
    public void onDeactivate() {
        previousYaw.clear();
        flaggedChunks.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.getY() < -64 || entity.getY() > 0 || entity instanceof net.minecraft.world.entity.player.Player) continue;

            float yaw = entity.getYRot();
            Float oldYaw = previousYaw.put(entity.getUUID(), yaw);
            if (oldYaw != null && angleDifference(oldYaw, yaw) >= yawThreshold.get()) {
                BlockPos pos = entity.blockPosition();
                long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
                flaggedChunks.put(key, 100);
            }
        }

        flaggedChunks.replaceAll((key, ticks) -> ticks - 1);
        flaggedChunks.values().removeIf(ticks -> ticks <= 0);

        for (Map.Entry<Long, Integer> entry : flaggedChunks.entrySet()) {
            int cx = (int) (entry.getKey() >> 32);
            int cz = (int) (long) entry.getKey();
            AABB chunkBox = new AABB(cx * 16.0, -64, cz * 16.0, cx * 16.0 + 16, 1, cz * 16.0 + 16);
            event.renderer.box(chunkBox, color.get(), color.get(), ShapeMode.Sides, 0);
            event.renderer.box(chunkBox, color.get(), color.get(), ShapeMode.Lines, 0);
        }
    }

    private static double angleDifference(float a, float b) {
        double diff = Math.abs((a - b) % 360.0);
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
