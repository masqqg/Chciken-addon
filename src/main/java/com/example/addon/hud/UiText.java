package com.example.addon.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Text drawing for the Chicken Client interface.
 *
 * <p>The HUD and its menu do not use Mojangles. Text is rendered with the
 * bundled Inter typeface, so the interface looks like a modern overlay instead
 * of the blocky vanilla pixel font. Inter ships as a regular resource-pack
 * font ({@code assets/chicken/font/inter.json}) and is resolved per component
 * style, which is how Minecraft applies custom fonts to individual glyphs.</p>
 *
 * <p>{@code modern-font=false} in {@code nano-hud.properties} (or the FONT
 * toggle in the menu) falls back to the vanilla font without touching any
 * layout code.</p>
 */
public final class UiText {
    public static final Identifier FONT_ID = Identifier.fromNamespaceAndPath("chicken", "inter");

    private static final Style MODERN_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT_ID));

    private UiText() {
    }

    private static boolean modern() {
        return NanoHudConfig.get().modernFont;
    }

    public static void draw(GuiGraphics context, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        if (font == null) return;

        if (modern()) context.drawString(font, styled(text), x, y, color, shadow);
        else context.drawString(font, text, x, y, color, shadow);
    }

    public static void drawCentered(GuiGraphics context, String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        draw(context, text, centerX - width(text) / 2, y, color, false);
    }

    public static int width(String text) {
        if (text == null || text.isEmpty()) return 0;

        Font font = Minecraft.getInstance().font;
        if (font == null) return 0;

        return modern() ? font.width(styled(text)) : font.width(text);
    }

    private static Component styled(String text) {
        return Component.literal(text).withStyle(MODERN_STYLE);
    }
}
