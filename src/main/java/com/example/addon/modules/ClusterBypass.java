package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.AABB;

/**
 * Blok ışığı verisinden amethyst geode'larını bulur (blok state'i okumaz).
 * ClusterBypass v6
 *
 * v6 yenilikleri:
 *  - Işık, section DataLayer'larından okunur (getBrightness yerine) -> belirgin hız artışı
 *  - Otomatik marker Y: işaret geode'nin gerçek derinliğine çizilir
 *  - Sohbete "geode bulundu" bildirimi (chunk başına 1 kez, koordinat + mesafe + yön)
 *  - HUD: geode sayısı, sweep yüzdesi, en yakın geode mesafesi/yönü, tarama süresi
 *  - Tracer: en yakın geode'ye çizgi
 *  - Ziyaret edilen geodeları gizleme
 *  - Overworld filtresi ve ayar değişince kuyruğu otomatik yenileme
 *  - Sprite artık blok blok değil, birleştirilmiş dikdörtgenlerle çizilir (daha az draw call)
 */
public class ClusterBypass extends Module {
    private static final int BUD_LIGHT_MASK = (1 << 1) | (1 << 2) | (1 << 4) | (1 << 5);
    private static final int MAX_BUD_LIGHT = 5;

    private static final int QUIET_FULL_SCANS = 3;
    private static final int COLD_PERIOD = 5;

    private static final Direction[] DIRS = Direction.values();
    private static final String[] COMPASS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private final Random random = new Random();

    private static final int[] SPRITE_PALETTE = {7755952, 13409009, 9136332, 6309019, 16697829, 11503858, 16711121};
    private static final String[] SPRITE = {
        "................",
        ".......B........",
        "......BEF.......",
        ".....BBECB......",
        "....BBBECCB.....",
        "....BEGGFCB.....",
        "....AFFBACA.....",
        "....ACFBACD.....",
        "EEB..ACBAAD..BEE",
        "BEEB.AAFAAD.BEEB",
        "BCGBBDACFA.BBGCB",
        "BCCEADAAFDCCBCCB",
        ".BCABADACDABACB.",
        "..BAAAADCDCAAC..",
        "...CADADBCDAC...",
        "...ADDDCBDDDA..."
    };
    private static final Color[] SPRITE_COLORS = new Color[SPRITE_PALETTE.length];
    private static final List<int[]> SPRITE_RECTS = new ArrayList<>();

    static {
        for (int i = 0; i < SPRITE_PALETTE.length; i++) {
            int p = SPRITE_PALETTE[i];
            SPRITE_COLORS[i] = new Color(p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF, 255);
        }

        int rows = SPRITE.length;
        boolean[][] used = new boolean[rows][];
        for (int r = 0; r < rows; r++) used[r] = new boolean[SPRITE[r].length()];

        for (int r = 0; r < rows; r++) {
            String line = SPRITE[r];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.' || used[r][c]) continue;

                int w = 1;
                while (c + w < line.length() && line.charAt(c + w) == ch && !used[r][c + w]) w++;

                int h = 1;
                grow:
                while (r + h < rows) {
                    String next = SPRITE[r + h];
                    if (next.length() < c + w) break;
                    for (int k = 0; k < w; k++) {
                        if (next.charAt(c + k) != ch || used[r + h][c + k]) break grow;
                    }
                    h++;
                }

                for (int dr = 0; dr < h; dr++) {
                    for (int dc = 0; dc < w; dc++) used[r + dr][c + dc] = true;
                }
                SPRITE_RECTS.add(new int[]{c, r, w, h, ch - 'A'});
            }
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgNotify = settings.createGroup("Notify");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ---- General ----
    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Scan loaded chunks within this many chunks of you.")
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
        .defaultValue(64)
        .range(-64, 320)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> sweepTicks = sgGeneral.add(new IntSetting.Builder()
        .name("sweep-ticks")
        .description("Start a new sweep over the loaded chunks every N ticks.")
        .defaultValue(20)
        .range(1, 200)
        .sliderRange(5, 100)
        .build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("ms-per-tick")
        .description("Scan time budget per tick in milliseconds.")
        .defaultValue(5)
        .range(1, 40)
        .sliderRange(1, 40)
        .build());

    private final Setting<Boolean> overworldOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("overworld-only")
        .description("Only scan in the overworld (geodes do not spawn elsewhere).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print bud counts in chat when a chunk changes.")
        .defaultValue(false)
        .build());

    // ---- Filter ----
    private final Setting<Integer> minimum = sgFilter.add(new IntSetting.Builder()
        .name("min-buds")
        .description("Bud light sources needed to mark a chunk.")
        .defaultValue(8)
        .range(1, 200)
        .sliderRange(1, 60)
        .build());

    private final Setting<Integer> maxBuds = sgFilter.add(new IntSetting.Builder()
        .name("max-buds")
        .description("A chunk reporting more sources than this is treated as noise and ignored.")
        .defaultValue(150)
        .range(10, 1000)
        .sliderRange(20, 400)
        .build());

    private final Setting<Integer> confirmSweeps = sgFilter.add(new IntSetting.Builder()
        .name("confirm-sweeps")
        .description("A chunk must qualify on this many consecutive sweeps before it is drawn.")
        .defaultValue(2)
        .range(1, 6)
        .sliderRange(1, 6)
        .build());

    private final Setting<Boolean> undergroundOnly = sgFilter.add(new BoolSetting.Builder()
        .name("underground-only")
        .description("Ignore positions that receive any sky light.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> checkFalloff = sgFilter.add(new BoolSetting.Builder()
        .name("check-falloff")
        .description("Emitters with light >= 2 must have a neighbour at exactly light-1.")
        .defaultValue(true)
        .build());

    // ---- Notify ----
    private final Setting<Boolean> chatNotify = sgNotify.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Announce a newly confirmed geode once per chunk (coords + distance + direction).")
        .defaultValue(true)
        .build());

    // ---- Render ----
    private final Setting<Integer> markerY = sgRender.add(new IntSetting.Builder()
        .name("marker-y")
        .description("Y level of the chunk marker when auto-y is off.")
        .defaultValue(64)
        .range(-64, 320)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Boolean> autoY = sgRender.add(new BoolSetting.Builder()
        .name("auto-y")
        .description("Draw the marker at the average Y of the light sources instead of marker-y.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> spriteMarker = sgRender.add(new BoolSetting.Builder()
        .name("sprite-marker")
        .description("Draw the amethyst cluster pixel art on the chunk floor.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> hud = sgRender.add(new BoolSetting.Builder()
        .name("hud-overlay")
        .description("Shows real-time status and found geode count on screen.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> hudX = sgRender.add(new IntSetting.Builder()
        .name("hud-x")
        .description("HUD horizontal offset.")
        .defaultValue(10)
        .range(0, 3000)
        .sliderRange(0, 800)
        .build());

    private final Setting<Integer> hudY = sgRender.add(new IntSetting.Builder()
        .name("hud-y")
        .description("HUD vertical offset.")
        .defaultValue(10)
        .range(0, 2000)
        .sliderRange(0, 800)
        .build());

    private final Setting<SettingColor> hudColor = sgRender.add(new ColorSetting.Builder()
        .name("hud-color")
        .description("HUD text color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Marker fill color.")
        .defaultValue(new SettingColor(170, 0, 255, 55))
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Marker outline color.")
        .defaultValue(new SettingColor(255, 80, 255, 180))
        .build());

    private final Setting<Boolean> showGrowthArea = sgRender.add(new BoolSetting.Builder()
        .name("show-growth-area")
        .description("Also mark the full square of chunks around a found chunk.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> growthRadius = sgRender.add(new IntSetting.Builder()
        .name("growth-radius")
        .description("Radius in chunks.")
        .defaultValue(1)
        .range(1, 16)
        .sliderRange(1, 10)
        .build());

    private final Setting<SettingColor> growthSideColor = sgRender.add(new ColorSetting.Builder()
        .name("growth-side-color")
        .defaultValue(new SettingColor(255, 150, 220, 25))
        .build());

    private final Setting<SettingColor> growthLineColor = sgRender.add(new ColorSetting.Builder()
        .name("growth-line-color")
        .defaultValue(new SettingColor(255, 180, 230, 90))
        .build());

    private final Setting<Boolean> tracer = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .description("Draw a line from your eyes to the nearest confirmed geode.")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .defaultValue(new SettingColor(255, 80, 255, 200))
        .build());

    private final Setting<Boolean> hideVisited = sgRender.add(new BoolSetting.Builder()
        .name("hide-visited")
        .description("Stop drawing geodes you have already walked up to.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> visitedRadius = sgRender.add(new IntSetting.Builder()
        .name("visited-radius")
        .description("Distance in blocks from the chunk center that counts as visited.")
        .defaultValue(20)
        .range(4, 64)
        .sliderRange(4, 48)
        .build());

    /** Bir chunk'ın tarama durumu. */
    private static final class ChunkState {
        int count;
        int confirms;
        int ySum;
        int emitters;
        boolean visited;

        int avgYOr(int fallback) {
            return emitters > 0 ? ySum / emitters : fallback;
        }
    }

    private final Map<Long, ChunkState> chunks = new HashMap<>();
    private final Map<Long, Integer> quiet = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    private final List<int[]> queue = new ArrayList<>();

    // Aynı tick içinde aynı section verisi tekrar tekrar istenmesin diye küçük önbellek.
    private final Map<Long, DataLayer> blockLayers = new HashMap<>();
    private final Map<Long, DataLayer> skyLayers = new HashMap<>();
    private LayerLightEventListener blockLight;
    private LayerLightEventListener skyLight;

    private Level lastLevel;
    private int cursor;
    private int sinceSweep;
    private int sweepIndex;
    private int queueSignature;
    private int visitedTimer;

    private int scanSumY;
    private int scanEmitters;
    private double emaScanMs;

    private final List<int[]> markerCache = new ArrayList<>();
    private long[] growthCache = new long[0];
    private int dataVersion;
    private int cacheVersion = -1;
    private int cacheSignature;

    public ClusterBypass() {
        super(AddonTemplate.CATEGORY, "cluster-bypass", "Marks chunks when block-light bud sources reach the configured minimum.");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }

    private static int keyX(long k) {
        return (int) (k >> 32);
    }

    private static int keyZ(long k) {
        return (int) k;
    }

    @Override
    public void onActivate() {
        reset();
        lastLevel = null;
    }

    @Override
    public void onDeactivate() {
        reset();
        lastLevel = null;
    }

    private void reset() {
        chunks.clear();
        quiet.clear();
        announced.clear();
        queue.clear();
        markerCache.clear();
        blockLayers.clear();
        skyLayers.clear();
        growthCache = new long[0];
        cursor = 0;
        sweepIndex = 0;
        queueSignature = 0;
        visitedTimer = 0;
        scanSumY = 0;
        scanEmitters = 0;
        emaScanMs = 0.0;
        sinceSweep = Integer.MAX_VALUE / 2;
        dataVersion++;
    }

    // ------------------------------------------------------------------ tick

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }

        if (overworldOnly.get() && mc.level.dimension() != Level.OVERWORLD) {
            if (!queue.isEmpty()) queue.clear();
            return;
        }

        blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        skyLight = level.getLightEngine().getLayerListener(LightLayer.SKY);

        if (visitedTimer-- <= 0) {
            visitedTimer = 10; // saniyede 2 kez yeterli
            updateVisited();
        }

        // Ayar değiştiyse kuyruğu beklemeden baştan kur.
        int signature = queueSignatureOf();
        if (signature != queueSignature) {
            queueSignature = signature;
            sinceSweep = Integer.MAX_VALUE / 2;
            cursor = queue.size();
        }

        sinceSweep++;
        if (cursor >= queue.size()) {
            if (sinceSweep < sweepTicks.get()) return;
            sinceSweep = 0;
            rebuildQueue(level);
            if (queue.isEmpty()) return;
        }

        int bottom = level.getMinY();
        int top = level.getMaxY() + 1;
        int limit = maxBuds.get();
        int confirm = confirmSweeps.get();
        int needed = minimum.get();
        int fallbackY = markerY.get();

        blockLayers.clear();
        skyLayers.clear();

        long start = System.nanoTime();
        long deadline = start + msPerTick.get() * 1_000_000L;

        while (cursor < queue.size() && System.nanoTime() < deadline) {
            int[] c = queue.get(cursor++);
            if (!level.hasChunk(c[0], c[1])) continue;

            long k = key(c[0], c[1]);

            scanSumY = 0;
            scanEmitters = 0;
            int found = scanChunk(level, c[0], c[1], bottom, top);

            if (found > limit) {
                if (debug.get()) info("chunk %d,%d: %d sources, ignored as noise", c[0], c[1], found);
                found = 0;
            }

            ChunkState state = chunks.get(k);

            if (found == 0) {
                quiet.merge(k, 1, (a, b) -> Math.min(a + b, 1000));
                if (state != null) {
                    chunks.remove(k);
                    dataVersion++;
                }
                continue;
            }

            quiet.remove(k);

            if (state == null) {
                state = new ChunkState();
                chunks.put(k, state);
                dataVersion++;
            }

            int oldCount = state.count;
            state.count = found;
            if (state.confirms < 100) state.confirms++;
            state.ySum = scanSumY;
            state.emitters = scanEmitters;

            if (found != oldCount || state.confirms == confirm) dataVersion++;

            if (debug.get() && found != oldCount) {
                info("chunk %d,%d: buds=%d (y~%d)", c[0], c[1], found, state.avgYOr(fallbackY));
            }

            // Yeni onaylanan geode -> tek seferlik bildirim.
            if (chatNotify.get() && found >= needed && state.confirms >= confirm && announced.add(k)) {
                notifyFound(c[0], c[1], state.avgYOr(fallbackY));
            }
        }

        double ms = (System.nanoTime() - start) / 1_000_000.0;
        emaScanMs = emaScanMs <= 0.0 ? ms : emaScanMs * 0.8 + ms * 0.2;
    }

    private int queueSignatureOf() {
        return ((chunkRange.get() * 31 + minY.get()) * 31 + maxY.get()) * 31 + sweepTicks.get();
    }

    private void notifyFound(int cx, int cz, int y) {
        int centerX = (cx << 4) + 8;
        int centerZ = (cz << 4) + 8;

        double dx = centerX + 0.5 - mc.player.getX();
        double dy = y + 0.5 - mc.player.getY();
        double dz = centerZ + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));

        info("Geode: %d, %d, %d (%dm %s)", centerX, y, centerZ, dist, compass(dx, dz));
    }

    private static String compass(double dx, double dz) {
        double yaw = -Math.toDegrees(Math.atan2(dx, dz));
        yaw = (yaw % 360.0 + 360.0) % 360.0;
        int i = (int) Math.round(yaw / 45.0) % 8;
        return COMPASS[i];
    }

    /**
     * Oyuncunun gerçekten yanına geldiği geodeları işaretle (bir daha çizilmesin).
     * Mesafe 3B hesaplanır: yüzeyde üstünden geçmek "ziyaret" saymaz.
     */
    private void updateVisited() {
        if (!hideVisited.get()) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double rSq = (double) visitedRadius.get() * visitedRadius.get();

        int min = minimum.get();
        int confirm = confirmSweeps.get();
        int fallbackY = markerY.get();
        boolean changed = false;

        for (Map.Entry<Long, ChunkState> e : chunks.entrySet()) {
            ChunkState s = e.getValue();
            if (s.visited || s.count < min || s.confirms < confirm) continue;

            double dx = (keyX(e.getKey()) * 16.0 + 8.0) - px;
            double dy = s.avgYOr(fallbackY) - py;
            double dz = (keyZ(e.getKey()) * 16.0 + 8.0) - pz;

            if (dx * dx + dy * dy + dz * dz <= rSq) {
                s.visited = true;
                changed = true;
            }
        }

        if (changed) dataVersion++;
    }

    // ------------------------------------------------------------------ scan

    private void rebuildQueue(Level level) {
        queue.clear();
        cursor = 0;
        sweepIndex++;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();
        boolean coldDue = sweepIndex % COLD_PERIOD == 0;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!level.hasChunk(cx, cz)) continue;

                Integer q = quiet.get(key(cx, cz));
                if (q != null && q >= QUIET_FULL_SCANS && !coldDue) continue;

                int distSq = dx * dx + dz * dz;
                // Jitter önceden hesaplanır: comparator sözleşmesi bozulmaz.
                int jitteredDist = distSq + random.nextInt(4);
                queue.add(new int[]{cx, cz, jitteredDist});
            }
        }

        queue.sort(Comparator.comparingInt(a -> a[2]));

        boolean removed = chunks.keySet().removeIf(k -> isStale(level, k, pcx, pcz, r));
        quiet.keySet().removeIf(k -> isStale(level, k, pcx, pcz, r));
        if (removed) dataVersion++;
    }

    private static boolean isStale(Level level, long k, int pcx, int pcz, int r) {
        int cx = keyX(k);
        int cz = keyZ(k);
        return Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r || !level.hasChunk(cx, cz);
    }

    private int scanChunk(Level level, int cx, int cz, int bottom, int top) {
        int lo = Math.max(minY.get(), bottom);
        int hi = Math.min(maxY.get(), top - 1);
        if (lo > hi) return 0;

        boolean underground = undergroundOnly.get();
        boolean falloff = checkFalloff.get();

        int bx = cx << 4;
        int bz = cz << 4;
        int count = 0;

        for (int sy = lo >> 4; sy <= hi >> 4; sy++) {
            DataLayer arr = layer(blockLayers, blockLight, cx, sy, cz);
            if (arr == null) continue; // boş / yüklenmemiş section

            int yStart = Math.max(lo, sy << 4);
            int yEnd = Math.min(hi, (sy << 4) + 15);

            for (int y = yStart; y <= yEnd; y++) {
                int ly = y & 15;

                for (int x = 0; x < 16; x++) {
                    int wx = bx + x;

                    for (int z = 0; z < 16; z++) {
                        int light = arr.get(x, ly, z);
                        if (light < 1 || light > MAX_BUD_LIGHT) continue;
                        if (((BUD_LIGHT_MASK >> light) & 1) == 0) continue;

                        int wz = bz + z;
                        if (underground && skyLightAt(wx, y, wz) > 0) continue;
                        if (!isEmitter(wx, y, wz, light, bottom, top, falloff)) continue;

                        count++;
                        scanSumY += y;
                        scanEmitters++;
                    }
                }
            }
        }

        return count;
    }

    private boolean isEmitter(int x, int y, int z, int light, int bottom, int top, boolean falloff) {
        boolean hasFalloff = light < 2;

        for (Direction dir : DIRS) {
            int ny = y + dir.getStepY();
            if (ny < bottom || ny >= top) continue;

            int nl = blockLightAt(x + dir.getStepX(), ny, z + dir.getStepZ());
            if (nl < 0) continue;          // komşu section yok: eleme yapma
            if (nl > light) return false;  // daha parlak komşu: kaynak değil
            if (nl == light - 1) hasFalloff = true;
        }

        return !falloff || hasFalloff;
    }

    // ------------------------------------------------------------------ light

    /**
     * Section'ın ışık DataLayer'ını önbellekten döndürür.
     * null = section yok veya tamamen boş (o bölgede ışık yok sayılır).
     */
    private DataLayer layer(Map<Long, DataLayer> cache, LayerLightEventListener listener, int cx, int sy, int cz) {
        long k = SectionPos.asLong(cx, sy, cz);
        if (cache.containsKey(k)) return cache.get(k);

        DataLayer arr = listener == null ? null : listener.getDataLayerData(SectionPos.of(k));
        if (arr != null && arr.isEmpty()) arr = null;

        cache.put(k, arr);
        return arr;
    }

    private int blockLightAt(int x, int y, int z) {
        DataLayer arr = layer(blockLayers, blockLight, x >> 4, y >> 4, z >> 4);
        return arr == null ? -1 : arr.get(x & 15, y & 15, z & 15);
    }

    private int skyLightAt(int x, int y, int z) {
        DataLayer arr = layer(skyLayers, skyLight, x >> 4, y >> 4, z >> 4);
        return arr == null ? 0 : arr.get(x & 15, y & 15, z & 15);
    }

    // ------------------------------------------------------------------ cache

    private void refreshCache() {
        int min = minimum.get();
        int confirm = confirmSweeps.get();
        int gr = growthRadius.get();
        boolean growth = showGrowthArea.get();
        boolean skipVisited = hideVisited.get();
        int fallbackY = markerY.get();

        int signature = min;
        signature = signature * 31 + confirm;
        signature = signature * 31 + gr;
        signature = signature * 31 + fallbackY;
        signature = signature * 2 + (growth ? 1 : 0);
        signature = signature * 2 + (skipVisited ? 1 : 0);

        if (cacheVersion == dataVersion && cacheSignature == signature) return;
        cacheVersion = dataVersion;
        cacheSignature = signature;

        markerCache.clear();
        Set<Long> area = new HashSet<>();

        for (Map.Entry<Long, ChunkState> e : chunks.entrySet()) {
            ChunkState s = e.getValue();
            if (s.count < min || s.confirms < confirm) continue;
            if (skipVisited && s.visited) continue;

            int cx = keyX(e.getKey());
            int cz = keyZ(e.getKey());
            markerCache.add(new int[]{cx, cz, s.avgYOr(fallbackY)});

            if (growth) {
                for (int dx = -gr; dx <= gr; dx++) {
                    for (int dz = -gr; dz <= gr; dz++) area.add(key(cx + dx, cz + dz));
                }
            }
        }

        growthCache = new long[area.size()];
        int i = 0;
        for (long k : area) growthCache[i++] = k;
    }

    private int[] nearestMarker() {
        int[] best = null;
        double bestDist = Double.MAX_VALUE;

        for (int[] m : markerCache) {
            double dx = m[0] * 16.0 + 8.0 - mc.player.getX();
            double dz = m[1] * 16.0 + 8.0 - mc.player.getZ();
            double d = dx * dx + dz * dz;

            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }

        return best;
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || mc.level != lastLevel || chunks.isEmpty()) return;

        refreshCache();
        if (markerCache.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();
        double baseY = markerY.get();

        if (showGrowthArea.get()) {
            int reach = r + growthRadius.get();
            Color side = growthSideColor.get();
            Color line = growthLineColor.get();

            for (long k : growthCache) {
                int cx = keyX(k);
                int cz = keyZ(k);
                if (Math.abs(cx - pcx) > reach || Math.abs(cz - pcz) > reach) continue;

                double x = cx * 16.0;
                double z = cz * 16.0;
                event.renderer.box(new AABB(x, baseY - 0.05, z, x + 16.0, baseY + 0.25, z + 16.0),
                    side, line, ShapeMode.Both, 0);
            }
        }

        Color side = sideColor.get();
        Color line = lineColor.get();
        boolean sprite = spriteMarker.get();
        boolean dynamicY = autoY.get();

        for (int[] m : markerCache) {
            if (Math.abs(m[0] - pcx) > r || Math.abs(m[1] - pcz) > r) continue;

            double x = m[0] * 16.0;
            double z = m[1] * 16.0;
            double y = dynamicY ? m[2] : baseY;

            if (sprite) {
                event.renderer.box(new AABB(x, y, z, x + 16.0, y + 0.5, z + 16.0), side, line, ShapeMode.Lines, 0);
                renderSprite(event, x, y, z);
            } else {
                event.renderer.box(new AABB(x, y, z, x + 16.0, y + 0.5, z + 16.0), side, line, ShapeMode.Both, 0);
            }
        }

        if (tracer.get()) {
            int[] nearest = nearestMarker();

            if (nearest != null) {
                event.renderer.line(
                    mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                    nearest[0] * 16.0 + 8.0, nearest[2] + 0.5, nearest[1] * 16.0 + 8.0,
                    tracerColor.get());
            }
        }
    }

    private void renderSprite(Render3DEvent event, double bx, double y, double bz) {
        for (int[] rect : SPRITE_RECTS) {
            Color c = SPRITE_COLORS[rect[4]];
            double x = bx + rect[0];
            double z = bz + rect[1];
            event.renderer.box(new AABB(x, y, z, x + rect[2], y + 0.5, z + rect[3]),
                c, c, ShapeMode.Sides, 0);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!hud.get() || mc.level == null || mc.player == null) return;

        refreshCache();

        double x = hudX.get();
        double y = hudY.get();
        Color color = hudColor.get();

        int[] nearest = nearestMarker();
        String near = "nearest: -";

        if (nearest != null) {
            double dx = nearest[0] * 16.0 + 8.0 - mc.player.getX();
            double dz = nearest[1] * 16.0 + 8.0 - mc.player.getZ();
            near = String.format("nearest: %dm %s", (int) Math.round(Math.sqrt(dx * dx + dz * dz)), compass(dx, dz));
        }

        int pct = queue.isEmpty() ? 100 : cursor * 100 / queue.size();

        TextRenderer renderer = TextRenderer.get();
        renderer.begin(1.0);
        renderer.render(String.format("ClusterBypass v6 | Geodes: %d", markerCache.size()), x, y, color);
        renderer.render(
            String.format("sweep %d%% (%d/%d) | %s | %.1fms", pct, cursor, queue.size(), near, emaScanMs),
            x, y + renderer.getHeight(), color);
        renderer.end();
    }
}
