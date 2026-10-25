package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage ESP with everything Meteor's Storage ESP has:
 * chests, trapped chests, barrels, ender chests, shulker boxes, furnaces, blast furnaces,
 * smokers, hoppers, dispensers, droppers, brewing stands, chiseled bookshelves,
 * decorated pots and crafters.
 *
 * Colors: chests = gray, hoppers = gray, everything else = orange (all changeable).
 * Results are cached per chunk and replaced as a whole, so nothing flickers.
 */
public class ChickenStorageESP extends Module {
    private static final int GROUP_CHEST = 0;
    private static final int GROUP_HOPPER = 1;
    private static final int GROUP_OTHER = 2;

    private record Entry(BlockPos pos, int group) {}

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range").description("Show storage within this many chunks.")
        .defaultValue(8).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Lowest Y to scan.")
        .defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Highest Y to scan.")
        .defaultValue(320).range(-64, 320).sliderRange(-64, 320).build());

    private final Setting<Integer> sweepTicks = sgGeneral.add(new IntSetting.Builder()
        .name("sweep-ticks").description("Rescan loaded chunks every N ticks.")
        .defaultValue(20).range(1, 200).sliderRange(5, 100).build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("ms-per-tick").description("Scan time budget per tick in milliseconds.")
        .defaultValue(8).range(1, 40).sliderRange(1, 40).build());

    private final Setting<Integer> fillOpacity = sgColors.add(new IntSetting.Builder()
        .name("fill-opacity").description("Opacity of the box fill (0-255).")
        .defaultValue(40).range(0, 255).sliderRange(0, 255).build());

    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder()
        .name("chest-color").description("Chests and trapped chests.")
        .defaultValue(new SettingColor(170, 0, 255, 255)).build());

    private final Setting<SettingColor> hopperColor = sgColors.add(new ColorSetting.Builder()
        .name("hopper-color").description("Hoppers.")
        .defaultValue(new SettingColor(170, 0, 255, 255)).build());

    private final Setting<SettingColor> otherColor = sgColors.add(new ColorSetting.Builder()
        .name("other-color").description("Barrels, ender chests, shulkers, furnaces and the rest.")
        .defaultValue(new SettingColor(255, 20, 200, 255)).build());

    private final Map<Long, List<Entry>> cache = new HashMap<>();
    private final List<int[]> queue = new ArrayList<>();
    private int cursor;
    private int sinceSweep;

    public ChickenStorageESP() {
        super(AddonTemplate.CATEGORY, "chicken-storage-esp",
            "Highlights storage blocks. Chests and hoppers gray, everything else orange.");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int group(BlockState st) {
        if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST)) return GROUP_CHEST;
        if (st.is(Blocks.HOPPER)) return GROUP_HOPPER;
        if (st.is(Blocks.BARREL) || st.is(Blocks.ENDER_CHEST) || st.is(BlockTags.SHULKER_BOXES)
            || st.is(Blocks.FURNACE) || st.is(Blocks.BLAST_FURNACE) || st.is(Blocks.SMOKER)
            || st.is(Blocks.DISPENSER) || st.is(Blocks.DROPPER) || st.is(Blocks.BREWING_STAND)
            || st.is(Blocks.CHISELED_BOOKSHELF) || st.is(Blocks.DECORATED_POT) || st.is(Blocks.CRAFTER)) {
            return GROUP_OTHER;
        }
        return -1;
    }

    private static boolean isStorage(BlockState st) {
        return group(st) >= 0;
    }

    @Override
    public void onActivate() {
        cache.clear();
        queue.clear();
        cursor = 0;
        sinceSweep = Integer.MAX_VALUE / 2;
    }

    @Override
    public void onDeactivate() {
        cache.clear();
        queue.clear();
        cursor = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        sinceSweep++;

        if (cursor >= queue.size()) {
            if (sinceSweep < sweepTicks.get()) return;
            sinceSweep = 0;
            rebuildQueue();
            if (queue.isEmpty()) return;
        }

        long deadline = System.nanoTime() + msPerTick.get() * 1_000_000L;
        while (cursor < queue.size() && System.nanoTime() < deadline) {
            int[] c = queue.get(cursor++);
            if (!mc.level.hasChunk(c[0], c[1])) continue;

            List<Entry> found = scanChunk(c[0], c[1]);
            if (found.isEmpty()) cache.remove(key(c[0], c[1]));
            else cache.put(key(c[0], c[1]), found); // replaced as a whole = no flicker
        }
    }

    private void rebuildQueue() {
        queue.clear();
        cursor = 0;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (mc.level.hasChunk(cx, cz)) queue.add(new int[]{cx, cz, dx * dx + dz * dz});
            }
        }
        queue.sort((a, b) -> Integer.compare(a[2], b[2]));

        // forget chunks that are gone or out of range
        cache.keySet().removeIf(k -> {
            int cx = (int) (k >> 32);
            int cz = (int) (long) k;
            return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !mc.level.hasChunk(cx, cz);
        });
    }

    private List<Entry> scanChunk(int cx, int cz) {
        List<Entry> out = new ArrayList<>();
        LevelChunk chunk = mc.level.getChunk(cx, cz);
        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);
        int minSection = mc.level.getMinY() >> 4;
        int bx = cx << 4;
        int bz = cz << 4;

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            int secMin = (minSection + i) << 4;
            int secMax = secMin + 15;
            if (secMax < lo || secMin > hi) continue;

            LevelChunkSection section = chunk.getSection(i);
            if (section.hasOnlyAir() || !section.maybeHas(ChickenStorageESP::isStorage)) continue;

            int yStart = Math.max(secMin, lo);
            int yEnd = Math.min(secMax, hi);

            for (int y = yStart; y <= yEnd; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState st = section.getBlockState(x, y - secMin, z);
                        int g = group(st);
                        if (g >= 0) out.add(new Entry(new BlockPos(bx + x, y, bz + z), g));
                    }
                }
            }
        }
        return out;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || cache.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();

        Color[] line = {chestColor.get(), hopperColor.get(), otherColor.get()};
        Color[] side = new Color[3];
        for (int i = 0; i < 3; i++) {
            side[i] = new Color(line[i].r, line[i].g, line[i].b, fillOpacity.get());
        }

        for (Map.Entry<Long, List<Entry>> e : cache.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k >> 32);
            int cz = (int) k;
            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;

            for (Entry entry : e.getValue()) {
                event.renderer.box(new AABB(entry.pos()), side[entry.group()], line[entry.group()], ShapeMode.Both, 0);
            }
        }
    }
}
