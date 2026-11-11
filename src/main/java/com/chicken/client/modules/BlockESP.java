package com.chicken.client.modules;

import com.chicken.client.ChickenClient;
import com.chicken.client.core.Category;
import com.chicken.client.core.Module;
import com.chicken.client.render.Renderer3D;
import com.chicken.client.settings.DoubleSetting;
import com.chicken.client.settings.StringSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/** Highlights configured blocks around the player, rescanned once a second. */
public final class BlockESP extends Module {
    private static final String DEFAULT_BLOCKS = String.join(",",
        "minecraft:diamond_ore",
        "minecraft:deepslate_diamond_ore",
        "minecraft:ancient_debris",
        "minecraft:chest",
        "minecraft:trapped_chest",
        "minecraft:barrel",
        "minecraft:spawner");
    private static final int LINE_COLOR = 0xFF35E0FF;
    private static final int MAX_BLOCKS = 8000;

    private final DoubleSetting range = settings().add(
        new DoubleSetting("chunk-range", "Chunk radius that is scanned.", 4.0, 1.0, 10.0));
    private final StringSetting blocks = settings().add(
        new StringSetting("blocks", "Comma separated block ids, editable in the config file.", DEFAULT_BLOCKS));

    private final Map<Long, List<BlockPos>> cache = new HashMap<>();
    private int timer;

    public BlockESP() {
        super(Category.RENDER, "block-esp", "Highlights the configured blocks around you.");
    }

    @Override
    public void onActivate() {
        cache.clear();
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        cache.clear();
    }

    @Override
    public void onTick() {
        if (++timer < 20) return;
        timer = 0;
        scan();
    }

    private Set<String> targets() {
        Set<String> set = new HashSet<>();
        for (String raw : blocks.get().split(",")) {
            String id = raw.trim();
            if (!id.isEmpty()) set.add(id);
        }
        return set;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }

    private void scan() {
        Minecraft mc = ChickenClient.mc();
        cache.clear();
        if (mc.level == null || mc.player == null) return;

        Set<String> targets = targets();
        if (targets.isEmpty()) return;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int radius = (int) Math.round(range.get());
        int minY = mc.level.getMinY();
        int found = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!mc.level.hasChunk(cx, cz)) continue;

                LevelChunk chunk = mc.level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                List<BlockPos> positions = new ArrayList<>();

                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (!section.maybeHas(state -> targets.contains(idOf(state)))) continue;

                    int baseY = minY + (index << 4);
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!targets.contains(idOf(state))) continue;

                                positions.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
                                if (++found >= MAX_BLOCKS) {
                                    cache.put(key(cx, cz), positions);
                                    return;
                                }
                            }
                        }
                    }
                }

                if (!positions.isEmpty()) cache.put(key(cx, cz), positions);
            }
        }
    }

    private static String idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Called by ChickenRenderMixin while the level is drawn. */
    public void onRender3D(PoseStack pose) {
        if (cache.isEmpty()) return;

        Minecraft mc = ChickenClient.mc();
        if (mc.player == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double maxSq = 96.0 * 96.0;

        VertexConsumer consumer = Renderer3D.begin();
        try {
            for (List<BlockPos> positions : cache.values()) {
                for (BlockPos pos : positions) {
                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > maxSq) continue;

                    AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                    Renderer3D.box(pose, consumer, box, LINE_COLOR);
                }
            }
        } finally {
            Renderer3D.end();
        }
    }
}
