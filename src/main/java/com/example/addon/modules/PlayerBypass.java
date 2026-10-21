package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerBypass extends Module {
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color").description("Color for underground players.")
        .defaultValue(new SettingColor(255, 70, 90, 255)).build());

    private final Map<UUID, Integer> lastSeenChunk = new HashMap<>();

    public PlayerBypass() {
        super(AddonTemplate.CATEGORY, "player-bypass",
            "Highlights currently received player entities below Y=0; chunk association is only an estimate.");
    }

    @Override
    public void onDeactivate() {
        lastSeenChunk.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        for (Player player : mc.level.players()) {
            if (player == mc.player || player.getY() > 0 || player.getY() < -64) continue;

            BlockPos pos = player.blockPosition();
            int chunkKey = (pos.getX() >> 4) * 31 + (pos.getZ() >> 4);
            lastSeenChunk.put(player.getUUID(), chunkKey);

            AABB box = player.getBoundingBox();
            event.renderer.box(box, color.get(), color.get(), ShapeMode.Lines, 0);
            event.renderer.line(
                mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                player.getX(), player.getEyeY(), player.getZ(), color.get()
            );
        }
    }
}
