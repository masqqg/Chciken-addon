package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Same detection idea as Codex ClusterDetect: hidden amethyst clusters leak through
 * block light (level 5) in the light packets. A light-5 point that touches a visible
 * amethyst block / budding amethyst is counted. Chunks with enough points get a purple
 * chunk-sized, half-block-tall marker (SusChunkFinder style).
 */
public class ClusterBypass extends Module {
    private static final int CLUSTER_LIGHT = 5;
    private static final int MAX_PER_TICK = 8;
    private static final int MAX_AGE_TICKS = 200;

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
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Render range in chunks.")
        .defaultValue(16).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-cluster").description("Minimum cluster points in a chunk before it is marked.")
        .defaultValue(12).range(1, 64).sliderRange(1, 40).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Lowest Y to scan.")
        .defaultValue(17).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Highest Y to scan.")
        .defaultValue(50).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Boolean> onlyHidden = sgGeneral.add(new BoolSetting.Builder()
        .name("only-hidden").description("Only count points where your client shows a solid block (deepslate/stone).")
        .defaultValue(true).build());

    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
        .name("marker-y").description("Y level of the chunk marker.")
        .defaultValue(64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").description("Marker fill color.")
        .defaultValue(new SettingColor(170, 0, 255, 55)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Marker outline color.")
        .defaultValue(new SettingColor(255, 80, 255, 180)).build());

    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final Queue<Long> unloads = new ConcurrentLinkedQueue<>();
    private final Map<Long, Map<Integer, List<BlockPos>>> chunks = new HashMap<>();

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass",
            "Marks chunks with many hidden amethyst clusters (light leak detection).");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private void clearAll() {
        pending.clear();
        unloads.clear();
        chunks.clear();
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
        if (p.fullChunk) chunks.remove(k);

        Map<Integer, List<BlockPos>> perSection = chunks.computeIfAbsent(k, x -> new HashMap<>());
        int bottomSection = (mc.level.getMinY() >> 4) - 1;

        for (int bit = p.empty.nextSetBit(0); bit >= 0; bit = p.empty.nextSetBit(bit + 1)) {
            perSection.remove(bottomSection + bit);
        }

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

            List<BlockPos> found = decode(p.chunkX, p.chunkZ, sectionY, nib);
            if (!found.isEmpty()) perSection.put(sectionY, found);
        }

        if (perSection.isEmpty()) chunks.remove(k);
    }

    private List<BlockPos> decode(int cx, int cz, int sectionY, byte[] nib) {
        List<BlockPos> out = new ArrayList<>();
        int lo = minY.get();
        int hi = maxY.get();

        for (int i = 0; i < 4096; i++) {
            int b = nib[i >> 1];
            int light = (i & 1) == 0 ? (b & 15) : ((b >> 4) & 15);
            if (light != CLUSTER_LIGHT) continue;

            int ly = i >> 8;
            int y = (sectionY << 4) + ly;
            if (y < lo || y > hi) continue;

            int lx = i & 15;
            int lz = (i >> 4) & 15;
            BlockPos pos = new BlockPos((cx << 4) + lx, y, (cz << 4) + lz);

            if (onlyHidden.get()) {
                BlockState self = mc.level.getBlockState(pos);
                if (!self.canOcclude()) continue;
            }

            if (touchesAmethyst(pos)) out.add(pos);
        }
        return out;
    }

    private boolean touchesAmethyst(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (!mc.level.hasChunkAt(n)) continue;
            BlockState s = mc.level.getBlockState(n);
            if (s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.BUDDING_AMETHYST)) return true;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = range.get();
        double y = markerY.get();

        for (Map.Entry<Long, Map<Integer, List<BlockPos>>> entry : chunks.entrySet()) {
            long k = entry.getKey();
            int cx = (int) (k >> 32);
            int cz = (int) k;

            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;

            int count = 0;
            for (List<BlockPos> list : entry.getValue().values()) count += list.size();
            if (count < minimum.get()) continue;

            AABB marker = new AABB(
                cx * 16.0, y, cz * 16.0,
                cx * 16.0 + 16.0, y + 0.5, cz * 16.0 + 16.0
            );
            event.renderer.box(marker, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }
}
