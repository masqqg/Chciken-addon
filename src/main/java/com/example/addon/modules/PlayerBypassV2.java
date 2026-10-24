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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Watches villagers and axolotls for repeated behavior changes: head rotation
 * (yaw) jumping noticeably, or the entity suddenly moving (running) compared to
 * the previous tick. Each such change for one entity counts toward a per-entity
 * streak; once an entity has changed "trigger-count" times (3-5 by default) in
 * a row, its chunk gets marked. This is checked every tick by comparing the
 * entity's current state to what it was last tick (no packet guessing needed -
 * these fields are already synced to the client).
 */
public class PlayerBypassV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> villagers = sgGeneral.add(new BoolSetting.Builder()
        .name("villagers").defaultValue(true).build());

    private final Setting<Boolean> axolotls = sgGeneral.add(new BoolSetting.Builder()
        .name("axolotls").defaultValue(true).build());

    private final Setting<Integer> headDegrees = sgGeneral.add(new IntSetting.Builder()
        .name("head-turn-degrees").description("Minimum head yaw change (degrees) to count as a turn.")
        .defaultValue(25).range(1, 180).sliderRange(5, 90).build());

    private final Setting<Double> runSpeed = sgGeneral.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
        .name("run-speed").description("Minimum blocks moved in one tick to count as running.")
        .defaultValue(0.15).range(0.01, 1.0).sliderRange(0.05, 0.5).build());

    private final Setting<Integer> triggerCount = sgGeneral.add(new IntSetting.Builder()
        .name("trigger-count").description("Times in a row an entity must change before its chunk is marked.")
        .defaultValue(4).range(3, 5).sliderRange(3, 5).build());

    private final Setting<Integer> streakWindow = sgGeneral.add(new IntSetting.Builder()
        .name("streak-window-ticks").description("Max ticks between changes for them to still count as the same streak.")
        .defaultValue(20).range(1, 100).sliderRange(5, 60).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
        .name("marker-y").defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(0, 180, 255, 55)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(60, 210, 255, 220)).build());

    private record Prev(float yaw, double x, double z, int lastTick, int streak) {}

    private final Map<Integer, Prev> tracked = new HashMap<>();
    private final Map<Long, Integer> markedChunks = new HashMap<>(); // chunk -> tick it was marked
    private int ticks;

    public PlayerBypassV2() {
        super(AddonTemplate.CATEGORY, "player-bypass-v2",
            "Marks chunks where villagers/axolotls repeatedly turn their head or run (noticing someone).");
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public void onActivate() {
        tracked.clear();
        markedChunks.clear();
        ticks = 0;
    }

    @Override
    public void onDeactivate() {
        tracked.clear();
        markedChunks.clear();
    }

    private boolean isTarget(EntityType<?> type) {
        if (type == EntityType.VILLAGER && villagers.get()) return true;
        return type == EntityType.AXOLOTL && axolotls.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) return;
        ticks++;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isTarget(e.getType())) continue;

            float yaw = e.getYRot();
            double x = e.getX();
            double z = e.getZ();

            Prev prev = tracked.get(e.getId());
            if (prev == null) {
                tracked.put(e.getId(), new Prev(yaw, x, z, ticks, 0));
                continue;
            }

            float yawDelta = Math.abs(wrapDegrees(yaw - prev.yaw()));
            double dist = Math.hypot(x - prev.x(), z - prev.z());
            boolean changed = yawDelta >= headDegrees.get() || dist >= runSpeed.get();

            int streak = prev.streak();
            if (changed) {
                streak = (ticks - prev.lastTick() <= streakWindow.get()) ? streak + 1 : 1;
            } else if (ticks - prev.lastTick() > streakWindow.get()) {
                streak = 0;
            }

            tracked.put(e.getId(), new Prev(yaw, x, z, changed ? ticks : prev.lastTick(), streak));

            if (streak >= triggerCount.get()) {
                int cx = (int) Math.floor(x) >> 4;
                int cz = (int) Math.floor(z) >> 4;
                long k = chunkKey(cx, cz);
                markedChunks.put(k, ticks);
                if (debug.get()) info("chunk %d,%d marked (streak=%d)", cx, cz, streak);
                tracked.put(e.getId(), new Prev(yaw, x, z, ticks, 0)); // reset after marking
            }
        }

        // forget old tracked entities and faded marks
        if (ticks % 100 == 0) {
            markedChunks.entrySet().removeIf(en -> ticks - en.getValue() > 1200); // ~1 min
        }
    }

    private static float wrapDegrees(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || markedChunks.isEmpty()) return;
        double y = markerY.get();

        for (long k : markedChunks.keySet()) {
            int cx = (int) (k >> 32);
            int cz = (int) k;

            AABB marker = new AABB(
                cx * 16.0, y, cz * 16.0,
                cx * 16.0 + 16.0, y + 0.5, cz * 16.0 + 16.0
            );
            event.renderer.box(marker, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }
}
