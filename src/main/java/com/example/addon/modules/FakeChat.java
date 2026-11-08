package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.List;
import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Tamamen istemci tarafında sahte sohbet mesajları ekler.
 * Mesajlar sunucuya gönderilmez; sadece kendi sohbet ekranında görünür.
 */
public class FakeChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> intervalMin = sgGeneral.add(new IntSetting.Builder()
        .name("interval-min")
        .description("Minimum seconds between fake messages.")
        .defaultValue(6)
        .range(1, 300)
        .sliderRange(1, 60)
        .build());

    private final Setting<Integer> intervalMax = sgGeneral.add(new IntSetting.Builder()
        .name("interval-max")
        .description("Maximum seconds between fake messages.")
        .defaultValue(20)
        .range(1, 600)
        .sliderRange(1, 120)
        .build());

    private final Setting<List<String>> names = sgGeneral.add(new StringListSetting.Builder()
        .name("names")
        .description("Random names used for fake messages.")
        .defaultValue(
            "Ben", "Baksov", "EkremSeydiMeyve", "Aduket", "mammta", "Rigat", "Tarkan",
            "Kaan", "Efe", "Deniz", "Arda", "Mert", "Luna", "Nova", "Sora", "Yuki"
        )
        .build());

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Random chat messages.")
        .defaultValue(
            "kanka lag mı var",
            "ben geldim",
            "bu ne la",
            "gg",
            "tekrar dene",
            "hack mi kullanıyon",
            "abi çok iyi",
            "naber",
            "pingim 52",
            "kim beni vurdu",
            "biraz bekleyin",
            "geliyorum",
            "yine mi",
            "bu chunk niye böyle",
            "diamond buldum",
            "koordinat ver",
            "burada kimse var mı",
            "iyi oyunlar",
            "afk mısın",
            "takım var mı",
            "bu ne biçim spawn",
            "server çok iyi"
        )
        .build());

    private final Setting<SettingColor> nameColor = sgGeneral.add(new ColorSetting.Builder()
        .name("name-color")
        .description("Color of the fake player name.")
        .defaultValue(new SettingColor(0, 255, 170))
        .build());

    private final Setting<SettingColor> messageColor = sgGeneral.add(new ColorSetting.Builder()
        .name("message-color")
        .description("Color of the fake message.")
        .defaultValue(new SettingColor(210, 210, 210))
        .build());

    private final Random random = new Random();
    private int timer;

    public FakeChat() {
        super(AddonTemplate.CATEGORY, "fake-chat", "Client-side fake chat messages with random names.");
    }

    @Override
    public void onActivate() {
        timer = nextDelay();
    }

    @Override
    public void onDeactivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gui == null) return;
        if (--timer > 0) return;

        timer = nextDelay();

        List<String> nameList = names.get();
        List<String> messageList = messages.get();
        if (nameList.isEmpty() || messageList.isEmpty()) return;

        String name = nameList.get(random.nextInt(nameList.size()));
        String message = messageList.get(random.nextInt(messageList.size()));

        MutableComponent line = Component.empty();
        line.append(Component.literal("<").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        line.append(Component.literal(name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb(nameColor.get())))));
        line.append(Component.literal("> ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        line.append(Component.literal(message).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb(messageColor.get())))));

        mc.gui.getChat().addMessage(line);
    }

    private int nextDelay() {
        int min = Math.max(1, Math.min(intervalMin.get(), intervalMax.get()));
        int max = Math.max(min, intervalMax.get());
        return (min + random.nextInt(max - min + 1)) * 20;
    }

    private static int rgb(SettingColor color) {
        return (color.r << 16) | (color.g << 8) | color.b;
    }
}
