package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds chosen blocks in loaded chunks and renders them through walls. */
public class BlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgTracers = settings.createGroup("Tracers");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to search for.")
        .defaultValue(
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.ANCIENT_DEBRIS, Blocks.CHEST, Blocks.SPAWNER,
            Blocks.AMETHYST_CLUSTER, Blocks.BUDDING_AMETHYST
        )
        .onChanged(list -> rebuildIfActive())
        .build());

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Scan and render within this many chunks.")
        .defaultValue(12)
        .range(1, 32)
        .sliderRange(1, 32)
        .build());

    private final Setting<Integer> maxBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks")
        .description("Maximum number of found blocks kept in memory.")
        .defaultValue(10000)
        .range(100, 50000)
        .sliderRange(500, 20000)
        .build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("ms-per-tick")
        .description("Chunk scan time budget per tick.")
        .defaultValue(6)
        .range(1, 30)
        .sliderRange(1, 20)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are drawn.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(0, 255, 210, 220))
        .build());

    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity")
        .defaultValue(35)
        .range(0, 255)
        .sliderRange(0, 255)
        .build());

    private final Setting<Boolean> tracers = sgTracers.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw lines from the camera to found blocks.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> tracerRange = sgTracers.add(new IntSetting.Builder()
        .name("tracer-range")
        .defaultValue(256)
        .range(8, 512)
        .sliderRange(8, 512)
        .build());

    private final Setting<Integer> tracerLimit = sgTracers.add(new IntSetting.Builder()
        .name("tracer-limit")
        .defaultValue(64)
        .range(4, 256)
        .sliderRange(4, 128)
        .build());

    private final Setting<SettingColor> tracerColor = sgTracers.add(new ColorSetting.Builder()
        .name("tracer-color")
        .defaultValue(new SettingColor(0, 255, 210, 140))
        .build());

    /** chunk key -> packed block positions. */
    private final Map<Long, Set<Long>> chunks = new HashMap<>();
    private final ArrayDeque<int[]> scanQueue = new ArrayDeque<>();

    private int found;

    public BlockESP() {
        super(AddonTemplate.CATEGORY, "chicken-block-esp", "Finds selected blocks in loaded chunks and draws them through walls.");
    }

    @Override
    public void onActivate() {
        chunks.clear();
        scanQueue.clear();
        found = 0;
        enqueueLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        chunks.clear();
        scanQueue.clear();
        found = 0;
    }

    private void rebuildIfActive() {
        if (!isActive()) return;
        chunks.clear();
        scanQueue.clear();
        found = 0;
        enqueueLoadedChunks();
    }

    private void enqueueLoadedChunks() {
        if (mc.level == null || mc.player == null) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int range = chunkRange.get();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (mc.level.hasChunk(cx, cz)) scanQueue.add(new int[]{cx, cz});
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.level == null || mc.player == null) return;

        int cx = event.chunk().getPos().x;
        int cz = event.chunk().getPos().z;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int range = chunkRange.get();

        if (Math.abs(cx - pcx) <= range && Math.abs(cz - pcz) <= range) {
            scanQueue.add(new int[]{cx, cz});
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.level == null || event.pos == null || event.oldState == null || event.newState == null) return;

        List<Block> wanted = blocks.get();
        boolean was = wanted.contains(event.oldState.getBlock());
        boolean now = wanted.contains(event.newState.getBlock());

        if (was == now) return;

        long chunkKey = key(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        Set<Long> positions = chunks.computeIfAbsent(chunkKey, k -> new HashSet<>());

        if (now) {
            if (found < maxBlocks.get() && positions.add(event.pos.asLong())) found++;
        } else if (positions.remove(event.pos.asLong())) {
            found--;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        if (scanQueue.isEmpty()) return;

        long deadline = System.nanoTime() + msPerTick.get() * 1_000_000L;

        while (!scanQueue.isEmpty() && System.nanoTime() < deadline) {
            int[] c = scanQueue.poll();
            if (mc.level.hasChunk(c[0], c[1])) scanChunk(mc.level.getChunk(c[0], c[1]));
        }
    }

    private void scanChunk(LevelChunk chunk) {
        List<Block> wanted = blocks.get();
        Set<Long> positions = new HashSet<>();
        int minSection = mc.level.getMinY() >> 4;
        int minY = mc.level.getMinY();
        int maxY = mc.level.getMaxY() - 1;

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            LevelChunkSection section = chunk.getSection(i);
            if (section == null || section.hasOnlyAir()) continue;

            int secMin = (minSection + i) << 4;
            int secMax = secMin + 15;
            if (secMax < minY || secMin > maxY) continue;

            int yStart = Math.max(secMin, minY);
            int yEnd = Math.min(secMax, maxY);

            for (int y = yStart; y <= yEnd; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = section.getBlockState(x, y - secMin, z).getBlock();
                        if (!wanted.contains(block)) continue;
                        positions.add(BlockPos.asLong((chunk.getPos().x << 4) + x, y, (chunk.getPos().z << 4) + z));
                    }
                }
            }
        }

        long key = key(chunk.getPos().x, chunk.getPos().z);
        Set<Long> old = chunks.put(key, positions);
        if (old != null) found -= old.size();
        found += positions.size();

        if (positions.isEmpty()) chunks.remove(key);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || chunks.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int range = chunkRange.get();
        Color line = lineColor.get();
        Color side = new Color(line.r, line.g, line.b, fillOpacity.get());
        ShapeMode mode = shapeMode.get();

        int drawn = 0;
        List<BlockPos> tracerTargets = new ArrayList<>();

        for (Map.Entry<Long, Set<Long>> entry : chunks.entrySet()) {
            int cx = keyX(entry.getKey());
            int cz = keyZ(entry.getKey());
            if (Math.abs(cx - pcx) > range || Math.abs(cz - pcz) > range) continue;
            if (!mc.level.hasChunk(cx, cz)) continue;

            for (long packed : entry.getValue()) {
                if (drawn >= maxBlocks.get()) break;

                BlockPos pos = BlockPos.of(packed);
                event.renderer.box(new AABB(pos), side, line, mode, 0);
                drawn++;

                if (tracers.get()) tracerTargets.add(pos);
            }
        }

        if (!tracers.get() || tracerTargets.isEmpty()) return;

        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        int limit = Math.min(tracerTargets.size(), tracerLimit.get());
        int tracerRangeSq = tracerRange.get() * tracerRange.get();

        for (int i = 0; i < limit; i++) {
            BlockPos pos = tracerTargets.get(i);
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            double dx = x - camera.x;
            double dy = y - camera.y;
            double dz = z - camera.z;
            if (dx * dx + dy * dy + dz * dz > tracerRangeSq) continue;

            event.renderer.line(camera.x, camera.y, camera.z, x, y, z, tracerColor.get());
        }
    }

    @Override
    public String getInfoString() {
        return found + " blocks";
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyZ(long key) {
        return (int) key;
    }
}
