package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Sağ tarafa, sunucunun gerçek scoreboard'ının üzerine çizilen sahte bir sidebar.
 * Başlık ve satırlar ayarlanabilir; &amp;/§ renk kodları desteklenir.
 */
public class FakeScoreboard extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<String> title = sgGeneral.add(new StringSetting.Builder()
        .name("title")
        .description("Scoreboard title. Supports & color codes.")
        .defaultValue("&6CHICKEN SMP")
        .wide()
        .build());

    private final Setting<List<String>> lines = sgGeneral.add(new StringListSetting.Builder()
        .name("lines")
        .description("Scoreboard lines. Supports & color codes.")
        .defaultValue(
            "&aBen",
            "&bBaksov",
            "&6EkremSeydiMeyve",
            "&aAduket",
            "&cmammta &7[774.15s]",
            "&eRigat",
            "&aTarkan &7[zenqid123]",
            "&7────────────────",
            "&fCHICKEN SMP &7(52ms)"
        )
        .build());

    private final Setting<Integer> offsetX = sgRender.add(new IntSetting.Builder()
        .name("x-offset")
        .description("Distance from the right screen edge.")
        .defaultValue(2)
        .range(0, 500)
        .sliderRange(0, 100)
        .build());

    private final Setting<Integer> offsetY = sgRender.add(new IntSetting.Builder()
        .name("y-offset")
        .description("Distance from the top screen edge.")
        .defaultValue(2)
        .range(0, 1000)
        .sliderRange(0, 200)
        .build());

    private final Setting<Integer> padding = sgRender.add(new IntSetting.Builder()
        .name("padding")
        .description("Inner padding of the sidebar.")
        .defaultValue(3)
        .range(1, 10)
        .build());

    private final Setting<Boolean> shadow = sgRender.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draw text with a shadow.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> background = sgRender.add(new ColorSetting.Builder()
        .name("background")
        .description("Sidebar background color. Alpha 255 fully covers the vanilla scoreboard.")
        .defaultValue(new SettingColor(0, 0, 0, 200))
        .build());

    private final Setting<SettingColor> titleColor = sgRender.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Default title color (before & codes).")
        .defaultValue(new SettingColor(255, 170, 0))
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Default line color (before & codes).")
        .defaultValue(new SettingColor(255, 255, 255))
        .build());

    public FakeScoreboard() {
        super(AddonTemplate.CATEGORY, "fake-scoreboard", "Draws a configurable fake scoreboard sidebar.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Component titleComponent = legacy(title.get(), titleColor.get()).withStyle(ChatFormatting.BOLD);
        List<Component> lineComponents = new ArrayList<>();
        int contentWidth = mc.font.width(titleComponent);

        for (String raw : lines.get()) {
            if (raw == null) continue;
            Component line = legacy(raw, lineColor.get());
            lineComponents.add(line);
            contentWidth = Math.max(contentWidth, mc.font.width(line));
        }

        int pad = padding.get();
        int lineHeight = mc.font.lineHeight + 1;
        int width = contentWidth + pad * 2;
        int height = pad * 2 + mc.font.lineHeight + 4 + lineComponents.size() * lineHeight;
        int x = Math.max(0, event.screenWidth - width - 2 - offsetX.get());
        int y = Math.max(0, 2 + offsetY.get());

        event.drawContext.fill(x, y, x + width, y + height, argb(background.get()));

        int textY = y + pad;
        int titleX = x + (width - mc.font.width(titleComponent)) / 2;
        event.drawContext.drawString(mc.font, titleComponent, titleX, textY, 0xFFFFFFFF, shadow.get());
        textY += mc.font.lineHeight + 4;

        for (Component line : lineComponents) {
            event.drawContext.drawString(mc.font, line, x + pad, textY, 0xFFFFFFFF, shadow.get());
            textY += lineHeight;
        }
    }

    private static int argb(SettingColor color) {
        return (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }

    /** & veya § renk kodlarını Minecraft Component'ine çevirir. */
    private static MutableComponent legacy(String text, SettingColor defaultColor) {
        Style base = Style.EMPTY.withColor(TextColor.fromRgb(
            (defaultColor.r << 16) | (defaultColor.g << 8) | defaultColor.b));
        MutableComponent out = Component.empty();
        Style style = base;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if ((ch == '&' || ch == '§') && i + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));

                if (format != null) {
                    if (buffer.length() > 0) {
                        out.append(Component.literal(buffer.toString()).withStyle(style));
                        buffer.setLength(0);
                    }

                    if (format == ChatFormatting.RESET) style = base;
                    else if (format.isColor()) style = style.withColor(format);

                    i++;
                    continue;
                }
            }

            buffer.append(ch);
        }

        if (buffer.length() > 0) out.append(Component.literal(buffer.toString()).withStyle(style));
        return out;
    }
}
