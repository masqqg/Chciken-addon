package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Chest Packet Debug - sunucudan chest / container ile ilgili gelen her paketi
 * aninda sohbete yazar.
 */
public class ChestPacketDebug extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPackets = settings.createGroup("Packets");

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

    private final Setting<Boolean> logAll = sgPackets.add(new BoolSetting.Builder()
        .name("all")
        .description("Print every packet type the server sends, once each. Use this to see what your server actually sends.")
        .defaultValue(false)
        .build());

    private final ConcurrentLinkedQueue<String> out = new ConcurrentLinkedQueue<>();
    private final java.util.Set<String> seenTypes = new java.util.HashSet<>();

    private int windowSecond = -1;
    private int windowCount;

    public ChestPacketDebug() {
        super(AddonTemplate.CATEGORY, "chest-packet-debug",
            "Prints every chest / container related packet the server sends, the moment it arrives.");
    }

    @Override
    public void onActivate() {
        out.clear();
        seenTypes.clear();
        windowSecond = -1;
        windowCount = 0;
        info("watching chest packets - open, break or look at a chest");
    }

    @Override
    public void onDeactivate() {
        out.clear();
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

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet == null) return;

        String className = event.packet.getClass().getSimpleName();

        if (logAll.get() && seenTypes.add(className)) {
            out.add("all   " + className);
        }

        if (event.packet instanceof ClientboundBlockEntityDataPacket p) {
            if (!logBlockEntity.get()) return;

            BlockEntityType<?> type = p.getType();
            if (chestOnly.get() && !isContainer(type)) return;

            CompoundTag tag = p.getTag();
            String extra = "";

            if (showNbt.get() && tag != null) {
                extra = "  nbt=" + trim(tag.toString(), 150);
            }

            BlockPos pos = p.getPos();
            out.add("BE    " + typeLabel(type) + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + extra);
            return;
        }

        if (event.packet instanceof ClientboundLevelChunkWithLightPacket p) {
            if (!logChunkBe.get()) return;

            int cx = p.getX();
            int cz = p.getZ();

            try {
                p.getChunkData().getBlockEntitiesTagsConsumer(cx, cz).accept((pos, type, tag) -> {
                    if (chestOnly.get() && !isContainer(type)) return;

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

        if (className.startsWith("ClientboundContainer")) {
            out.add("menu  " + className);
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
}
