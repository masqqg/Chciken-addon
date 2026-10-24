package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Combines three independent signals into one "sensitivity" score per chunk.
 * Each method, when it fires, adds 1 point to that chunk's score. A chunk is
 * marked once its score reaches "required-sensitivity". Score decays over time
 * if nothing new happens.
 *
 * 1) EGG   : a chicken egg item appears in the world (chunk farm activity).
 * 2) HEADS : "head-turn burst" - 5+ distinct living entities (any mob) rotate
 *            their head (ClientboundRotateHeadPacket) within a short window,
 *            between Y -64 and Y 0 (they all noticed something, e.g. a player).
 * 3) SPAWN : "spawn burst" - 7+ distinct mobs spawn in the same chunk within a
 *            short window, between Y 0 and Y 18 (unnaturally dense spawning).
 */
public class MobActivityFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> required = sgGeneral.add(new IntSetting.Builder()
        .name("required-sensitivity").description("Score needed to mark a chunk.")
        .defaultValue(1).range(1, 10).sliderRange(1, 5).build());

    private final Setting<Integer> decayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("decay-ticks").description("A chunk loses 1 sensitivity point every N ticks with no new activity.")
        .defaultValue(600).range(20, 6000).sliderRange(20, 2000).build());

    private final Setting<Integer> headMinY = sgGeneral.add(new IntSetting.Builder()
        .name("head-min-y").defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());
    private final Setting<Integer> headMaxY = sgGeneral.add(new IntSetting.Builder()
        .name("head-max-y").defaultValue(0).range(-64, 320).sliderRange(-64, 128).build());
    private final Setting<Integer> headCount = sgGeneral.add(new IntSetting.Builder()
        .name("head-mob-count").description("Distinct mobs that must turn their head together.")
        .defaultValue(5).range(1, 30).sliderRange(1, 15).build());
    private final Setting<Integer> headWindow = sgGeneral.add(new IntSetting.Builder()
        .name("head-window-ticks").description("How close together (in ticks) the head turns must happen.")
        .defaultValue(10).range(1, 100).sliderRange(1, 40).build());

    private final Setting<Integer> spawnMinY = sgGeneral.add(new IntSetting.Builder()
        .name("spawn-min-y").defaultValue(0).range(-64, 320).sliderRange(-64, 128).build());
    private final Setting<Integer> spawnMaxY = sgGeneral.add(new IntSetting.Builder()
        .name("spawn-max-y").defaultValue(18).range(-64, 320).sliderRange(-64, 128).build());
    private final Setting<Integer> spawnCount = sgGeneral.add(new IntSetting.Builder()
        .name("spawn-mob-count").description("Mobs that must spawn together.")
        .defaultValue(7).range(1, 40).sliderRange(1, 20).build());
    private final Setting<Integer> spawnWindow = sgGeneral.add(new IntSetting.Builder()
        .name("spawn-window-ticks").defaultValue(20).range(1, 200).sliderRange(1, 100).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").defaultValue(false).build());

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
        .name("marker-y").defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 150, 0, 55)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 180, 60, 220)).build());

    private final Map<Long, Integer> sensitivity = new HashMap<>();
    private final Map<Long, Integer> lastActivityTick = new HashMap<>();

    // rolling windows: chunkKey -> (entityId -> tick seen)
    private final Map<Long, Map<Integer, Integer>> headWindowMap = new HashMap<>();
    private final Map<Long, Map<Integer, Integer>> spawnWindowMap = new HashMap<>();
    private final Set<Integer> eggSeen = new HashSet<>();

    private int ticks;

    public MobActivityFinder() {
        super(AddonTemplate.CATEGORY, "mob-activity-finder",
            "Marks chunks using egg drops, mob head-turn bursts and spawn bursts as activity signals.");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public void onActivate() {
        sensitivity.clear();
        lastActivityTick.clear();
        headWindowMap.clear();
        spawnWindowMap.clear();
        eggSeen.clear();
        ticks = 0;
    }

    @Override
    public void onDeactivate() {
        sensitivity.clear();
        lastActivityTick.clear();
        headWindowMap.clear();
        spawnWindowMap.clear();
        eggSeen.clear();
    }

    private void addSensitivity(long chunkKey, String reason, int cx, int cz) {
        sensitivity.merge(chunkKey, 1, Integer::sum);
        lastActivityTick.put(chunkKey, ticks);
        if (debug.get()) info("chunk %d,%d: +1 sensitivity (%s), total=%d", cx, cz, reason, sensitivity.get(chunkKey));
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.level == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundRotateHeadPacket p) {
            Entity entity = p.getEntity(mc.level);
            if (!(entity instanceof LivingEntity)) return;

            double y = entity.getY();
            if (y < headMinY.get() || y > headMaxY.get()) return;

            int cx = (int) Math.floor(entity.getX()) >> 4;
            int cz = (int) Math.floor(entity.getZ()) >> 4;
            long k = key(cx, cz);

            Map<Integer, Integer> window = headWindowMap.computeIfAbsent(k, x -> new HashMap<>());
            window.entrySet().removeIf(en -> ticks - en.getValue() > headWindow.get());
            window.put(entity.getId(), ticks);

            if (window.size() >= headCount.get()) {
                addSensitivity(k, "head-turn burst", cx, cz);
                window.clear();
            }
        } else if (packet instanceof ClientboundAddEntityPacket p) {
            EntityType<?> type = p.getType();
            // MISC covers non-mob entities (items, projectiles, display entities, ...).
            // Anything else is a living mob, hostile or passive - both count here.
            if (type.getCategory() == net.minecraft.world.entity.MobCategory.MISC) return;

            double y = p.getY();
            if (y < spawnMinY.get() || y > spawnMaxY.get()) return;

            int cx = (int) Math.floor(p.getX()) >> 4;
            int cz = (int) Math.floor(p.getZ()) >> 4;
            long k = key(cx, cz);

            Map<Integer, Integer> window = spawnWindowMap.computeIfAbsent(k, x -> new HashMap<>());
            window.entrySet().removeIf(en -> ticks - en.getValue() > spawnWindow.get());
            window.put(p.getId(), ticks);

            if (window.size() >= spawnCount.get()) {
                addSensitivity(k, "spawn burst", cx, cz);
                window.clear();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) return;
        ticks++;

        // Method 1: egg items - checked by scanning loaded entities each tick (reliable,
        // doesn't depend on guessing which raw packet carries the item stack)
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item)) continue;
            if (eggSeen.contains(item.getId())) continue;
            if (!item.getItem().is(Items.EGG)) continue;

            eggSeen.add(item.getId());
            int cx = (int) Math.floor(item.getX()) >> 4;
            int cz = (int) Math.floor(item.getZ()) >> 4;
            addSensitivity(key(cx, cz), "egg", cx, cz);
        }
        if (eggSeen.size() > 5000) eggSeen.clear(); // basic cleanup, avoid unbounded growth

        // decay
        if (ticks % 20 == 0) {
            sensitivity.entrySet().removeIf(en -> {
                long k = en.getKey();
                int last = lastActivityTick.getOrDefault(k, 0);
                if (ticks - last < decayTicks.get()) return false;
                lastActivityTick.remove(k);
                return true;
            });
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || sensitivity.isEmpty()) return;
        double y = markerY.get();

        for (Map.Entry<Long, Integer> e : sensitivity.entrySet()) {
            if (e.getValue() < required.get()) continue;

            long k = e.getKey();
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
