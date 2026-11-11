package com.example.addon.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;

/**
 * Safe HUD path for Android/ARM or non-OpenGL renderers.
 *
 * <p>NanoVG is a native OpenGL library. MobileGlues/ANGLE and aarch64 Android
 * installations cannot reliably load the desktop NanoVG natives, so this class
 * draws the same yellow HUD concept with Minecraft's own 2D GUI primitives.
 * It has no native dependency and therefore cannot trigger the NanoVG native
 * crash path.</p>
 */
public final class NanoHudFallback {
    private static final int WIDTH = 272;
    private static final int HEIGHT = 208;
    private static final float[] FPS = new float[32];

    private static int fpsIndex;
    private static Boolean unavailable;

    private NanoHudFallback() {
    }

    public static void render(GuiGraphics context, NanoHudConfig hud) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getWindow().isIconified()) return;
        if (mc.options.hideGui) return;

        boolean leftHand = hud.hudMode == NanoHudConfig.HudMode.LEFT_HAND;
        double scale = Math.max(0.35, Math.min(2.5, hud.scale));
        if (leftHand) {
            scale = Math.min(scale, (mc.getWindow().getGuiScaledHeight() - 24.0) / HEIGHT);
        }
        double x = leftHand ? 12 : hud.panelX();
        double y = leftHand ? mc.getWindow().getGuiScaledHeight() - HEIGHT * scale - 10 : hud.panelY();

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale((float) scale, (float) scale);

        if (leftHand) drawArm(context, hud, true);
        drawPanel(context, mc, hud, leftHand);
        if (leftHand) drawArm(context, hud, false);

        pose.popMatrix();
    }

    private static void drawPanel(GuiGraphics context, Minecraft mc, NanoHudConfig hud, boolean leftHand) {
        int primary = hud.primary;
        int secondary = hud.secondary;
        int accent = hud.accent;
        long now = System.currentTimeMillis();
        double time = now / 1000.0;
        boolean animated = hud.animations;
        LocalPlayer player = mc.player;

        // Shadow, body and yellow border.
        context.fill(-4, -4, WIDTH + 4, HEIGHT + 4, 0x66000000);
        context.fill(0, 0, WIDTH, HEIGHT, argb(hud.backgroundAlpha, 9, 13, 20));
        context.fill(0, 0, WIDTH, 2, color(primary, 235));
        context.fill(0, HEIGHT - 1, WIDTH, HEIGHT, color(secondary, 100));
        context.fill(0, 0, 1, HEIGHT, color(primary, 120));
        context.fill(WIDTH - 1, 0, WIDTH, HEIGHT, color(secondary, 70));

        // Corner brackets.
        context.fill(6, 6, 18, 7, color(primary, 220));
        context.fill(6, 6, 7, 18, color(primary, 220));
        context.fill(WIDTH - 18, 6, WIDTH - 6, 7, color(secondary, 220));
        context.fill(WIDTH - 7, 6, WIDTH - 6, 18, color(secondary, 220));
        context.fill(6, HEIGHT - 7, 18, HEIGHT - 6, color(secondary, 220));
        context.fill(6, HEIGHT - 18, 7, HEIGHT - 6, color(secondary, 220));

        // Drifting dust.
        if (animated) {
            for (int i = 0; i < 12; i++) {
                double fall = 5.0 + (i % 4) * 3.0;
                int px = 16 + ((i * 47) % (WIDTH - 32));
                int py = HEIGHT - 12 - (int) ((time * fall + i * 19.0) % (HEIGHT - 24));
                context.fill(px, py, px + 1, py + 1, color(i % 2 == 0 ? primary : secondary, 50));
            }
        }

        text(context, mc, leftHand ? "LEFT HAND HUD" : "CHICKEN CLIENT", 18, 10, color(primary, 240));
        text(context, mc, leftHand ? "HELD IN YOUR LEFT HAND" : "NANO HUD // YELLOW CORE", 18, 21, 0xFF8E9AAA);

        if (hud.showWatermark) {
            drawCloud(context, 18, 33, primary, animated ? (int) Math.round(Math.sin(time * 2.1) * 2) : 0);
            text(context, mc, "CHICKEN", 39, 33, 0xFFF5F7FA);
            text(context, mc, "CLIENT", 39, 43, color(primary, 220));
            text(context, mc, "LOCAL PLAYER", 82, 43, 0xFF7F8A96);
        }

        int contentTop = 55;
        if (hud.showPlayer && player != null) {
            drawPlayer(context, mc, player, hud, 14, contentTop, animated, time);
            contentTop += 58;
        }

        if (hud.showStats) {
            drawStats(context, mc, player, hud, 14, Math.min(contentTop, HEIGHT - 58), animated, time);
        }

        if (!hud.showWatermark && !hud.showPlayer && !hud.showStats) {
            text(context, mc, "HUD HIDDEN", 18, HEIGHT / 2, color(primary, 220));
        }

        if (animated) {
            int scan = 6 + (int) ((time * 28.0) % (HEIGHT - 12));
            context.fill(8, scan, WIDTH - 8, scan + 1, color(primary, 48));
        }

        text(context, mc, "F7 HIDE", 14, HEIGHT - 17, 0xFF7F8A96);
        text(context, mc, "F8 SETTINGS", 62, HEIGHT - 17, 0xFF7F8A96);
        text(context, mc, String.format(Locale.ROOT, "%.2fx", hud.scale), WIDTH - 48, HEIGHT - 17, color(primary, 210));
    }

    private static void drawPlayer(GuiGraphics context, Minecraft mc, LocalPlayer player, NanoHudConfig hud,
                                   int x, int y, boolean animated, double time) {
        int primary = hud.primary;
        int secondary = hud.secondary;
        int accent = hud.accent;
        float health = Math.max(0.0f, player.getHealth());
        float maxHealth = Math.max(1.0f, player.getMaxHealth());
        float ratio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        boolean danger = ratio < 0.35f;

        context.fill(x - 1, y - 1, x + WIDTH - 14, y + 49, color(primary, animated ? 28 : 18));
        context.fill(x, y, x + WIDTH - 14, y + 48, 0x99151C26);

        drawWings(context, x + 17, y + 5, hud, animated, time);

        // Avatar.
        int bob = animated ? (int) Math.round(Math.sin(time * 2.2)) : 0;
        int sway = animated ? (int) Math.round(Math.sin(time * 1.35)) : 0;
        context.fill(x, y + 4, x + 20, y + 24, 0xFF26303D);
        context.fill(x + 1, y + 5, x + 19, y + 23, color(primary, 50));
        context.fill(x + 7 + sway, y + 8 + bob, x + 13 + sway, y + 16 + bob, 0xFFF5EFE0);
        context.fill(x + 5, y + 15 + bob, x + 15, y + 23 + bob, 0xFFF5EFE0);
        boolean blink = animated && (time % 4.2) > 4.05;
        if (!blink) {
            context.fill(x + 8 + sway, y + 11 + bob, x + 9 + sway, y + 12 + bob, 0xFF23201A);
            context.fill(x + 11 + sway, y + 11 + bob, x + 12 + sway, y + 12 + bob, 0xFF23201A);
        }

        text(context, mc, player.getName().getString(), x + 28, y + 4, 0xFFF5F7FA);
        text(context, mc, "LOCAL PLAYER", x + 28, y + 15, 0xFF7F8A96);

        int barX = x + 28;
        int barW = WIDTH - 78;
        bar(context, barX, y + 27, barW, 5, ratio,
            danger ? color(0xFF465A, animated ? 190 + (int) (40 * Math.sin(time * 7)) : 220) : color(primary, 230));
        bar(context, barX, y + 35, barW, 3, Math.min(1.0f, player.getArmorValue() / 20.0f), color(secondary, 220));
        bar(context, barX, y + 41, barW, 2, player.experienceProgress, color(accent, 210));
        text(context, mc, String.format(Locale.ROOT, "%.1f", health), x + WIDTH - 56, y + 26, color(accent, 230));
        if (danger) text(context, mc, "LOW HP", x + WIDTH - 56, y + 36, color(0xFF465A, 220));
        else text(context, mc, "ARMOR " + player.getArmorValue(), x + WIDTH - 56, y + 36, color(primary, 210));
    }

    private static void drawStats(GuiGraphics context, Minecraft mc, LocalPlayer player, NanoHudConfig hud,
                                  int x, int y, boolean animated, double time) {
        int ping = -1;
        if (mc.getConnection() != null && player != null) {
            var info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        int fps = mc.getFps();
        FPS[fpsIndex] = fps;
        fpsIndex = (fpsIndex + 1) % FPS.length;

        context.fill(x, y, x + WIDTH - 28, y + 1, color(hud.primary, 50));
        text(context, mc, "PING", x, y + 6, 0xFF7F8A96);
        text(context, mc, "FPS", x + 46, y + 6, 0xFF7F8A96);
        text(context, mc, "SPEED", x + 82, y + 6, 0xFF7F8A96);
        text(context, mc, ping < 0 ? "--" : ping + "ms", x, y + 16, color(hud.accent, 225));
        text(context, mc, Integer.toString(fps), x + 46, y + 16, color(hud.primary, 230));
        text(context, mc, String.format(Locale.ROOT, "%.1f", player == null ? 0.0f
            : (float) Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z) * 20.0f), x + 82, y + 16, color(hud.accent, 225));

        // Small FPS graph.
        int gx = x + 132;
        int gy = y + 6;
        int gw = WIDTH - 160;
        int gh = 24;
        context.fill(gx, gy, gx + gw, gy + gh, 0x66000000);
        int previous = -1;
        for (int i = 0; i < FPS.length; i++) {
            float value = FPS[(fpsIndex + i) % FPS.length];
            int px = gx + 2 + i * (gw - 4) / FPS.length;
            int py = gy + gh - 2 - (int) (Math.min(1.0f, value / 240.0f) * (gh - 4));
            if (previous >= 0) context.fill(px - 1, py, px, Math.max(py, previous), color(i > FPS.length / 2 ? hud.primary : hud.secondary, 170));
            previous = py;
        }
        text(context, mc, "FPS", gx + 2, gy - 7, 0xFF7F8A96);
    }

    private static void drawWings(GuiGraphics context, int avatarX, int avatarY, NanoHudConfig hud,
                                  boolean animated, double time) {
        double speed = Math.max(0.15, hud.wingSpeed);
        double amplitude = Math.max(0.1, hud.wingAmplitude);
        for (int side = -1; side <= 1; side += 2) {
            int rootX = side < 0 ? avatarX - 2 : avatarX + 20;
            for (int i = 0; i < 3; i++) {
                int tipX = rootX + side * (8 + i * 4);
                int flap = animated
                    ? (int) Math.round(Math.sin(time * 7.5 * speed + i * 0.7 + side * 0.9) * 2.0 * amplitude)
                    : 0;
                int tipY = avatarY + 9 + i * 4 + flap;
                int left = Math.min(rootX, tipX);
                int right = Math.max(rootX, tipX);
                context.fill(left, tipY, right, tipY + 1, color(i == 0 ? hud.primary : hud.secondary, 185 - i * 35));
            }
        }
    }

    private static void drawArm(GuiGraphics context, NanoHudConfig hud, boolean behind) {
        int primary = hud.primary;
        if (behind) {
            context.fill(-18, HEIGHT - 26, 58, HEIGHT + 34, 0xFF4A3525);
            context.fill(-10, HEIGHT - 40, 48, HEIGHT + 18, 0xFF6B4A2A);
            return;
        }

        // Glove and fingers around the lower-left corner of the panel.
        context.fill(0, HEIGHT - 20, 36, HEIGHT + 6, color(primary, 230));
        context.fill(8, HEIGHT - 34, 24, HEIGHT - 18, color(primary, 240));
        for (int i = 0; i < 3; i++) {
            int yy = HEIGHT - 14 + i * 6;
            context.fill(32, yy, 48, yy + 4, color(hud.secondary, 220));
        }
    }

    private static void drawCloud(GuiGraphics context, int x, int y, int primary, int bob) {
        int c = color(primary, 235);
        context.fill(x + 3, y + 6 + bob, x + 15, y + 13 + bob, c);
        context.fill(x, y + 8 + bob, x + 18, y + 13 + bob, c);
        context.fill(x + 5, y + 3 + bob, x + 12, y + 8 + bob, c);
        context.fill(x + 7, y + 4 + bob, x + 14, y + 6 + bob, 0x66FFFFFF);
    }

    private static void bar(GuiGraphics context, int x, int y, int width, int height, float ratio, int fill) {
        context.fill(x, y, x + width, y + height, 0xFF202B37);
        int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (filled > 0) context.fill(x, y, x + filled, y + height, fill);
    }

    private static void text(GuiGraphics context, Minecraft mc, String text, int x, int y, int color) {
        context.drawString(mc.font, text, x, y, color, false);
    }

    private static int color(int rgb, int alpha) {
        return ((alpha & 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    public static boolean isUnavailable() {
        if (unavailable == null) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean android = System.getenv("ANDROID_ROOT") != null
                || System.getenv("ANDROID_DATA") != null
                || os.contains("android");
            boolean armWithoutNative = (arch.contains("aarch64") || arch.contains("arm64")) && !os.contains("mac");
            unavailable = android || armWithoutNative;
        }
        return unavailable;
    }
}
