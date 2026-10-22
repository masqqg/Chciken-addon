package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * Two leaks are combined:
 *  1) LIGHT: hidden amethyst clusters have block light 5. Deepslate blocks touching
 *     those points are collected.
 *  2) PALETTE: the block palette of every chunk section is sent as-is. If a section
 *     (at or below palette-max-y, e.g. under Y 0 where light does not work) lists
 *     amethyst in its palette but has no amethyst block in its data, the server hid
 *     them -> each such section adds "palette-points".
 * A chunk with enough points gets a flat purple marker, a lot of points a big red
 * marker with "Base chance: 80%".
 */
public class ClusterBypass extends Module {
    private static final int CLUSTER_LIGHT = 5;
    private static final int MAX_PER_TICK = 8;
    private static final int MAX_AGE_TICKS = 200;

    private static final Predicate<BlockState> AMETHYST =
        st -> st.is(Blocks.AMETHYST_CLUSTER) || st.is(Blocks.BUDDING_AMETHYST);

    private static class Pending {
        final int chunkX, chunkZ;
        final boolean fullChunk;
        final BitSet mask, empty;
        final List<byte[]> nibbles;
        int age;

        Pending(int chunkX, int chunkZ, boolean fullChunk, BitSet mask, BitSet empty, List<byte[]> nibbles) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.fullChunk = fullChunk;
            this.mask = mask;
            this.empty = empty;
            this.nibbles = nibbles;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPalette = settings.createGroup("Palette");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Render range in chunks.")
        .defaultValue(16).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-points").description("Points needed for the purple marker.")
        .defaultValue(12).range(1, 100).sliderRange(1, 60).build());

    private final Setting<Integer> baseThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("base-threshold").description("Points needed for the big red base marker.")
        .defaultValue(20).range(1, 200).sliderRange(1, 80).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Lowest Y for the light scan.")
        .defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Highest Y for the light scan.")
        .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Print light and palette findings in chat.")
        .defaultValue(false).build());

    private final Setting<Boolean> usePalette = sgPalette.add(new BoolSetting.Builder()
        .name("palette-bypass").description("Also use the section palette leak.")
        .defaultValue(true).build());

    private final Setting<Integer> paletteMaxY = sgPalette.add(new IntSetting.Builder()
        .name("palette-max-y").description("Check sections that start at or below this Y.")
        .defaultValue(0).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> palettePoints = sgPalette.add(new IntSetting.Builder()
        .name("palette-points").description("Points added per section with an amethyst palette leak.")
        .defaultValue(12).range(1, 100).sliderRange(1, 40).build());

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
        .name("marker-y").description("Y level of the chunk markers.")
        .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> baseHeight = sgRender.add(new IntSetting.Builder()
        .name("base-height").description("Height of the red base marker in blocks.")
        .defaultValue(12).range(1, 100).sliderRange(1, 40).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").description("Purple marker fill color.")
        .defaultValue(new SettingColor(170, 0, 255, 55)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Purple marker outline color.")
        .defaultValue(new SettingColor(255, 80, 255, 180)).build());

    private final Setting<SettingColor> baseSideColor = sgRender.add(new ColorSetting.Builder()
        .name("base-side-color").description("Red base marker fill color.")
        .defaultValue(new SettingColor(255, 0, 0, 80)).build());

    private final Setting<SettingColor> baseLineColor = sgRender.add(new ColorSetting.Builder()
        .name("base-line-color").description("Red base marker outline color.")
        .defaultValue(new SettingColor(255, 30, 30, 255)).build());

    private final Setting<SettingColor> baseTextColor = sgRender.add(new ColorSetting.Builder()
        .name("base-text-color").description("Color of the Base chance text.")
        .defaultValue(new SettingColor(255, 60, 60, 255)).build());

    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final Queue<Long> unloads = new ConcurrentLinkedQueue<>();
    private final Map<Long, Map<Integer, Set<BlockPos>>> chunks = new HashMap<>();
    private final Map<Long, Integer> leaks = new HashMap<>();
    private int dbgLight5;

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass",
            "Marks chunks with hidden amethyst clusters (light leak + palette leak).");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int count(Map<Integer, Set<BlockPos>> perSection) {
        int total = 0;
        for (Set<BlockPos> s : perSection.values()) total += s.size();
        return total;
    }

    private int total(long k) {
        Map<Integer, Set<BlockPos>> m = chunks.get(k);
        int c = m == null ? 0 : count(m);
        return c + palettePoints.get() * leaks.getOrDefault(k, 0);
    }

    private Set<Long> allKeys() {
        Set<Long> keys = new HashSet<>(chunks.keySet());
        keys.addAll(leaks.keySet());
        return keys;
    }

    private void clearAll() {
        pending.clear();
        unloads.clear();
        chunks.clear();
        leaks.clear();
    }

    @Override
    public void onActivate() {
        clearAll();
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearAll();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundLevelChunkWithLightPacket p) {
            queueLight(p.getX(), p.getZ(), true, p.getLightData());
        } else if (packet instanceof ClientboundLightUpdatePacket p) {
            queueLight(p.getX(), p.getZ(), false, p.getLightData());
        } else if (packet instanceof ClientboundForgetLevelChunkPacket p) {
            int cx = p.pos().getMinBlockX() >> 4;
            int cz = p.pos().getMinBlockZ() >> 4;
            unloads.add(key(cx, cz));
        }
    }

    private void queueLight(int cx, int cz, boolean full, ClientboundLightUpdatePacketData data) {
        pending.add(new Pending(
            cx, cz, full,
            (BitSet) data.getBlockYMask().clone(),
            (BitSet) data.getEmptyBlockYMask().clone(),
            new ArrayList<>(data.getBlockUpdates())
        ));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            clearAll();
            return;
        }

        Long gone;
        while ((gone = unloads.poll()) != null) {
            long k = gone;
            chunks.remove(k);
            leaks.remove(k);
            pending.removeIf(p -> key(p.chunkX, p.chunkZ) == k);
        }

        int size = pending.size();
        int done = 0;
        for (int i = 0; i < size && done < MAX_PER_TICK; i++) {
            Pending p = pending.poll();
            if (p == null) break;

            if (!mc.level.hasChunk(p.chunkX, p.chunkZ)) {
                if (++p.age < MAX_AGE_TICKS) pending.add(p);
                continue;
            }

            handle(p);
            done++;
        }
    }

    private void handle(Pending p) {
        long k = key(p.chunkX, p.chunkZ);

        if (p.fullChunk) {
            chunks.remove(k);
            if (usePalette.get()) {
                int n = paletteLeaks(p.chunkX, p.chunkZ);
                if (n > 0) leaks.put(k, n);
                else leaks.remove(k);
            }
        }

        Map<Integer, Set<BlockPos>> perSection = chunks.computeIfAbsent(k, x -> new HashMap<>());
        int bottomSection = (mc.level.getMinY() >> 4) - 1;

        for (int bit = p.empty.nextSetBit(0); bit >= 0; bit = p.empty.nextSetBit(bit + 1)) {
            perSection.remove(bottomSection + bit);
        }

        dbgLight5 = 0;
        int idx = 0;
        for (int bit = p.mask.nextSetBit(0); bit >= 0; bit = p.mask.nextSetBit(bit + 1)) {
            byte[] nib = idx < p.nibbles.size() ? p.nibbles.get(idx) : null;
            idx++;
            int sectionY = bottomSection + bit;
            perSection.remove(sectionY);

            if (nib == null || nib.length < 2048) continue;

            int secMin = sectionY << 4;
            int secMax = secMin + 15;
            if (secMin > maxY.get() || secMax < minY.get()) continue;

            Set<BlockPos> found = decode(p.chunkX, p.chunkZ, sectionY, nib);
            if (!found.isEmpty()) perSection.put(sectionY, found);
        }

        if (debug.get() && dbgLight5 > 0) {
            info("chunk %d,%d: light5=%d, deepslate=%d", p.chunkX, p.chunkZ, dbgLight5, count(perSection));
        }

        if (perSection.isEmpty()) chunks.remove(k);
    }

    /** Sections whose palette lists amethyst while no amethyst block is in the data. */
    private int paletteLeaks(int cx, int cz) {
        LevelChunk chunk = mc.level.getChunk(cx, cz);
        int minSection = mc.level.getMinY() >> 4;
        int leakCount = 0;

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            int sectionY = minSection + i;
            if ((sectionY << 4) > paletteMaxY.get()) continue;

            LevelChunkSection section = chunk.getSection(i);
            if (section.hasOnlyAir()) continue;
            if (!section.maybeHas(AMETHYST)) continue;

            int[] visible = {0};
            section.getStates().count((state, n) -> {
                if (AMETHYST.test(state)) visible[0] += n;
            });

            if (debug.get()) {
                info("chunk %d,%d section y=%d: amethyst in palette, visible blocks=%d",
                    cx, cz, sectionY << 4, visible[0]);
            }

            if (visible[0] == 0) leakCount++;
        }
        return leakCount;
    }

    private Set<BlockPos> decode(int cx, int cz, int sectionY, byte[] nib) {
        Set<BlockPos> out = new HashSet<>();
        int lo = minY.get();
        int hi = maxY.get();

        for (int i = 0; i < 4096; i++) {
            int b = nib[i >> 1];
            int light = (i & 1) == 0 ? (b & 15) : ((b >> 4) & 15);
            if (light != CLUSTER_LIGHT) continue;

            int y = (sectionY << 4) + (i >> 8);
            if (y < lo || y > hi) continue;

            int lx = i & 15;
            int lz = (i >> 4) & 15;
            BlockPos pos = new BlockPos((cx << 4) + lx, y, (cz << 4) + lz);

            // Light 5 inside a block we see as solid = hidden cluster
            BlockState self = mc.level.getBlockState(pos);
            if (!self.canOcclude()) continue;
            dbgLight5++;

            // Collect the deepslate blocks around the cluster
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (!mc.level.hasChunkAt(n)) continue;
                if (mc.level.getBlockState(n).is(Blocks.DEEPSLATE)) out.add(n);
            }
        }
        return out;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = range.get();
        double y = markerY.get();

        for (long k : allKeys()) {
            int cx = (int) (k >> 32);
            int cz = (int) k;

            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;

            int points = total(k);
            if (points < minimum.get()) continue;

            if (points >= baseThreshold.get()) {
                AABB big = new AABB(
                    cx * 16.0, y, cz * 16.0,
                    cx * 16.0 + 16.0, y + baseHeight.get(), cz * 16.0 + 16.0
                );
                event.renderer.box(big, baseSideColor.get(), baseLineColor.get(), ShapeMode.Both, 0);
            } else {
                AABB flat = new AABB(
                    cx * 16.0, y, cz * 16.0,
                    cx * 16.0 + 16.0, y + 0.5, cz * 16.0 + 16.0
                );
                event.renderer.box(flat, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = range.get();
        double y = markerY.get() + baseHeight.get() + 1.5;
        String label = "Base chance: 80%";

        for (long k : allKeys()) {
            int cx = (int) (k >> 32);
            int cz = (int) k;

            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;
            if (total(k) < baseThreshold.get()) continue;

            Vector3d pos = new Vector3d(cx * 16.0 + 8.0, y, cz * 16.0 + 8.0);
            if (!NametagUtils.to2D(pos, 1.5)) continue;

            NametagUtils.begin(pos);
            TextRenderer text = TextRenderer.get();
            text.beginBig();
            double half = text.getWidth(label) / 2.0;
            text.render(label, -half, 0, baseTextColor.get(), true);
            text.end();
            NametagUtils.end();
        }
    }
}
