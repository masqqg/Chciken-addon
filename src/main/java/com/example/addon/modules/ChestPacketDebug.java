package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

/**
 * Chest Packet Debug - sunucudan chest / container ile ilgili gelen her paketi
 * aninda sohbete yazar, istenirse paketlerden bulunan sandiklari ekrana cizer.
 *
 * Not: Meteor calisirken sikistirilmis (intermediary) sinif isimleri gorunur,
 * yani "class_2744" gibi isimler normaldir. Bu modul onlari okunabilir etiketlere
 * cevirir: chunk-data, chunk-batch-start, be-data, open-screen ...
 */
public class ChestPacketDebug extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPackets = settings.createGroup("Packets");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> chestOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("chest-only")
        .description("Only report container block entities (chest, barrel, shulker, hopper, furnace...).")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxPerSecond = sgGeneral.add(new IntSetting.Builder()
        .name("max-per-second")
        .description("Chat spam guard. Extra lines are dropped.")
        .defaultValue(12)
        .range(1, 100)
        .sliderRange(1, 50)
        .build());

    private final Setting<Boolean> showNbt = sgGeneral.add(new BoolSetting.Builder()
        .name("show-nbt")
        .description("Print the beginning of the block entity NBT (Items list, id...).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logBlockEntity = sgPackets.add(new BoolSetting.Builder()
        .name("be-data")
        .description("Block entity data packets. This is the packet that carries a chest's contents.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logChunkBe = sgPackets.add(new BoolSetting.Builder()
        .name("chunk-be")
        .description("Block entities listed inside chunk packets.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logBlockEvent = sgPackets.add(new BoolSetting.Builder()
        .name("lid")
        .description("Block event packets: chest lid animation.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logLevelEvent = sgPackets.add(new BoolSetting.Builder()
        .name("level-event")
        .description("World event packets (block break effect 2001 and friends).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logSound = sgPackets.add(new BoolSetting.Builder()
        .name("sound")
        .description("Chest, barrel, shulker and ender chest sounds.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logScreen = sgPackets.add(new BoolSetting.Builder()
        .name("screen")
        .description("Container open screen packets.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logContainer = sgPackets.add(new BoolSetting.Builder()
        .name("container")
        .description("Container content / slot / close packets.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logChunkFlow = sgPackets.add(new BoolSetting.Builder()
        .name("chunk-flow")
        .description("Chunk data, chunk batch, light update and chunk unload packets.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> logAll = sgPackets.add(new BoolSetting.Builder()
        .name("all")
        .description("Print every packet type the server sends, once each, with a readable label.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a box on containers found in packets (chunk data + block entity data).")
        .defaultValue(true)
        .build());

    private final Setting<Integer> renderSeconds = sgRender.add(new IntSetting.Builder()
        .name("render-seconds")
        .description("How long a box stays on screen after the packet that revealed it.")
        .defaultValue(60)
        .range(5, 600)
        .sliderRange(5, 180)
        .build());

    private final ConcurrentLinkedQueue<String> out = new ConcurrentLinkedQueue<>();
    private final java.util.Set<String> seenTypes = new java.util.HashSet<>();
    private final Map<BlockPos, Long> boxes = new HashMap<>();

    private int windowSecond = -1;
    private int windowCount;

    private static final Color LINE = new Color(255, 170, 0, 255);
    private static final Color SIDE = new Color(255, 170, 0, 40);

    public ChestPacketDebug() {
        super(AddonTemplate.CATEGORY, "chest-packet-debug",
            "Prints every chest / container related packet the moment it arrives, and can draw what the packets reveal.");
    }

    @Override
    public void onActivate() {
        out.clear();
        seenTypes.clear();
        boxes.clear();
        windowSecond = -1;
        windowCount = 0;
        info("watching chest packets - open, break or look at a chest");
    }

    @Override
    public void onDeactivate() {
        out.clear();
        boxes.clear();
    }

    /** Container olmayan block entity'leri filtrelemek icin. */
    private static String beName(BlockEntityType<?> type) {
        if (type == BlockEntityType.CHEST) return "chest";
        if (type == BlockEntityType.TRAPPED_CHEST) return "trapped_chest";
        if (type == BlockEntityType.BARREL) return "barrel";
        if (type == BlockEntityType.SHULKER_BOX) return "shulker_box";
        if (type == BlockEntityType.HOPPER) return "hopper";
        if (type == BlockEntityType.FURNACE) return "furnace";
        if (type == BlockEntityType.BLAST_FURNACE) return "blast_furnace";
        if (type == BlockEntityType.SMOKER) return "smoker";
        if (type == BlockEntityType.DISPENSER) return "dispenser";
        if (type == BlockEntityType.DROPPER) return "dropper";
        if (type == BlockEntityType.ENDER_CHEST) return "ender_chest";
        if (type == BlockEntityType.CRAFTER) return "crafter";
        if (type == BlockEntityType.LECTERN) return "lectern";
        if (type == BlockEntityType.DECORATED_POT) return "decorated_pot";
        if (type == BlockEntityType.JUKEBOX) return "jukebox";
        if (type == BlockEntityType.BEACON) return "beacon";
        if (type == BlockEntityType.CAMPFIRE) return "campfire";
        return null;
    }

    private boolean isContainer(BlockEntityType<?> type) {
        return beName(type) != null;
    }

    private String typeLabel(BlockEntityType<?> type) {
        String n = beName(type);
        return n != null ? n : type.toString();
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** class_2744 gibi sikistirilmis isimleri okunabilir hale getirir. */
    private static String label(Object p) {
        if (p instanceof ClientboundLevelChunkWithLightPacket) return "chunk-data";
        if (p instanceof ClientboundLightUpdatePacket) return "light-update";
        if (p instanceof ClientboundForgetLevelChunkPacket) return "chunk-unload";
        if (p instanceof ClientboundChunkBatchStartPacket) return "chunk-batch-start";
        if (p instanceof ClientboundChunkBatchFinishedPacket) return "chunk-batch-end";
        if (p instanceof ClientboundBlockEntityDataPacket) return "be-data";
        if (p instanceof ClientboundBlockUpdatePacket) return "block-update";
        if (p instanceof ClientboundSectionBlocksUpdatePacket) return "section-blocks";
        if (p instanceof ClientboundBlockEventPacket) return "block-event";
        if (p instanceof ClientboundLevelEventPacket) return "level-event";
        if (p instanceof ClientboundSoundPacket) return "sound";
        if (p instanceof ClientboundOpenScreenPacket) return "open-screen";
        if (p instanceof ClientboundContainerSetContentPacket) return "container-content";
        if (p instanceof ClientboundContainerSetSlotPacket) return "container-slot";
        if (p instanceof ClientboundContainerClosePacket) return "container-close";

        String simple = p.getClass().getSimpleName();
        String full = p.getClass().getName();
        return simple + "  (" + full + ")";
    }

    private void addBox(BlockPos pos) {
        if (!render.get()) return;
        boxes.put(pos.immutable(), System.currentTimeMillis() + renderSeconds.get() * 1000L);
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet == null) return;

        String name = label(event.packet);

        if (logAll.get() && seenTypes.add(name)) {
            out.add("all   " + name);
        }

        // ---- chunk paketindeki ham block entity listesi ----
        if (event.packet instanceof ClientboundLevelChunkWithLightPacket p) {
            if (logChunkFlow.get()) {
                out.add("chunk chunk-data  cx=" + p.getX() + " cz=" + p.getZ());
            }

            if (!logChunkBe.get()) return;

            int cx = p.getX();
            int cz = p.getZ();

            try {
                p.getChunkData().getBlockEntitiesTagsConsumer(cx, cz).accept((pos, type, tag) -> {
                    if (chestOnly.get() && !isContainer(type)) return;

                    addBox(pos);

                    String extra = "";

                    if (showNbt.get() && tag != null) {
                        extra = "  nbt=" + trim(tag.toString(), 120);
                    }

                    out.add("chunk " + typeLabel(type) + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + extra);
                });
            } catch (Exception ignored) {
                // taninmayan block entity tipi: chunk'i atla
            }
            return;
        }

        if (event.packet instanceof ClientboundForgetLevelChunkPacket p) {
            if (logChunkFlow.get()) out.add("chunk chunk-unload  cx=" + p.pos().x + " cz=" + p.pos().z);
            return;
        }

        if (event.packet instanceof ClientboundLightUpdatePacket p) {
            if (logChunkFlow.get()) out.add("chunk light-update  cx=" + p.getX() + " cz=" + p.getZ());
            return;
        }

        if (event.packet instanceof ClientboundChunkBatchStartPacket) {
            if (logChunkFlow.get()) out.add("chunk chunk-batch-start");
            return;
        }

        if (event.packet instanceof ClientboundChunkBatchFinishedPacket) {
            if (logChunkFlow.get()) out.add("chunk chunk-batch-end");
            return;
        }

        if (event.packet instanceof ClientboundBlockUpdatePacket p) {
            if (!logChunkBe.get()) return;

            BlockPos pos = p.getPos();
            if (chestOnly.get()) return;

            out.add("block " + p.getBlockState().getBlock().getDescriptionId() + " @ "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            return;
        }

        if (event.packet instanceof ClientboundBlockEntityDataPacket p) {
            if (!logBlockEntity.get()) return;

            BlockEntityType<?> type = p.getType();
            if (chestOnly.get() && !isContainer(type)) return;

            addBox(p.getPos());

            CompoundTag tag = p.getTag();
            String extra = "";

            if (showNbt.get() && tag != null) {
                extra = "  nbt=" + trim(tag.toString(), 150);
            }

            BlockPos pos = p.getPos();
            out.add("BE    " + typeLabel(type) + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + extra);
            return;
        }

        if (event.packet instanceof ClientboundBlockEventPacket p) {
            if (!logBlockEvent.get()) return;

            BlockPos pos = p.getPos();
            out.add("lid   " + p.getBlock().getDescriptionId() + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " b0=" + p.getB0() + " b1=" + p.getB1());
            return;
        }

        if (event.packet instanceof ClientboundLevelEventPacket p) {
            if (!logLevelEvent.get()) return;

            BlockPos pos = p.getPos();
            out.add("event type=" + p.getType() + " data=" + p.getData() + " @ "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            return;
        }

        if (event.packet instanceof ClientboundSoundPacket p) {
            if (!logSound.get()) return;

            String path = p.getSound().value().location().getPath();

            if (!path.contains("chest") && !path.contains("barrel") && !path.contains("shulker")) return;

            out.add("sound " + path + " @ " + (int) p.getX() + " " + (int) p.getY() + " " + (int) p.getZ());
            return;
        }

        if (event.packet instanceof ClientboundOpenScreenPacket p) {
            if (!logScreen.get()) return;

            out.add("screen " + p.getTitle().getString());
            return;
        }

        if (!logContainer.get()) return;

        if (event.packet instanceof ClientboundContainerSetContentPacket) {
            out.add("menu  container-content");
        } else if (event.packet instanceof ClientboundContainerSetSlotPacket) {
            out.add("menu  container-slot");
        } else if (event.packet instanceof ClientboundContainerClosePacket) {
            out.add("menu  container-close");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        int second = (int) (System.currentTimeMillis() / 1000L);

        if (second != windowSecond) {
            windowSecond = second;
            windowCount = 0;
        }

        while (!out.isEmpty()) {
            String line = out.poll();
            if (line == null) continue;

            if (windowCount >= maxPerSecond.get()) continue;

            windowCount++;
            info("%s", line);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;
        if (boxes.isEmpty()) return;

        long now = System.currentTimeMillis();

        boxes.entrySet().removeIf(e -> e.getValue() < now);

        for (BlockPos pos : boxes.keySet()) {
            event.renderer.box(new AABB(pos), SIDE, LINE, ShapeMode.Lines, 0);
        }
    }
}
