package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.MeteorClient;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Storage ESP Bypass - uc kanalli tespit, kalici hafiza ve tracer.
 *
 * Sandic (chest) chunk paketinde iki yerde gelir:
 *   1) blok olarak (section paletinde)
 *   2) ayri bir "block entity" listesinde, koordinatiyla birlikte
 * Sunucudaki anti-xray / anti-ESP eklentileri blogu deepslate/stone ile degistirir,
 * bir kismi block entity listesini de keser. Bu modul uc kanali birlikte kullanir:
 *
 *   KANAL 1 (block entities): LevelChunk#getBlockEntities()
 *   KANAL 2 (block states): section blok state taramasi
 *   KANAL 3 (paket hafizasi): blok guncellemelerinde gorulen konumlar
 *
 * KANAL 3 kalicidir: bulunan chest'ler dimension;x;y;z;grup formatinda diske yazilir,
 * oyun kapanip acildiginda geri yuklenir. Sunucu chest'i sadece yaklasinca acan
 * eklentiler kullaniyorsa bile bir kez gordugun her sey haritada kalir.
 */
public class ChickenStorageESP extends Module {
    private static final int G_CHEST = 0;
    private static final int G_HOPPER = 1;
    private static final int G_OTHER = 2;

    private static final String[] GROUP_NAMES = {"chest", "hopper", "other"};

    private static final String FILE_NAME = "chicken-storage-esp.txt";
    private static final int MAX_SAVED = 60_000;
    private static final long SAVE_INTERVAL_MS = 15_000L;

    private record Entry(BlockPos pos, int group, boolean hidden) {
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChannels = settings.createGroup("Channels");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgTracers = settings.createGroup("Tracers");

    // ---- General ----
    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Show storage within this many chunks.")
        .defaultValue(16)
        .range(1, 32)
        .sliderRange(1, 32)
        .build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Lowest Y to scan.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Highest Y to scan.")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build());

    private final Setting<Integer> sweepTicks = sgGeneral.add(new IntSetting.Builder()
        .name("sweep-ticks")
        .description("Rescan loaded chunks every N ticks.")
        .defaultValue(20)
        .range(1, 200)
        .sliderRange(5, 100)
        .build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("ms-per-tick")
        .description("Scan time budget per tick in milliseconds.")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 40)
        .build());

    // ---- Channels ----
    private final Setting<Boolean> scanEntities = sgChannels.add(new BoolSetting.Builder()
        .name("scan-block-entities")
        .description("Read the block entity list. Catches containers whose block was replaced by stone/deepslate but whose entry leaked.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> scanStates = sgChannels.add(new BoolSetting.Builder()
        .name("scan-block-states")
        .description("Read section block states. Catches containers whose block entity entry was stripped instead.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> packetMemory = sgChannels.add(new BoolSetting.Builder()
        .name("packet-memory")
        .description("Remember containers seen in block updates / block entity packets.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> saveToDisk = sgChannels.add(new BoolSetting.Builder()
        .name("save-to-disk")
        .description("Write remembered containers to disk so they survive a restart.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> showHidden = sgChannels.add(new BoolSetting.Builder()
        .name("show-hidden")
        .description("Draw containers that the server replaced with another block.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatNotify = sgChannels.add(new BoolSetting.Builder()
        .name("notify-hidden")
        .description("Print a chat message the first time a hidden container is found.")
        .defaultValue(false)
        .build());

    // ---- Colors ----
    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder()
        .name("chest-color")
        .defaultValue(new SettingColor(255, 160, 40, 255))
        .build());

    private final Setting<SettingColor> hopperColor = sgColors.add(new ColorSetting.Builder()
        .name("hopper-color")
        .defaultValue(new SettingColor(120, 200, 255, 255))
        .build());

    private final Setting<SettingColor> otherColor = sgColors.add(new ColorSetting.Builder()
        .name("other-color")
        .defaultValue(new SettingColor(255, 90, 220, 255))
        .build());

    private final Setting<SettingColor> hiddenColor = sgColors.add(new ColorSetting.Builder()
        .name("hidden-color")
        .description("Containers the server tried to hide.")
        .defaultValue(new SettingColor(255, 40, 60, 255))
        .build());

    private final Setting<Integer> fillOpacity = sgColors.add(new IntSetting.Builder()
        .name("fill-opacity")
        .defaultValue(35)
        .range(0, 255)
        .sliderRange(0, 255)
        .build());

    // ---- Tracers ----
    private final Setting<Boolean> tracers = sgTracers.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw a line from your eyes to every container in range.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> tracerRange = sgTracers.add(new IntSetting.Builder()
        .name("tracer-range")
        .description("Only draw tracers for containers within this many blocks.")
        .defaultValue(96)
        .range(8, 512)
        .sliderRange(8, 256)
        .build());

    private final Setting<Integer> tracerLimit = sgTracers.add(new IntSetting.Builder()
        .name("tracer-limit")
        .description("Maximum number of tracer lines drawn at once.")
        .defaultValue(96)
        .range(4, 512)
        .sliderRange(8, 256)
        .build());

    private final Setting<Integer> tracerAlpha = sgTracers.add(new IntSetting.Builder()
        .name("tracer-fade")
        .description("How strongly far tracers fade out. 0 = solid lines.")
        .defaultValue(70)
        .range(0, 95)
        .sliderRange(0, 95)
        .build());

    private final Setting<Boolean> tracerStart = sgTracers.add(new BoolSetting.Builder()
        .name("tracer-from-feet")
        .description("Start tracers at your feet instead of your eyes.")
        .defaultValue(false)
        .build());

    private final Map<Long, List<Entry>> cache = new HashMap<>();

    /** dimension -> chunk key -> packed block pos -> grup. Kalici hafiza. */
    private final Map<String, Map<Long, Map<Long, Integer>>> found = new HashMap<>();

    private final ConcurrentLinkedQueue<long[]> incoming = new ConcurrentLinkedQueue<>();
    private final Set<Long> announcedHidden = new HashSet<>();
    private final List<int[]> queue = new ArrayList<>();

    private int cursor;
    private int sinceSweep = Integer.MAX_VALUE / 2;

    private boolean dirty;
    private int savedCount;
    private boolean capWarned;
    private long lastSave = System.currentTimeMillis();

    public ChickenStorageESP() {
        super(AddonTemplate.CATEGORY, "storage-esp-bypass",
            "Storage ESP with leak detection, persistent packet memory and clean tracers.");
    }

    // --------------------------------------------------------------- helpers

    private static long key(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }

    private static int keyX(long k) {
        return (int) (k >> 32);
    }

    private static int keyZ(long k) {
        return (int) k;
    }

    /** Blok state'ten grup. -1 = storage degil. */
    private static int group(BlockState st) {
        if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST)) return G_CHEST;
        if (st.is(Blocks.HOPPER)) return G_HOPPER;
        if (st.is(Blocks.BARREL) || st.is(Blocks.ENDER_CHEST) || st.is(BlockTags.SHULKER_BOXES)
            || st.is(Blocks.FURNACE) || st.is(Blocks.BLAST_FURNACE) || st.is(Blocks.SMOKER)
            || st.is(Blocks.DISPENSER) || st.is(Blocks.DROPPER) || st.is(Blocks.BREWING_STAND)
            || st.is(Blocks.CHISELED_BOOKSHELF) || st.is(Blocks.DECORATED_POT) || st.is(Blocks.CRAFTER)) {
            return G_OTHER;
        }
        return -1;
    }

    /** Block entity tipinden grup. Sunucu blogu degistirdiginde tek bilgi kaynagi. */
    private static int group(BlockEntityType<?> type) {
        if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST) return G_CHEST;
        if (type == BlockEntityType.HOPPER) return G_HOPPER;
        if (type == BlockEntityType.BARREL || type == BlockEntityType.ENDER_CHEST
            || type == BlockEntityType.SHULKER_BOX || type == BlockEntityType.FURNACE
            || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER
            || type == BlockEntityType.DISPENSER || type == BlockEntityType.DROPPER
            || type == BlockEntityType.BREWING_STAND || type == BlockEntityType.MOB_SPAWNER) {
            return G_OTHER;
        }
        return -1;
    }

    private String dimension() {
        return mc.level == null ? "unknown" : mc.level.dimension().identifier().toString();
    }

    private Path storageFile() {
        return MeteorClient.FOLDER.toPath().resolve(FILE_NAME);
    }

    @Override
    public void onActivate() {
        reset();
        if (saveToDisk.get()) loadSaved();
    }

    @Override
    public void onDeactivate() {
        saveSaved(true);
        reset();
    }

    private void reset() {
        cache.clear();
        incoming.clear();
        announcedHidden.clear();
        queue.clear();
        cursor = 0;
        sinceSweep = Integer.MAX_VALUE / 2;
    }

    // ------------------------------------------------------------ persistence

    private void loadSaved() {
        Path path = storageFile();
        if (!Files.isRegularFile(path)) return;

        int count = 0;

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;

                String[] parts = line.split(";");
                if (parts.length != 5) continue;

                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    int g = Integer.parseInt(parts[4]);
                    if (g < 0 || g > G_OTHER) continue;

                    store(parts[0], BlockPos.asLong(x, y, z), g);
                    count++;
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            info("Could not read %s: %s", FILE_NAME, e.getMessage());
            return;
        }

        savedCount = count;
        if (count > 0) info("Loaded %d saved containers.", count);
    }

    /** Kalici haritaya yazar. Yeni kayit eklendiyse true doner. */
    private boolean store(String dim, long packedPos, int group) {
        Map<Long, Map<Long, Integer>> dimMap = found.computeIfAbsent(dim, k -> new HashMap<>());

        long ck = key(BlockPos.getX(packedPos) >> 4, BlockPos.getZ(packedPos) >> 4);
        Map<Long, Integer> chunkMap = dimMap.computeIfAbsent(ck, k -> new HashMap<>());

        Integer old = chunkMap.put(packedPos, group);
        if (old == null) {
            savedCount++;
            dirty = true;
            return true;
        }
        if (old != group) dirty = true;
        return false;
    }

    private void saveSaved(boolean sync) {
        if (!saveToDisk.get() || !dirty) return;
        dirty = false;

        // Tick thread'inde sadece kopya al, dosya isini ayri thread'de yap.
        Map<String, Map<Long, Map<Long, Integer>>> snapshot = new HashMap<>();
        for (Map.Entry<String, Map<Long, Map<Long, Integer>>> e : found.entrySet()) {
            Map<Long, Map<Long, Integer>> inner = new HashMap<>();
            for (Map.Entry<Long, Map<Long, Integer>> c : e.getValue().entrySet()) {
                inner.put(c.getKey(), new HashMap<>(c.getValue()));
            }
            snapshot.put(e.getKey(), inner);
        }

        Runnable task = () -> {
            try {
                Path dir = MeteorClient.FOLDER.toPath();
                Files.createDirectories(dir);

                Path target = dir.resolve(FILE_NAME);
                Path tmp = dir.resolve(FILE_NAME + ".tmp");

                List<String> lines = new ArrayList<>();
                lines.add("# chicken-storage-esp: dimension;x;y;z;group");

                for (Map.Entry<String, Map<Long, Map<Long, Integer>>> e : snapshot.entrySet()) {
                    for (Map<Long, Integer> chunkMap : e.getValue().values()) {
                        for (Map.Entry<Long, Integer> entry : chunkMap.entrySet()) {
                            BlockPos pos = BlockPos.of(entry.getKey());
                            lines.add(e.getKey() + ";" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ()
                                + ";" + entry.getValue());
                        }
                    }
                }

                Files.write(tmp, lines, StandardCharsets.UTF_8);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // dosya yazilamadi: oyunu kilitlememek icin sessiz gec
            }
        };

        if (sync) {
            task.run();
        } else {
            new Thread(task, "chicken-storage-esp-save").start();
        }
    }

    // ------------------------------------------------------------- packet memory

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!packetMemory.get()) return;

        if (event.packet instanceof ClientboundBlockUpdatePacket p) {
            int g = group(p.getBlockState());
            if (g >= 0) remember(p.getPos(), g);
        } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates((pos, state) -> {
                int g = group(state);
                if (g >= 0) remember(pos, g);
            });
        } else if (event.packet instanceof ClientboundBlockEntityDataPacket p) {
            int g = group(p.getType());
            if (g >= 0) remember(p.getPos(), g);
        }
    }

    /** Ag is parcacigindan cagrilir: sadece kuyruga at, haritaya dokunma. */
    private void remember(BlockPos pos, int group) {
        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) return;
        incoming.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), group});
    }

    private void drainIncoming() {
        if (incoming.isEmpty()) return;

        Set<Long> dirtyChunks = new HashSet<>();
        String dim = dimension();
        long[] a;

        while ((a = incoming.poll()) != null) {
            if (savedCount >= MAX_SAVED) {
                if (!capWarned) {
                    capWarned = true;
                    info("Storage memory is full (%d entries), new containers are not saved.", MAX_SAVED);
                }
                continue;
            }

            long pk = BlockPos.asLong((int) a[0], (int) a[1], (int) a[2]);
            store(dim, pk, (int) a[3]);
            dirtyChunks.add(key((int) (a[0] >> 4), (int) (a[2] >> 4)));
        }

        // Sadece etkilenen chunk'lari yeniden tara (tick thread'inde).
        for (long ck : dirtyChunks) rescan(keyX(ck), keyZ(ck));
    }

    // ------------------------------------------------------------------- tick

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        drainIncoming();

        if (System.currentTimeMillis() - lastSave > SAVE_INTERVAL_MS) {
            lastSave = System.currentTimeMillis();
            saveSaved(false);
        }

        sinceSweep++;

        if (cursor >= queue.size()) {
            if (sinceSweep < sweepTicks.get()) return;
            sinceSweep = 0;

            int pcx = mc.player.blockPosition().getX() >> 4;
            int pcz = mc.player.blockPosition().getZ() >> 4;
            int r = chunkRange.get();

            queue.clear();
            cursor = 0;

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (!mc.level.hasChunk(cx, cz)) continue;
                    queue.add(new int[]{cx, cz, dx * dx + dz * dz});
                }
            }
            queue.sort((a, b) -> Integer.compare(a[2], b[2]));

            // Menzil disina cikan cache kayitlarini at (kalici hafiza etkilenmez).
            cache.keySet().removeIf(k -> {
                int cx = keyX(k);
                int cz = keyZ(k);
                return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !mc.level.hasChunk(cx, cz);
            });

            if (queue.isEmpty()) return;
        }

        long deadline = System.nanoTime() + msPerTick.get() * 1_000_000L;

        while (cursor < queue.size() && System.nanoTime() < deadline) {
            int[] c = queue.get(cursor++);
            if (!mc.level.hasChunk(c[0], c[1])) continue;
            rescan(c[0], c[1]);
        }
    }

    /** Bir chunk'i yeniden tarar ve cache'e yazar. */
    private void rescan(int cx, int cz) {
        if (mc.level == null) return;
        if (!mc.level.hasChunk(cx, cz)) return;

        LevelChunk chunk = mc.level.getChunk(cx, cz);
        List<Entry> entries = scanChunk(chunk, cx, cz);
        long k = key(cx, cz);

        if (entries.isEmpty()) {
            cache.remove(k);
        } else {
            cache.put(k, entries);
        }
    }

    private List<Entry> scanChunk(LevelChunk chunk, int cx, int cz) {
        List<Entry> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        // ---- KANAL 1: block entity listesi ----
        if (scanEntities.get()) {
            for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = e.getKey();
                if (pos.getY() < lo || pos.getY() > hi) continue;

                int g = group(e.getValue().getType());
                if (g < 0) continue;

                BlockState state = chunk.getBlockState(pos);
                boolean hidden = group(state) < 0;

                if (hidden && !showHidden.get()) continue;

                out.add(new Entry(pos, g, hidden));
                seen.add(pos.asLong());

                if (hidden && chatNotify.get() && announcedHidden.add(pos.asLong())) {
                    info("Hidden container at %d, %d, %d (%s behind %s)", pos.getX(), pos.getY(), pos.getZ(),
                        GROUP_NAMES[g], state.getBlock().getName().getString());
                }
            }
        }

        // ---- KANAL 2: blok state'leri ----
        if (scanStates.get()) {
            int byteX = cx << 4;
            int byteZ = cz << 4;
            int minSection = mc.level.getMinY() >> 4;

            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                int secMin = (minSection + i) << 4;
                int secMax = secMin + 15;
                if (secMax < lo || secMin > hi) continue;

                LevelChunkSection section = chunk.getSection(i);
                if (section == null || section.hasOnlyAir()) continue;

                int yStart = Math.max(secMin, lo);
                int yEnd = Math.min(secMax, hi);

                for (int y = yStart; y <= yEnd; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState st = section.getBlockState(x, y - secMin, z);
                            int g = group(st);
                            if (g < 0) continue;

                            BlockPos pos = new BlockPos(byteX + x, y, byteZ + z);
                            long pk = pos.asLong();
                            if (seen.contains(pk)) continue;

                            seen.add(pk);
                            out.add(new Entry(pos, g, false));
                        }
                    }
                }
            }
        }

        // ---- KANAL 3: kalici hafiza ----
        Map<Long, Map<Long, Integer>> dimMap = found.get(dimension());
        Map<Long, Integer> remembered = dimMap == null ? null : dimMap.get(key(cx, cz));

        if (remembered != null && !remembered.isEmpty()) {
            for (Map.Entry<Long, Integer> e : remembered.entrySet()) {
                long pk = e.getKey();
                if (seen.contains(pk)) continue;

                BlockPos pos = BlockPos.of(pk);
                if (pos.getY() < lo || pos.getY() > hi) continue;

                seen.add(pk);
                out.add(new Entry(pos, e.getValue(), false));
            }
        }

        return out;
    }

    // ------------------------------------------------------------------ render

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || cache.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();
        int op = fillOpacity.get();

        Color[] line = {chestColor.get(), hopperColor.get(), otherColor.get(), hiddenColor.get()};
        Color hidden = line[3];

        // --- kutular ---
        for (Map.Entry<Long, List<Entry>> e : cache.entrySet()) {
            if (Math.abs(keyX(e.getKey()) - pcx) > r || Math.abs(keyZ(e.getKey()) - pcz) > r) continue;

            for (Entry entry : e.getValue()) {
                Color c = entry.hidden() ? hidden : line[entry.group()];
                Color side = new Color(c.r, c.g, c.b, op);

                event.renderer.box(new AABB(entry.pos()), side, c, ShapeMode.Both, 0);
            }
        }

        // --- tracer'lar ---
        if (!tracers.get()) return;

        int range = tracerRange.get();
        int limit = tracerLimit.get();
        double fadeFactor = tracerAlpha.get() / 100.0;
        boolean feet = tracerStart.get();

        double ox = mc.player.getX();
        double oy = feet ? mc.player.getY() : mc.player.getEyeY();
        double oz = mc.player.getZ();

        int drawn = 0;

        for (Map.Entry<Long, List<Entry>> e : cache.entrySet()) {
            if (drawn >= limit) break;
            if (Math.abs(keyX(e.getKey()) - pcx) > r || Math.abs(keyZ(e.getKey()) - pcz) > r) continue;

            for (Entry entry : e.getValue()) {
                if (drawn >= limit) break;

                double tx = entry.pos().getX() + 0.5;
                double ty = entry.pos().getY() + 0.5;
                double tz = entry.pos().getZ() + 0.5;

                double dx = tx - ox;
                double dy = ty - oy;
                double dz = tz - oz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist > range) continue;

                Color base = entry.hidden() ? hidden : line[entry.group()];
                int alpha = (int) (255 * (1.0 - fadeFactor * (dist / range)));
                if (alpha < 30) alpha = 30;

                event.renderer.line(ox, oy, oz, tx, ty, tz, new Color(base.r, base.g, base.b, alpha));
                drawn++;
            }
        }
    }
}
