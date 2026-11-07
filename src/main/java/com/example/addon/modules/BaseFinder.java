package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Base Finder - base / stash / farm avi icin blok ve entity izleri.
 *
 * Anti-xray sadece hidden-blocks listesindeki bloklari gizler. Ciftlik bloklari
 * (pumpkin, melon, sugar cane, nether wart...), is istasyonlari, isik bloklari,
 * villagerlar ve hayvanlar genelde o listede YOKTUR; bu yuzden base avinda
 * en guvenilir izler bunlar.
 *
 * Her kategori ayri acilip kapanabilir. Ayrica chunk bazli isi haritasi var:
 * bir chunk'ta yeterince iz varsa chunk'in tamami isaretlenir.
 */
public class BaseFinder extends Module {
    private static final int C_FARM = 0;
    private static final int C_VILLAGER = 1;
    private static final int C_WORK = 2;
    private static final int C_STORAGE = 3;
    private static final int C_FUNCTIONAL = 4;
    private static final int C_REDSTONE = 5;
    private static final int C_LIGHT = 6;
    private static final int C_PORTAL = 7;
    private static final int C_MANMADE = 8;
    private static final int C_ENTITY = 9;

    private static final String[] NAMES = {
        "farm", "villager", "workstation", "storage", "functional",
        "redstone", "light", "portal", "man-made", "entity"
    };

    private static final Color[] PALETTE = {
        new Color(120, 220, 90, 255),
        new Color(255, 220, 90, 255),
        new Color(255, 160, 60, 255),
        new Color(255, 120, 40, 255),
        new Color(90, 220, 255, 255),
        new Color(230, 90, 90, 255),
        new Color(240, 240, 240, 255),
        new Color(160, 120, 255, 255),
        new Color(150, 150, 160, 255),
        new Color(120, 255, 220, 255)
    };

    private record Mark(BlockPos pos, int category) {
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgEntities = settings.createGroup("Entities");
    private final SettingGroup sgChunk = settings.createGroup("Chunk Alert");

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Scan radius in chunks.")
        .defaultValue(12)
        .range(1, 32)
        .sliderRange(1, 32)
        .build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build());

    private final Setting<Integer> msPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("ms-per-tick")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 40)
        .build());

    private final Setting<Integer> sweepTicks = sgGeneral.add(new IntSetting.Builder()
        .name("sweep-ticks")
        .defaultValue(40)
        .range(1, 200)
        .sliderRange(5, 100)
        .build());

    private final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .defaultValue(0)
        .range(0, 255)
        .sliderRange(0, 120)
        .build());

    // ---- kategori anahtarlari ----
    private final Setting<Boolean> farms = sgBlocks.add(new BoolSetting.Builder()
        .name("farms")
        .description("Pumpkin, melon, stems, sugar cane, cactus, bamboo, kelp, nether wart, cocoa, sweet berry, farmland.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> villagers = sgEntities.add(new BoolSetting.Builder()
        .name("villagers")
        .description("Villagers, wandering traders, iron and snow golems, allays.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> workstations = sgBlocks.add(new BoolSetting.Builder()
        .name("workstations")
        .description("Composter, lectern, smithing/fletching/cartography table, loom, stonecutter, grindstone, bell, beds.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> storage = sgBlocks.add(new BoolSetting.Builder()
        .name("storage")
        .description("Chests, barrels, shulker boxes, hoppers, furnaces, dispensers, crafters.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> functional = sgBlocks.add(new BoolSetting.Builder()
        .name("functional")
        .description("Beacon, lodestone, enchanting table, anvil, bookshelf, jukebox, note block, conduit, netherite block.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> redstone = sgBlocks.add(new BoolSetting.Builder()
        .name("redstone")
        .description("Observer, pistons, hoppers, repeaters, comparators, redstone blocks and lamps. Farm machinery.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> lights = sgBlocks.add(new BoolSetting.Builder()
        .name("lights")
        .description("Torches, lanterns, glowstone, sea lantern, shroomlight, end rods, campfires. Underground base tell.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> portals = sgBlocks.add(new BoolSetting.Builder()
        .name("portals")
        .description("Obsidian, crying obsidian, nether portals, respawn anchors.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> manMade = sgBlocks.add(new BoolSetting.Builder()
        .name("man-made")
        .description("Anything that is not natural terrain. Noisy but catches builds the other categories miss.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> animals = sgEntities.add(new BoolSetting.Builder()
        .name("animals")
        .description("Cows, sheep, pigs, chickens, horses: animal pens and farms.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> playerThings = sgEntities.add(new BoolSetting.Builder()
        .name("player-objects")
        .description("Armor stands, item frames, chest minecarts, chest boats: signs of a player base.")
        .defaultValue(true)
        .build());

    // ---- chunk alert ----
    private final Setting<Boolean> chunkAlert = sgChunk.add(new BoolSetting.Builder()
        .name("chunk-alert")
        .description("Highlight a whole chunk when it holds enough signals.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> alertThreshold = sgChunk.add(new IntSetting.Builder()
        .name("alert-threshold")
        .description("Signals needed in one chunk to mark it as a base.")
        .defaultValue(6)
        .range(2, 200)
        .sliderRange(2, 60)
        .build());

    private final Setting<Boolean> alertChat = sgChunk.add(new BoolSetting.Builder()
        .name("alert-chat")
        .description("Print chunk coordinates once when a chunk starts looking like a base.")
        .defaultValue(true)
        .build());

    private final Map<Long, List<Mark>> cache = new HashMap<>();
    private final Map<Long, Integer> chunkScore = new HashMap<>();
    private final List<Mark> entityMarks = new ArrayList<>();
    private final Set<Long> announced = new HashSet<>();
    private final List<int[]> queue = new ArrayList<>();

    private int cursor;
    private int sinceSweep = Integer.MAX_VALUE / 2;
    private Object lastLevel;

    private static final int[] COUNTS = new int[NAMES.length];

    public BaseFinder() {
        super(AddonTemplate.CATEGORY, "base-finder",
            "Finds bases, stashes and farms by farm blocks, villagers, workstations and chunk heat.");
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

    private boolean on(int category) {
        return switch (category) {
            case C_FARM -> farms.get();
            case C_WORK -> workstations.get();
            case C_STORAGE -> storage.get();
            case C_FUNCTIONAL -> functional.get();
            case C_REDSTONE -> redstone.get();
            case C_LIGHT -> lights.get();
            case C_PORTAL -> portals.get();
            case C_MANMADE -> manMade.get();
            default -> false;
        };
    }

    /** Blok kategorisi. -1 = ilgilenmiyoruz. */
    private int category(BlockState st) {
        if (on(C_PORTAL)) {
            if (st.is(Blocks.OBSIDIAN) || st.is(Blocks.CRYING_OBSIDIAN) || st.is(Blocks.NETHER_PORTAL)
                || st.is(Blocks.RESPAWN_ANCHOR)) return C_PORTAL;
        }

        if (on(C_FARM)) {
            if (st.is(Blocks.PUMPKIN) || st.is(Blocks.CARVED_PUMPKIN) || st.is(Blocks.JACK_O_LANTERN)
                || st.is(Blocks.MELON) || st.is(Blocks.PUMPKIN_STEM) || st.is(Blocks.MELON_STEM)
                || st.is(Blocks.ATTACHED_PUMPKIN_STEM) || st.is(Blocks.ATTACHED_MELON_STEM)
                || st.is(Blocks.SUGAR_CANE) || st.is(Blocks.CACTUS) || st.is(Blocks.BAMBOO)
                || st.is(Blocks.KELP) || st.is(Blocks.KELP_PLANT) || st.is(Blocks.SEA_PICKLE)
                || st.is(Blocks.NETHER_WART) || st.is(Blocks.COCOA) || st.is(Blocks.SWEET_BERRY_BUSH)
                || st.is(Blocks.FARMLAND) || st.is(Blocks.WHEAT) || st.is(Blocks.CARROTS)
                || st.is(Blocks.POTATOES) || st.is(Blocks.BEETROOTS)) {
                return C_FARM;
            }
        }

        if (on(C_WORK)) {
            if (st.is(Blocks.COMPOSTER) || st.is(Blocks.LECTERN) || st.is(Blocks.SMITHING_TABLE)
                || st.is(Blocks.FLETCHING_TABLE) || st.is(Blocks.CARTOGRAPHY_TABLE) || st.is(Blocks.LOOM)
                || st.is(Blocks.STONECUTTER) || st.is(Blocks.GRINDSTONE) || st.is(Blocks.BELL)
                || st.is(BlockTags.BEDS)) {
                return C_WORK;
            }
        }

        if (on(C_STORAGE)) {
            if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST) || st.is(Blocks.BARREL)
                || st.is(Blocks.ENDER_CHEST) || st.is(BlockTags.SHULKER_BOXES) || st.is(Blocks.HOPPER)
                || st.is(Blocks.FURNACE) || st.is(Blocks.BLAST_FURNACE) || st.is(Blocks.SMOKER)
                || st.is(Blocks.DISPENSER) || st.is(Blocks.DROPPER) || st.is(Blocks.CRAFTER)) {
                return C_STORAGE;
            }
        }

        if (on(C_FUNCTIONAL)) {
            if (st.is(Blocks.BEACON) || st.is(Blocks.LODESTONE) || st.is(Blocks.ENCHANTING_TABLE)
                || st.is(Blocks.ANVIL) || st.is(Blocks.CHIPPED_ANVIL) || st.is(Blocks.DAMAGED_ANVIL)
                || st.is(Blocks.BOOKSHELF) || st.is(Blocks.CHISELED_BOOKSHELF) || st.is(Blocks.JUKEBOX)
                || st.is(Blocks.NOTE_BLOCK) || st.is(Blocks.CONDUIT) || st.is(Blocks.NETHERITE_BLOCK)
                || st.is(Blocks.DRAGON_EGG) || st.is(Blocks.DECORATED_POT)) {
                return C_FUNCTIONAL;
            }
        }

        if (on(C_REDSTONE)) {
            if (st.is(Blocks.OBSERVER) || st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON)
                || st.is(Blocks.REPEATER) || st.is(Blocks.COMPARATOR) || st.is(Blocks.DAYLIGHT_DETECTOR)
                || st.is(Blocks.TARGET) || st.is(Blocks.TRIPWIRE_HOOK) || st.is(Blocks.LEVER)
                || st.is(Blocks.REDSTONE_BLOCK) || st.is(Blocks.REDSTONE_LAMP)
                || st.is(Blocks.REDSTONE_TORCH) || st.is(Blocks.REDSTONE_WIRE)) {
                return C_REDSTONE;
            }
        }

        if (on(C_LIGHT)) {
            if (st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH) || st.is(Blocks.SOUL_TORCH)
                || st.is(Blocks.SOUL_WALL_TORCH) || st.is(Blocks.LANTERN) || st.is(Blocks.SOUL_LANTERN)
                || st.is(Blocks.GLOWSTONE) || st.is(Blocks.SEA_LANTERN) || st.is(Blocks.SHROOMLIGHT)
                || st.is(Blocks.END_ROD) || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE)) {
                return C_LIGHT;
            }
        }

        if (on(C_MANMADE) && !isNatural(st)) {
            return C_MANMADE;
        }

        return -1;
    }

    /** Kaba dogal blok filtresi: bunlar man-made sayilmaz. */
    private static boolean isNatural(BlockState st) {
        Block b = st.getBlock();

        if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR || b == Blocks.WATER
            || b == Blocks.LAVA || b == Blocks.BEDROCK) return true;

        if (st.is(Blocks.STONE) || st.is(Blocks.DEEPSLATE) || st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK)
            || st.is(Blocks.SAND) || st.is(Blocks.GRAVEL) || st.is(Blocks.CLAY) || st.is(Blocks.SNOW)
            || st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE) || st.is(Blocks.BLUE_ICE)
            || st.is(Blocks.ANDESITE) || st.is(Blocks.DIORITE) || st.is(Blocks.GRANITE)
            || st.is(Blocks.TUFF) || st.is(Blocks.CALCITE) || st.is(Blocks.DRIPSTONE_BLOCK)
            || st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.MYCELIUM) || st.is(Blocks.PODZOL)
            || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SAND) || st.is(Blocks.RED_SANDSTONE)
            || st.is(Blocks.TERRACOTTA) || st.is(Blocks.MUD) || st.is(Blocks.MUDDY_MANGROVE_ROOTS)) {
            return true;
        }

        if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)
            || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
            || st.is(BlockTags.BASE_STONE_NETHER)) {
            return true;
        }

        String name = b.getDescriptionId();

        if (name.contains("_ore")) return true;

        return name.contains("mushroom") || name.contains("coral") || name.contains("flower")
            || name.contains("grass") || name.contains("fern") || name.contains("vine")
            || name.contains("sapling") || name.contains("azalea") || name.contains("lichen")
            || name.contains("wart_block") || name.contains("amethyst") || name.contains("sculk")
            || name.contains("deepslate") || name.contains("tuff");
    }

    private int entityCategory(Entity e) {
        EntityType<?> type = e.getType();

        if (villagers.get()) {
            if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER
                || type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM
                || type == EntityType.ALLAY || type == EntityType.ZOMBIE_VILLAGER) {
                return C_VILLAGER;
            }
        }

        if (animals.get()) {
            if (type == EntityType.COW || type == EntityType.SHEEP || type == EntityType.PIG
                || type == EntityType.CHICKEN || type == EntityType.RABBIT || type == EntityType.HORSE
                || type == EntityType.DONKEY || type == EntityType.MULE || type == EntityType.LLAMA
                || type == EntityType.TRADER_LLAMA || type == EntityType.MOOSHROOM
                || type == EntityType.BEE || type == EntityType.TURTLE || type == EntityType.FOX
                || type == EntityType.GOAT || type == EntityType.CAMEL) {
                return C_ENTITY;
            }
        }

        if (playerThings.get()) {
            if (type == EntityType.ARMOR_STAND || type == EntityType.ITEM_FRAME
                || type == EntityType.GLOW_ITEM_FRAME || type == EntityType.PAINTING
                || type == EntityType.CHEST_MINECART || type == EntityType.HOPPER_MINECART
                || type == EntityType.LEASH_KNOT || type == EntityType.CAT || type == EntityType.WOLF
                || type == EntityType.PARROT || e instanceof Boat) {
                return C_ENTITY;
            }
        }

        return -1;
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        cache.clear();
        chunkScore.clear();
        entityMarks.clear();
        announced.clear();
        queue.clear();
        cursor = 0;
        sinceSweep = Integer.MAX_VALUE / 2;
        lastLevel = null;
        java.util.Arrays.fill(COUNTS, 0);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        if (lastLevel != mc.level) {
            lastLevel = mc.level;
            cache.clear();
            chunkScore.clear();
            announced.clear();
            entityMarks.clear();
        }

        scanEntities();

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

            if (queue.isEmpty()) return;
        }

        long deadline = System.nanoTime() + msPerTick.get() * 1_000_000L;

        while (cursor < queue.size() && System.nanoTime() < deadline) {
            int[] c = queue.get(cursor++);
            if (!mc.level.hasChunk(c[0], c[1])) continue;
            scanChunk(c[0], c[1]);
        }
    }

    private void scanEntities() {
        entityMarks.clear();

        if (!villagers.get() && !animals.get() && !playerThings.get()) return;

        int range = chunkRange.get() * 16;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double rangeSq = (double) range * range;

        for (Entity e : mc.level.entitiesForRendering()) {
            double dx = e.getX() - px;
            double dy = e.getY() - py;
            double dz = e.getZ() - pz;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            int c = entityCategory(e);
            if (c < 0) continue;

            entityMarks.add(new Mark(e.blockPosition(), c));
            bump(c);
        }
    }

    private void bump(int category) {
        COUNTS[category]++;
    }

    private void scanChunk(int cx, int cz) {
        LevelChunk chunk = mc.level.getChunk(cx, cz);

        List<Mark> marks = new ArrayList<>();
        long k = key(cx, cz);

        int lo = Math.max(minY.get(), mc.level.getMinY());
        int hi = Math.min(maxY.get(), mc.level.getMaxY() - 1);
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
                        int c = category(st);
                        if (c < 0) continue;

                        BlockPos pos = new BlockPos((cx << 4) + x, y, (cz << 4) + z);
                        marks.add(new Mark(pos, c));
                        bump(c);
                    }
                }
            }
        }

        if (marks.isEmpty()) {
            cache.remove(k);
            chunkScore.remove(k);
        } else {
            cache.put(k, marks);
            chunkScore.put(k, marks.size());
        }

        if (chunkAlert.get() && chunkScore.getOrDefault(k, 0) >= alertThreshold.get()
            && alertChat.get() && announced.add(k)) {
            info("Base signal chunk: %d, %d  (x %d z %d)", cx, cz, cx << 4, cz << 4);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int r = chunkRange.get();
        int op = fillOpacity.get();

        for (Map.Entry<Long, List<Mark>> e : cache.entrySet()) {
            if (Math.abs(keyX(e.getKey()) - pcx) > r || Math.abs(keyZ(e.getKey()) - pcz) > r) continue;

            for (Mark m : e.getValue()) {
                Color c = PALETTE[m.category()];
                Color side = new Color(c.r, c.g, c.b, op);

                event.renderer.box(new AABB(m.pos()), side, c, ShapeMode.Lines, 0);
            }
        }

        for (Mark m : entityMarks) {
            Color c = PALETTE[m.category()];
            Color side = new Color(c.r, c.g, c.b, op);

            event.renderer.box(new AABB(m.pos()).inflate(0.3), side, c, ShapeMode.Both, 0);
        }

        if (!chunkAlert.get()) return;

        int threshold = alertThreshold.get();
        Color alert = new Color(255, 80, 80, 220);

        for (Map.Entry<Long, Integer> e : chunkScore.entrySet()) {
            if (e.getValue() < threshold) continue;

            int cx = keyX(e.getKey());
            int cz = keyZ(e.getKey());
            if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) continue;

            double x1 = (cx << 4);
            double z1 = (cz << 4);

            event.renderer.box(x1, mc.level.getMinY(), z1, x1 + 16, mc.level.getMaxY(), z1 + 16,
                new Color(255, 80, 80, 12), new Color(alert.r, alert.g, alert.b, 90), ShapeMode.Lines, 0);
        }
    }
}
