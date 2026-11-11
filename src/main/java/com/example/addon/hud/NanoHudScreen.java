package com.example.addon.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Vanilla-Minecraft settings screen for the standalone HUD.
 *
 * <p>No Meteor widget, setting or screen class is used here. The screen edits
 * {@link NanoHudConfig} directly, saves it to the HUD's own properties file and
 * draws a live yellow HUD preview beside the controls.</p>
 */
public final class NanoHudScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 352;

    private static final int PREVIEW_X = 356;
    private static final int PREVIEW_Y = 50;
    private static final int PREVIEW_WIDTH = 186;
    private static final int PREVIEW_HEIGHT = 286;

    private static final int YELLOW = 0xFFFFC400;
    private static final int YELLOW_SOFT = 0xFFFFE082;
    private static final int AMBER = 0xFFFF8A00;
    private static final int TEXT = 0xFFF5F7FA;
    private static final int MUTED = 0xFF9AA6B2;
    private static final int CARD = 0xF0141820;
    private static final int CARD_LIGHT = 0xFF1C222D;
    private static final int TRACK = 0xFF2A303A;

    private int slider;
    private boolean dirty;

    public NanoHudScreen() {
        super(Component.literal("Chicken Client HUD"));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        NanoHudConfig hud = NanoHudConfig.get();
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;

        context.fill(0, 0, width, height, 0xB8000000);

        // Yellow halo and card.
        context.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y + PANEL_HEIGHT + 2, 0x55FFC400);
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, CARD);
        context.fillGradient(x, y, x + PANEL_WIDTH, y + 3, YELLOW, AMBER);
        context.fill(x + 1, y + 3, x + PANEL_WIDTH - 1, y + 5, 0xFF3A2A00);

        context.drawString(font, "CHICKEN CLIENT", x + 18, y + 16, YELLOW, false);
        context.drawString(font, "HUD SETTINGS  //  STANDALONE NANO HUD", x + 18, y + 28, MUTED, false);
        button(context, "MODE: " + (hud.hudMode == NanoHudConfig.HudMode.LEFT_HAND ? "LEFT HAND" : "NORMAL"),
            x + PANEL_WIDTH - 190, y + 12, 150, 18, mouseX, mouseY);
        context.drawString(font, "P", x + PANEL_WIDTH - 28, y + 16, YELLOW_SOFT, false);

        int rowY = y + 52;
        toggle(context, "Watermark", hud.showWatermark, x + 18, rowY, mouseX, mouseY);
        toggle(context, "Player card", hud.showPlayer, x + 18, rowY + 25, mouseX, mouseY);
        toggle(context, "Stats & coordinates", hud.showStats, x + 18, rowY + 50, mouseX, mouseY);
        toggle(context, "Animations", hud.animations, x + 18, rowY + 75, mouseX, mouseY);

        slider(context, "Scale", hud.scale, 0.6, 2.5, x + 18, rowY + 108, 1, mouseX, mouseY, true);
        slider(context, "Background", hud.backgroundAlpha / 255.0, 0.2, 1.0, x + 18, rowY + 138, 2, mouseX, mouseY, false);
        slider(context, "Wing speed", hud.wingSpeed, 0.2, 3.0, x + 18, rowY + 168, 3, mouseX, mouseY, true);
        slider(context, "Wing amplitude", hud.wingAmplitude, 0.2, 2.0, x + 18, rowY + 198, 4, mouseX, mouseY, true);

        context.drawString(font, "BRAND COLOR", x + 18, rowY + 228, MUTED, false);
        boolean yellowSelected = (hud.primary & 0xFFFFFF) == 0xFFC400;
        boolean honeySelected = (hud.primary & 0xFFFFFF) == 0xFFB300;
        preset(context, "YELLOW", x + 18, rowY + 240, mouseX, mouseY, yellowSelected);
        preset(context, "HONEY", x + 88, rowY + 240, mouseX, mouseY, honeySelected);

        button(context, "RESET POSITION", x + 228, rowY + 240, 126, 20, mouseX, mouseY);
        button(context, "DONE", x + 286, y + PANEL_HEIGHT - 36, 56, 20, mouseX, mouseY);

        context.drawString(font, "P opens this menu.  F7 hides the HUD.  RSHIFT + drag moves it.  [ ] scales it.",
            x + 18, y + PANEL_HEIGHT - 15, 0xFF7F8A96, false);

        drawPreview(context, hud, x + PREVIEW_X, y + PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    private void drawPreview(GuiGraphics context, NanoHudConfig hud, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        double time = System.currentTimeMillis() / 1000.0;
        boolean animated = hud.animations;

        int primary = solid(hud.primary);
        int secondary = solid(hud.secondary);
        int accent = solid(hud.accent);
        int dotAlpha = animated
            ? 130 + (int) (90 * (0.5 + 0.5 * Math.sin(time * 3.0)))
            : 220;

        context.fill(x, y, x + width, y + height, 0xFF0B1017);
        context.fill(x, y, x + width, y + 2, color(hud.primary, 220));
        context.fill(x + 1, y + 2, x + width - 1, y + 3, color(hud.secondary, 70));
        context.drawString(font, "LIVE PREVIEW", x + 8, y + 8, YELLOW_SOFT, false);
        context.fill(x + width - 14, y + 11, x + width - 8, y + 17, color(hud.primary, dotAlpha));
        context.drawString(font, "LIVE", x + width - 42, y + 9, color(hud.primary, dotAlpha), false);

        int mx = x + 8;
        int my = y + 26;
        int mw = width - 16;
        int mh = height - 34;

        // The preview panel uses the same transparency and color values as the real HUD.
        context.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, color(hud.primary, 50));
        context.fill(mx, my, mx + mw, my + mh, argb(Math.max(80, hud.backgroundAlpha), 9, 13, 20));
        context.fillGradient(mx, my, mx + mw, my + 2, color(hud.primary, 210), color(hud.secondary, 35));

        int bob = animated ? (int) Math.round(Math.sin(time * 2.1) * 2.0) : 0;
        int contentTop = my + 9;

        if (hud.showWatermark) {
            drawCloud(context, mx + 10, contentTop + bob, hud.primary);
            context.drawString(font, "CHICKEN", mx + 31, contentTop + 1 + bob, TEXT, false);
            context.drawString(font, "CLIENT // NANO", mx + 31, contentTop + 11 + bob, color(hud.primary, 215), false);
            contentTop += 25;
        }

        if (hud.showPlayer) {
            drawPlayerCard(context, player, mx + 7, contentTop + 2, mw - 14,
                primary, secondary, accent, time, animated);
            contentTop += 48;
        }

        if (hud.showStats) {
            drawStats(context, mc, player, mx + 7, Math.min(contentTop + 3, my + mh - 55), mw - 14, primary, accent);
        }

        if (!hud.showWatermark && !hud.showPlayer && !hud.showStats) {
            context.drawCenteredString(font, "HUD HIDDEN", mx + mw / 2, my + mh / 2 - 4, color(hud.primary, 220));
        }

        if (animated) {
            int scan = my + 6 + (int) ((time * 32.0) % Math.max(1, mh - 12));
            context.fill(mx + 5, scan, mx + mw - 5, scan + 1, color(hud.primary, 55));
            context.fill(mx + 5, scan - 3, mx + mw - 5, scan + 4, color(hud.primary, 10));
        }

        context.drawString(font, String.format(Locale.ROOT, "%.2fx", hud.scale), mx + 6, my + mh - 11, color(hud.primary, 220), false);
        context.drawString(font, "YELLOW CORE", mx + mw - 60, my + mh - 11, MUTED, false);
    }

    private void drawCloud(GuiGraphics context, int x, int y, int primary) {
        int cloud = color(primary, 240);
        context.fill(x + 3, y + 5, x + 15, y + 13, cloud);
        context.fill(x, y + 7, x + 18, y + 13, cloud);
        context.fill(x + 5, y + 2, x + 12, y + 7, cloud);
        context.fill(x + 7, y + 3, x + 14, y + 5, 0x77FFFFFF);
    }

    private void drawPlayerCard(GuiGraphics context, LocalPlayer player,
                                int x, int y, int width, int primary, int secondary, int accent,
                                double time, boolean animated) {
        float health = player == null ? 20.0f : player.getHealth();
        float maxHealth = player == null ? 20.0f : Math.max(1.0f, player.getMaxHealth());
        float healthRatio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        int armor = player == null ? 0 : player.getArmorValue();
        String name = player == null ? "Player" : player.getName().getString();

        int bob = animated ? (int) Math.round(Math.sin(time * 2.2)) : 0;
        int sway = animated ? (int) Math.round(Math.sin(time * 1.35)) : 0;
        int aura = animated ? 38 + (int) (26 * (0.5 + 0.5 * Math.sin(time * 2.8))) : 38;
        boolean blink = animated && (time % 4.2) > 4.05;

        // Soft pulsing aura behind the whole player card.
        context.fill(x - 1, y - 1, x + width + 1, y + 45, color(primary, aura));
        context.fill(x, y, x + width, y + 44, 0x99151C26);

        // Animated yellow wings behind the avatar.
        drawPreviewWings(context, x, y, primary, secondary, time, animated);

        // Avatar: breath, head sway, blink and tiny orbiting yellow sparks.
        context.fill(x, y, x + 18, y + 18, 0xFF26303D);
        context.fill(x + 1, y + 1, x + 17, y + 17, color(primary, 55));
        context.fill(x + 6 + sway, y + 4 + bob, x + 12 + sway, y + 11 + bob, 0xFFF5EFE0);
        context.fill(x + 4, y + 10 + bob, x + 14, y + 17 + bob, 0xFFF5EFE0);
        if (blink) {
            context.fill(x + 7 + sway, y + 7 + bob, x + 11 + sway, y + 8 + bob, 0xFF23201A);
        }
        else {
            context.fill(x + 7 + sway, y + 7 + bob, x + 8 + sway, y + 8 + bob, 0xFF23201A);
            context.fill(x + 10 + sway, y + 7 + bob, x + 11 + sway, y + 8 + bob, 0xFF23201A);
        }
        if (animated) {
            for (int i = 0; i < 4; i++) {
                double angle = time * 1.4 + i * Math.PI / 2.0;
                int sx = x + 9 + (int) Math.round(Math.cos(angle) * 11.0);
                int sy = y + 9 + (int) Math.round(Math.sin(angle) * 11.0);
                context.fill(sx, sy, sx + 1, sy + 1, color(primary, 125));
            }
        }

        context.drawString(font, name, x + 24, y + 1, TEXT, false);
        int nameWidth = font.width(name);
        int underline = animated
            ? (int) (Math.max(10, nameWidth) * (0.58 + 0.42 * (0.5 + 0.5 * Math.sin(time * 2.4))))
            : Math.max(10, nameWidth);
        context.fill(x + 24, y + 11, x + 24 + underline, y + 12, color(primary, 165));
        context.drawString(font, "LOCAL PLAYER", x + 24, y + 14, MUTED, false);

        int barX = x + 24;
        int barW = Math.max(26, width - 70);
        int healthAlpha = animated ? 205 + (int) (30 * (0.5 + 0.5 * Math.sin(time * 4.0))) : 235;
        previewBar(context, barX, y + 23, barW, 4, healthRatio, color(primary, healthAlpha), 0xFF202B37, time, animated);
        previewBar(context, barX, y + 30, barW, 3, Math.min(1.0f, armor / 20.0f), color(secondary, 225), 0xFF202B37, time, animated);
        float xp = player == null ? 0.0f : player.experienceProgress;
        previewBar(context, barX, y + 36, barW, 2, xp, color(accent, 220), 0xFF202B37, time, animated);
        context.drawString(font, String.format(Locale.ROOT, "%.1f", health), x + width - 25, y + 21, color(accent, 230), false);
        context.drawString(font, "HP", x + width - 25, y + 30, MUTED, false);
    }

    private void drawPreviewWings(GuiGraphics context, int x, int y,
                                  int primary, int secondary, double time, boolean animated) {
        NanoHudConfig hud = NanoHudConfig.get();
        double speed = Math.max(0.15, hud.wingSpeed);
        double amplitude = Math.max(0.1, hud.wingAmplitude);

        context.fill(x - 5, y + 4, x + 23, y + 19, color(primary, animated ? 22 : 16));

        for (int side = -1; side <= 1; side += 2) {
            int rootX = side < 0 ? x + 2 : x + 16;
            for (int i = 0; i < 3; i++) {
                int tipX = rootX + side * (7 + i * 4);
                int flap = animated
                    ? (int) Math.round(Math.sin(time * 7.5 * speed + i * 0.7 + side * 0.9) * 2.0 * amplitude)
                    : 0;
                int tipY = y + 6 + i * 4 + flap;
                int left = Math.min(rootX, tipX);
                int right = Math.max(rootX, tipX);
                context.fill(left, tipY, right, tipY + 1, color(i == 0 ? primary : secondary, 195 - i * 35));
                context.fill(left, tipY + 1, right, tipY + 2, color(primary, 48));
            }
        }
    }

    private void drawStats(GuiGraphics context, Minecraft mc, LocalPlayer player,
                           int x, int y, int width, int primary, int accent) {
        int ping = -1;
        if (mc.getConnection() != null && player != null) {
            var info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) ping = info.getLatency();
        }

        float speed = player == null ? 0.0f
            : (float) Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z) * 20.0f;
        int fps = mc.getFps();

        context.fill(x, y, x + width, y + 1, color(primary, 45));
        context.drawString(font, "PING", x, y + 5, MUTED, false);
        context.drawString(font, "FPS", x + 38, y + 5, MUTED, false);
        context.drawString(font, "SPEED", x + 68, y + 5, MUTED, false);
        context.drawString(font, "STATUS", x + 112, y + 5, MUTED, false);
        context.drawString(font, ping < 0 ? "--" : ping + "ms", x, y + 14, color(accent, 230), false);
        context.drawString(font, Integer.toString(fps), x + 38, y + 14, color(primary, 235), false);
        context.drawString(font, String.format(Locale.ROOT, "%.1f", speed), x + 68, y + 14, color(accent, 230), false);
        context.drawString(font, "LIVE", x + 112, y + 14, color(primary, 235), false);
        context.drawString(font, "XYZ  READY", x, y + 25, MUTED, false);
    }

    private void previewBar(GuiGraphics context, int x, int y, int width, int height,
                            float ratio, int fill, int background, double time, boolean animated) {
        context.fill(x, y, x + width, y + height, background);
        int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (filled <= 0) return;

        context.fill(x, y, x + filled, y + height, fill);
        if (animated) {
            int shine = x + (int) ((time * 38.0) % Math.max(1, filled));
            context.fill(shine, y, Math.min(x + filled, shine + 7), y + height, 0x66FFF7DA);
        }
    }

    private void toggle(GuiGraphics context, String label, boolean value, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 260, 18);
        context.drawString(font, label, x, y + 3, hovered ? TEXT : 0xFFD5DCE5, false);

        int tx = x + 286;
        int ty = y + 2;
        context.fill(tx, ty, tx + 32, ty + 14, value ? 0xFFFFC400 : TRACK);
        context.fill(tx + (value ? 18 : 2), ty + 2, tx + (value ? 30 : 14), ty + 12, value ? 0xFF17130A : 0xFFD5DCE5);
    }

    private void slider(GuiGraphics context, String label, double value, double min, double max,
                        int x, int y, int id, int mouseX, int mouseY, boolean scale) {
        context.drawString(font, label, x, y, 0xFFD5DCE5, false);
        String shown = scale
            ? String.format(Locale.ROOT, "%.2fx", value)
            : Math.round(value * 100.0) + "%";
        context.drawString(font, shown, x + 286, y, YELLOW_SOFT, false);

        int tx = x + 110;
        int ty = y + 11;
        int tw = 184;
        int knob = tx + (int) Math.round((value - min) / (max - min) * tw);
        context.fill(tx, ty, tx + tw, ty + 3, TRACK);
        context.fill(tx, ty, Math.max(tx + 2, knob), ty + 3, YELLOW);
        context.fill(knob - 3, ty - 3, knob + 3, ty + 6, slider == id ? YELLOW_SOFT : YELLOW);
    }

    private void preset(GuiGraphics context, String label, int x, int y, int mouseX, int mouseY, boolean selected) {
        boolean hovered = inside(mouseX, mouseY, x, y, 64, 20);
        context.fill(x, y, x + 64, y + 20, selected ? 0xFFFFC400 : (hovered ? CARD_LIGHT : 0xFF202630));
        int color = selected ? 0xFF17130A : 0xFFD5DCE5;
        context.drawCenteredString(font, label, x + 32, y + 6, color);
    }

    private void button(GuiGraphics context, String label, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, hovered ? 0xFFFFC400 : 0xFF202630);
        context.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2 + 1, hovered ? 0xFF17130A : 0xFFD5DCE5);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        NanoHudConfig hud = NanoHudConfig.get();
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        int rowY = y + 52;

        if (inside(mouseX, mouseY, x + 286, rowY, 32, 18)) {
            hud.showWatermark = !hud.showWatermark;
            markDirty(hud);
            return true;
        }
        if (inside(mouseX, mouseY, x + 286, rowY + 25, 32, 18)) {
            hud.showPlayer = !hud.showPlayer;
            markDirty(hud);
            return true;
        }
        if (inside(mouseX, mouseY, x + 286, rowY + 50, 32, 18)) {
            hud.showStats = !hud.showStats;
            markDirty(hud);
            return true;
        }
        if (inside(mouseX, mouseY, x + 286, rowY + 75, 32, 18)) {
            hud.animations = !hud.animations;
            markDirty(hud);
            return true;
        }

        if (inside(mouseX, mouseY, x + 110, rowY + 116, 184, 18)) {
            slider = 1;
            updateScale(hud, mouseX, x + 110, 184);
            return true;
        }
        if (inside(mouseX, mouseY, x + 110, rowY + 146, 184, 18)) {
            slider = 2;
            updateAlpha(hud, mouseX, x + 110, 184);
            return true;
        }
        if (inside(mouseX, mouseY, x + 110, rowY + 176, 184, 18)) {
            slider = 3;
            updateWingSpeed(hud, mouseX, x + 110, 184);
            return true;
        }
        if (inside(mouseX, mouseY, x + 110, rowY + 206, 184, 18)) {
            slider = 4;
            updateWingAmplitude(hud, mouseX, x + 110, 184);
            return true;
        }

        if (inside(mouseX, mouseY, x + 18, rowY + 240, 64, 20)) {
            setPreset(hud, 0xFFFFC400, 0xFFFF8A00, 0xFFFFE082);
            return true;
        }
        if (inside(mouseX, mouseY, x + 88, rowY + 240, 64, 20)) {
            setPreset(hud, 0xFFFFB300, 0xFFFF7A00, 0xFFFFE0A0);
            return true;
        }

        if (inside(mouseX, mouseY, x + 228, rowY + 240, 126, 20)) {
            hud.x = 10;
            hud.y = 10;
            markDirty(hud);
            return true;
        }

        if (inside(mouseX, mouseY, x + 286, y + PANEL_HEIGHT - 36, 56, 20)) {
            onClose();
            return true;
        }

        if (inside(mouseX, mouseY, x + PANEL_WIDTH - 190, y + 12, 150, 18)) {
            hud.hudMode = hud.hudMode == NanoHudConfig.HudMode.LEFT_HAND
                ? NanoHudConfig.HudMode.NORMAL
                : NanoHudConfig.HudMode.LEFT_HAND;
            markDirty(hud);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (slider == 1 || slider == 2 || slider == 3 || slider == 4) {
            NanoHudConfig hud = NanoHudConfig.get();
            int x = (width - PANEL_WIDTH) / 2;
            if (slider == 1) updateScale(hud, event.x(), x + 110, 184);
            else if (slider == 2) updateAlpha(hud, event.x(), x + 110, 184);
            else if (slider == 3) updateWingSpeed(hud, event.x(), x + 110, 184);
            else updateWingAmplitude(hud, event.x(), x + 110, 184);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (slider != 0) {
            slider = 0;
            NanoHudConfig.get().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateScale(NanoHudConfig hud, double mouseX, int trackX, int trackWidth) {
        double t = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
        hud.scale = 0.6 + t * 1.9;
        markDirty(hud);
    }

    private void updateAlpha(NanoHudConfig hud, double mouseX, int trackX, int trackWidth) {
        double t = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
        hud.backgroundAlpha = (int) Math.round((0.2 + t * 0.8) * 255.0);
        markDirty(hud);
    }

    private void updateWingSpeed(NanoHudConfig hud, double mouseX, int trackX, int trackWidth) {
        double t = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
        hud.wingSpeed = 0.2 + t * 2.8;
        markDirty(hud);
    }

    private void updateWingAmplitude(NanoHudConfig hud, double mouseX, int trackX, int trackWidth) {
        double t = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
        hud.wingAmplitude = 0.2 + t * 1.8;
        markDirty(hud);
    }

    private void setPreset(NanoHudConfig hud, int primary, int secondary, int accent) {
        hud.primary = primary;
        hud.secondary = secondary;
        hud.accent = accent;
        markDirty(hud);
    }

    private void markDirty(NanoHudConfig hud) {
        dirty = true;
        hud.save();
    }

    private static int solid(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int color(int rgb, int alpha) {
        return ((alpha & 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        if (dirty) NanoHudConfig.get().save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
