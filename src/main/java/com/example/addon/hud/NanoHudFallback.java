package com.example.addon.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Chicken Client HUD, drawn only with Minecraft's own GUI primitives.
 *
 * <p>Everything here is plain {@link GuiGraphics} fills and text, so it has no
 * native dependency and cannot disturb the world renderer's GPU state. The
 * layout, palette and animations match the yellow Chicken Client branding.</p>
 */
public final class NanoHudFallback {
    public static final double PANEL_WIDTH = 272.0;
    public static final double PANEL_HEIGHT = 208.0;

    private static final float[] FPS = new float[32];
    private static int fpsIndex;

    private NanoHudFallback() {
    }

    /**
     * Screen-space red pulse at the panel edges when the player is close to
     * death. Drawn before the panel transform, so it uses absolute coordinates.
     */
    public static void renderLowHealthVignette(GuiGraphics context, NanoHudConfig hud) {
        Minecraft mc = Minecraft.getInstance();
        if (!hud.lowHealthVignette || !hud.animations) return;
        if (mc.level == null || mc.player == null) return;

        LocalPlayer player = mc.player;
        float maxHealth = Math.max(1.0f, player.getMaxHealth());
        float ratio = Math.max(0.0f, Math.min(1.0f, player.getHealth() / maxHealth));
        if (ratio >= 0.35f) return;

        double time = System.currentTimeMillis() / 1000.0;
        double pulse = 0.5 + 0.5 * Math.sin(time * 5.2);
        double strength = (1.0 - ratio / 0.35) * (0.45 + 0.55 * pulse);
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int band = 46;

        int top = (alphaByte(120 * strength) << 24) | 0xFF2530;
        int side = (alphaByte(70 * strength) << 24) | 0xFF2530;
        context.fillGradient(0, 0, width, band, top, 0x00FF2530);
        context.fillGradient(0, height - band, width, height, 0x00FF2530, top);
        context.fill(0, 0, 5, height, side);
        context.fill(width - 5, 0, width, height, side);
    }

    public static void render(GuiGraphics context, NanoHudConfig hud) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getWindow().isIconified()) return;
        if (mc.options.hideGui) return;

        boolean leftHand = hud.hudMode == NanoHudConfig.HudMode.LEFT_HAND;
        double scale = Math.max(0.35, Math.min(2.5, hud.scale));
        if (leftHand) {
            scale = Math.min(scale, (mc.getWindow().getGuiScaledHeight() - 24.0) / PANEL_HEIGHT);
        }
        double x = leftHand ? 12.0 : hud.panelX();
        double y = leftHand
            ? mc.getWindow().getGuiScaledHeight() - PANEL_HEIGHT * scale - 10.0
            : hud.panelY();

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
        int w = (int) PANEL_WIDTH;
        int h = (int) PANEL_HEIGHT;
        double time = System.currentTimeMillis() / 1000.0;
        boolean animated = hud.animations;
        LocalPlayer player = mc.player;
        double open = animated ? hud.openProgress() : 1.0;
        int alpha = (int) Math.round(hud.backgroundAlpha * (0.35 + 0.65 * open));

        // Layered outer glow makes the panel sit on the screen like a game overlay.
        for (int layer = 4; layer >= 1; layer--) {
            outline(context, -layer - 1, -layer - 1, w + layer + 1, h + layer + 1,
                color(layer % 2 == 0 ? primary : secondary, animated ? 8 + layer * 5 : 6 + layer * 3));
        }
        context.fill(-1, -1, w + 1, h + 1, color(primary, animated ? 46 : 30));

        // Body: vertical glass gradient plus a bright top band.
        context.fillGradient(0, 0, w, h, argb(alpha, 17, 23, 33), argb(alpha, 5, 8, 12));
        context.fillGradient(0, 0, w, 54, argb(Math.min(175, (int) (alpha * 0.78)), 36, 45, 62), 0x00090D14);
        context.fillGradient(2, 2, 2 + (w - 4), 22, 0x1EFFFFFF, 0x00FFFFFF);
        context.fillGradient(0, 0, w, 3, color(primary, 235), color(secondary, 70));
        context.fill(0, h - 2, w, h, color(secondary, 90));
        context.fill(0, 0, 1, h, color(primary, 130));
        context.fill(w - 1, 0, w, h, color(secondary, 80));

        if (animated) {
            lightSweep(context, w, h, primary, time);
            drawScanline(context, w, h, primary, time);
            drawDust(context, w, h, primary, secondary, time);
        }

        drawBrackets(context, primary, secondary, w, h, animated, time);

        drawWatermark(context, mc, hud, leftHand, time, primary, secondary, animated);
        context.fillGradient(14, 34, w - 14, 35, color(primary, 150), color(secondary, 30));

        if (player != null && hud.showPlayer) {
            drawPlayerCard(context, mc, player, hud, time, primary, secondary, accent, animated);
        }

        if (hud.showStats) {
            drawStats(context, mc, player, hud, time, primary, secondary, accent, animated);
        }

        if (!hud.showWatermark && !hud.showPlayer && !hud.showStats) {
            context.drawCenteredString(mc.font, "HUD HIDDEN", w / 2, h / 2, color(primary, 220));
        }

        // Footer.
        context.fill(12, h - 17, w - 12, h - 16, color(primary, 45));
        context.fill(12, h - 12, 16, h - 8, color(secondary, 225));
        context.drawString(mc.font, "CHICKEN CLIENT  //  MASQ", 20, h - 14, color(primary, 200), false);
        context.drawString(mc.font, "P MENU", w - 96, h - 14, 0xFF8E9AAA, false);
        context.drawString(mc.font, String.format(Locale.ROOT, "%.2fx", hud.scale), w - 42, h - 14, 0xFF8E9AAA, false);
    }

    /** A soft diagonal band of light travelling across the panel. */
    private static void lightSweep(GuiGraphics context, int w, int h, int primary, double time) {
        int head = (int) ((time * 46.0) % (w + 80)) - 40;
        for (int i = 0; i < 22; i++) {
            int x = head + i;
            if (x < 1 || x >= w - 1) continue;
            int a = 20 - i;
            if (a <= 0) continue;
            context.fill(x, 1, x + 1, h - 1, color(primary, a));
        }
    }

    private static void drawScanline(GuiGraphics context, int w, int h, int primary, double time) {
        int scan = 6 + (int) ((time * 32.0) % Math.max(1, h - 12));
        context.fill(5, scan, w - 5, scan + 1, color(primary, 62));
        context.fill(5, Math.max(2, scan - 5), w - 5, scan - 3, color(primary, 12));
    }

    private static void drawDust(GuiGraphics context, int w, int h, int primary, int secondary, double time) {
        for (int i = 0; i < 20; i++) {
            double fall = 4.0 + (i % 5) * 2.6;
            int px = 8 + ((i * 61) % (w - 16));
            int py = h - 12 - (int) ((time * fall + i * 17.0) % (h - 24));
            int size = i % 6 == 0 ? 2 : 1;
            context.fill(px, py, px + size, py + size,
                color(i % 2 == 0 ? primary : secondary, 45 + (i % 4) * 26));
        }
    }

    private static void drawBrackets(GuiGraphics context, int primary, int secondary, int w, int h,
                                     boolean animated, double time) {
        int pulse = animated ? 175 + (int) (60 * (0.5 + 0.5 * Math.sin(time * 2.4))) : 220;
        int len = 16;
        int pad = 5;

        hGradient(context, pad, pad, len, 2, color(primary, pulse), color(secondary, 40));
        context.fill(pad, pad, pad + 2, pad + len, color(primary, pulse));
        context.fill(pad + 2, pad + 2, pad + 6, pad + 4, color(primary, pulse / 2));

        hGradient(context, w - pad - len, pad, len, 2, color(secondary, 40), color(secondary, pulse));
        context.fill(w - pad - 2, pad, w - pad, pad + len, color(secondary, pulse));
        context.fill(w - pad - 6, pad + 2, w - pad - 2, pad + 4, color(secondary, pulse / 2));

        hGradient(context, pad, h - pad - 2, len, 2, color(secondary, 40), color(secondary, pulse));
        context.fill(pad, h - pad - len, pad + 2, h - pad, color(secondary, pulse));

        hGradient(context, w - pad - len, h - pad - 2, len, 2, color(primary, pulse), color(secondary, 40));
        context.fill(w - pad - 2, h - pad - len, w - pad, h - pad, color(primary, pulse));

        if (animated) {
            // Tiny ticks that chase each other along the top edge.
            for (int i = 0; i < 6; i++) {
                int tickX = pad + len + 6 + (int) ((time * 22.0 + i * 37.0) % Math.max(1, w - (pad + len) * 2 - 12));
                context.fill(tickX, pad, tickX + 3, pad + 1, color(primary, 45));
            }
        }
    }

    private static void drawWatermark(GuiGraphics context, Minecraft mc, NanoHudConfig hud, boolean leftHand,
                                      double time, int primary, int secondary, boolean animated) {
        if (!hud.showWatermark) return;

        int bob = animated ? (int) Math.round(Math.sin(time * 2.1) * 1.2) : 0;
        drawCloud(context, 16, 9 + bob, primary, secondary, animated, time);

        String word = leftHand ? "LEFT HAND HUD" : "CHICKEN CLIENT";
        int cursor = 38;
        for (int i = 0; i < word.length(); i++) {
            String character = String.valueOf(word.charAt(i));
            float t = i / (float) Math.max(1, word.length() - 1);
            int c = mix(color(primary, 255), color(secondary, 255), t);
            int wave = animated ? (int) Math.round(Math.sin(time * 2.6 + i * 0.42) * 1.2) : 0;
            context.drawString(mc.font, character, cursor, 9 + wave, c, true);
            cursor += mc.font.width(character);
        }

        context.drawString(mc.font, leftHand ? "HELD IN YOUR LEFT HAND" : "NANO HUD  //  YELLOW CORE",
            38, 21, 0xFF8B97A6, false);

        // Live pill.
        int pillX = (int) PANEL_WIDTH - 54;
        int pillY = 8;
        int dotAlpha = animated ? 160 + (int) (85 * (0.5 + 0.5 * Math.sin(time * 3.4))) : 220;
        context.fill(pillX, pillY, pillX + 42, pillY + 12, 0x55000000);
        outline(context, pillX, pillY, pillX + 42, pillY + 12, color(primary, 70));
        context.fill(pillX + 4, pillY + 4, pillX + 8, pillY + 8, color(primary, dotAlpha));
        context.drawString(mc.font, "LIVE", pillX + 11, pillY + 2, color(primary, 225), false);
    }

    private static void drawPlayerCard(GuiGraphics context, Minecraft mc, LocalPlayer player, NanoHudConfig hud,
                                       double time, int primary, int secondary, int accent, boolean animated) {
        int x = 12;
        int y = 38;
        int w = (int) PANEL_WIDTH - 24;
        int h = 60;

        float health = Math.max(0.0f, player.getHealth());
        float maxHealth = Math.max(1.0f, player.getMaxHealth());
        float ratio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        boolean danger = ratio < 0.35f;

        int auraAlpha = animated ? 42 + (int) (30 * (0.5 + 0.5 * Math.sin(time * 2.8))) : 26;

        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, color(primary, auraAlpha));
        context.fillGradient(x, y, x + w, y + h, argb(205, 23, 30, 42), argb(180, 13, 18, 26));
        context.fillGradient(x, y, x + w, 1, color(primary, 130), color(primary, 30));
        context.fill(x, y + h - 1, x + w, y + h, color(secondary, 60));

        drawAvatarWings(context, x + 22, y + 8, hud, time, primary, secondary, animated);

        int ax = x + 5;
        int ay = y + 5;
        int size = 30;
        context.fill(ax, ay, ax + size, ay + size, 0xFF1E2733);
        context.fillGradient(ax + 1, ay + 1, ax + size - 1, ay + size - 1, color(primary, 80), color(secondary, 30));
        outline(context, ax, ay, ax + size, ay + size, color(primary, 70));

        int bob = animated ? (int) Math.round(Math.sin(time * 2.2)) : 0;
        int sway = animated ? (int) Math.round(Math.sin(time * 1.35)) : 0;
        int cx = ax + size / 2 + sway;

        // Little character bust.
        context.fill(cx - 4, ay + 7 + bob, cx + 4, ay + 16 + bob, 0xFFF2ECDD);
        context.fill(cx - 5, ay + 17 + bob, cx + 5, ay + 26 + bob, 0xFFE8E1D0);
        context.fill(cx - 5, ay + 17 + bob, cx + 5, ay + 19 + bob, color(primary, 235));
        context.fill(cx - 3, ay + 22 + bob, cx + 3, ay + 26 + bob, 0xFFD9D2C1);

        boolean blink = animated && (time % 4.4) > 4.18;
        if (!blink) {
            context.fill(cx - 3, ay + 11 + bob, cx - 2, ay + 12 + bob, 0xFF23201A);
            context.fill(cx + 1, ay + 11 + bob, cx + 2, ay + 12 + bob, 0xFF23201A);
        } else {
            context.fill(cx - 3, ay + 11 + bob, cx + 2, ay + 12 + bob, 0xFFB9AFA0);
        }

        // Rotating reticle around the avatar.
        if (animated) {
            for (int i = 0; i < 12; i++) {
                double angle = time * 1.25 + i * Math.PI / 6.0;
                int px = ax + size / 2 + (int) Math.round(Math.cos(angle) * 21.0);
                int py = ay + size / 2 + (int) Math.round(Math.sin(angle) * 21.0);
                int len = i % 3 == 0 ? 3 : 2;
                context.fill(px, py, px + len, py + 1, color(i % 2 == 0 ? primary : secondary, 150 - (i % 3) * 30));
            }

            double ping = (time % 2.4) / 2.4;
            int grow = (int) (ping * 10.0);
            outline(context, ax - grow, ay - grow, ax + size + grow, ay + size + grow,
                color(primary, (int) (100 * (1.0 - ping))));
        }

        int tx = x + 42;
        String name = player.getName().getString();
        context.drawString(mc.font, name, tx, y + 6, 0xFFF5F7FA, true);
        int underline = animated
            ? (int) (Math.max(12, mc.font.width(name)) * (0.55 + 0.45 * (0.5 + 0.5 * Math.sin(time * 2.4))))
            : Math.max(12, mc.font.width(name));
        context.fill(tx, y + 16, tx + underline, y + 17, color(primary, 180));
        context.drawString(mc.font, "LVL " + player.experienceLevel, x + w - 38, y + 6, color(accent, 220), false);
        context.drawString(mc.font, "LOCAL PLAYER", tx, y + 20, 0xFF8B97A6, false);

        drawChips(context, mc, player, ratio, tx, y + 26);

        int barW = 160;
        int barX = tx;
        int healthAlpha = danger
            ? 190 + (int) (60 * (0.5 + 0.5 * Math.sin(time * 7.0)))
            : (animated ? 210 + (int) (30 * (0.5 + 0.5 * Math.sin(time * 4.0))) : 235);
        int healthColor = danger ? color(0xFF4350, healthAlpha) : color(primary, healthAlpha);

        bar(context, barX, y + 40, barW, 5, ratio, healthColor, 0xFF1A222D, time, animated);
        bar(context, barX, y + 47, barW, 3, Math.min(1.0f, player.getArmorValue() / 20.0f),
            color(secondary, 225), 0xFF1A222D, time, animated);
        bar(context, barX, y + 52, barW, 2, player.experienceProgress, color(accent, 220), 0xFF1A222D, time, animated);

        context.drawString(mc.font, String.format(Locale.ROOT, "%.1f", health), x + w - 34, y + 39, color(accent, 235), false);
        context.drawString(mc.font, "ARM " + player.getArmorValue(), x + w - 34, y + 47,
            danger ? color(0xFF4350, 225) : color(primary, 200), false);
    }

    private static void drawChips(GuiGraphics context, Minecraft mc, LocalPlayer player, float ratio, int x, int y) {
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (ratio < 0.35f) {
            labels.add("LOW HP");
            colors.add(0xFFFF4350);
        }
        if (player.isSprinting()) {
            labels.add("SPRINT");
            colors.add(0xFFFFC400);
        }
        if (player.isInWater()) {
            labels.add("SWIM");
            colors.add(0xFF57C8FF);
        }
        if (!player.onGround()) {
            labels.add("AIR");
            colors.add(0xFFFF8A00);
        }
        if (labels.isEmpty()) {
            labels.add("READY");
            colors.add(0xFF8B97A6);
        }

        int cursor = x;
        for (int i = 0; i < Math.min(3, labels.size()); i++) {
            String label = labels.get(i);
            int color = colors.get(i);
            int chipWidth = mc.font.width(label) + 8;
            context.fill(cursor, y, cursor + chipWidth, y + 10, 0x66000000);
            context.fill(cursor, y, cursor + chipWidth, y + 1, color(color, 210));
            outline(context, cursor, y, cursor + chipWidth, y + 10, color(color, 70));
            context.drawString(mc.font, label, cursor + 4, y + 2, color, false);
            cursor += chipWidth + 3;
        }
    }

    private static void drawStats(GuiGraphics context, Minecraft mc, LocalPlayer player, NanoHudConfig hud,
                                  double time, int primary, int secondary, int accent, boolean animated) {
        int x = 12;
        int w = (int) PANEL_WIDTH - 24;
        int y = 100;

        int ping = -1;
        if (mc.getConnection() != null && player != null) {
            var info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) ping = info.getLatency();
        }

        int fps = mc.getFps();
        FPS[fpsIndex] = fps;
        fpsIndex = (fpsIndex + 1) % FPS.length;

        float speed = player == null ? 0.0f
            : (float) Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z) * 20.0f;

        context.fill(x, y, x + w, y + 1, color(primary, 60));

        int col = w / 4;
        stat(context, mc, "PING", ping < 0 ? "--" : ping + "ms", x, y + 5, accent);
        stat(context, mc, "FPS", Integer.toString(fps), x + col, y + 5, primary);
        stat(context, mc, "SPEED", String.format(Locale.ROOT, "%.1f", speed), x + col * 2, y + 5, accent);
        stat(context, mc, "FACING", player == null ? "--" : facing(player.getYRot()), x + col * 3, y + 5,
            animated ? color(secondary, 200 + (int) (40 * (0.5 + 0.5 * Math.sin(time * 2.0)))) : color(secondary, 225));

        int lineY = y + 27;
        context.fill(x, lineY, x + w, lineY + 1, color(primary, 30));

        if (player != null) {
            context.drawString(mc.font, String.format(Locale.ROOT, "XYZ  %.0f  %.0f  %.0f",
                player.getX(), player.getY(), player.getZ()), x, lineY + 5, 0xFF8B97A6, false);
            String dim = player.level().dimension().identifier().toString();
            if (dim.length() > 21) dim = dim.substring(0, 19) + "..";
            context.drawString(mc.font, "DIM  " + dim, x, lineY + 15, 0xFF6F7B8A, false);
        }

        drawFpsGraph(context, x + w - 112, lineY + 4, primary, secondary, animated, time);
        drawContacts(context, mc, player, hud, time, primary, secondary);
    }

    private static void stat(GuiGraphics context, Minecraft mc, String label, String value, int x, int y, int valueColor) {
        context.drawString(mc.font, label, x, y, 0xFF7C8794, false);
        context.drawString(mc.font, value, x, y + 10, valueColor, true);
    }

    private static void drawFpsGraph(GuiGraphics context, int x, int y, int primary, int secondary,
                                     boolean animated, double time) {
        int w = 112;
        int h = 30;
        context.fill(x, y, x + w, y + h, 0x66000000);
        context.fill(x, y, x + w, y + 1, color(primary, 45));

        for (int i = 1; i < 4; i++) {
            int gy = y + (h * i) / 4;
            context.fill(x + 1, gy, x + w - 1, gy + 1, 0x18FFFFFF);
        }

        int previousY = -1;
        for (int i = 0; i < FPS.length; i++) {
            float value = FPS[(fpsIndex + i) % FPS.length];
            int px = x + 2 + i * (w - 4) / FPS.length;
            int py = y + h - 2 - (int) (Math.min(1.0f, value / 240.0f) * (h - 4));
            int c = color(i > FPS.length / 2 ? primary : secondary, 170);

            if (previousY >= 0) {
                int top = Math.min(py, previousY);
                int bottom = Math.max(py, previousY) + 1;
                context.fill(px - 1, top, px, bottom, c);
                // Gradient fill under the line.
                context.fill(px - 1, bottom, px, y + h - 1, color(primary, 16));
            }
            previousY = py;
        }

        if (animated) {
            int cursor = x + 2 + (int) ((time * 52.0) % Math.max(1, w - 4));
            context.fill(cursor, y + 2, cursor + 1, y + h - 2, color(secondary, 75));
        }

        context.drawString(Minecraft.getInstance().font, "FPS", x + 2, y - 8, 0xFF7C8794, false);
        int latest = (int) FPS[Math.max(0, (fpsIndex + FPS.length - 1) % FPS.length)];
        context.drawString(Minecraft.getInstance().font, Integer.toString(latest), x + w - 22, y - 8,
            color(primary, 210), false);
    }

    /** Compact threat radar: nearby players as blips along a bearing strip. */
    private static void drawContacts(GuiGraphics context, Minecraft mc, LocalPlayer player, NanoHudConfig hud,
                                     double time, int primary, int secondary) {
        if (mc.level == null || player == null) return;

        int x = 12;
        int w = (int) PANEL_WIDTH - 24;
        int y = 164;
        int h = 20;
        int lineY = y + 12;

        context.fill(x, y, x + w, y + h, 0x55000000);
        context.fill(x, y, x + w, y + 1, color(primary, 45));
        context.drawString(mc.font, "CONTACTS", x + 4, y + 2, 0xFF7C8794, false);

        context.fill(x + 4, lineY, x + w - 4, lineY + 1, 0x22FFFFFF);
        for (int i = 1; i < 8; i++) {
            int tick = x + 4 + (w - 8) * i / 8;
            context.fill(tick, lineY - 1, tick + 1, lineY + 2, 0x18FFFFFF);
        }

        int center = x + w / 2;
        context.fill(center - 1, lineY - 3, center + 1, lineY + 4, color(primary, 225));
        context.fill(center - 3, lineY - 1, center + 3, lineY + 2, color(primary, 80));

        double yaw = player.getYRot();
        int blips = 0;

        for (var other : mc.level.players()) {
            if (other == player) continue;

            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 48.0 || distance < 0.2) continue;

            double bearing = Math.toDegrees(Math.atan2(-dx, dz));
            double relative = bearing - yaw;
            while (relative > 180.0) relative -= 360.0;
            while (relative < -180.0) relative += 360.0;

            int px = center + (int) Math.round(relative / 90.0 * ((w - 20) / 2.0));
            px = Math.max(x + 5, Math.min(x + w - 6, px));

            boolean close = distance < 8.0;
            int blipColor = close ? 0xFFFF5560 : secondary;
            int blipAlpha = (int) (95 + 150 * (1.0 - Math.min(1.0, distance / 48.0)));
            int size = close ? 2 : 1;
            context.fill(px - 1, lineY - 1, px - 1 + size, lineY - 1 + size, color(blipColor, blipAlpha));

            if (++blips >= 24) break;
        }

        if (blips == 0) {
            context.drawString(mc.font, "NO CONTACTS", center - 30, y + 3, 0xFF5C6672, false);
        }

        if (hud.animations) {
            int sweep = x + 4 + (int) (((time * 0.22) % 1.0) * (w - 8));
            context.fill(sweep, lineY - 3, sweep + 1, lineY + 4, color(primary, 80));
        }
    }

    private static void drawAvatarWings(GuiGraphics context, int cx, int cy, NanoHudConfig hud,
                                        double time, int primary, int secondary, boolean animated) {
        double speed = Math.max(0.15, hud.wingSpeed);
        double amplitude = Math.max(0.1, hud.wingAmplitude);

        context.fill(cx - 13, cy + 2, cx + 13, cy + 16, color(primary, animated ? 26 : 18));

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                double phase = time * 7.2 * speed + i * 0.55 + side * 0.9;
                int flap = animated ? (int) Math.round(Math.sin(phase) * 2.4 * amplitude) : 0;
                int rootX = cx + side * 3;
                int rootY = cy + 7 + i;
                int tipX = rootX + side * (7 + i * 3);
                int tipY = rootY - 3 + i * 3 + flap;
                int left = Math.min(rootX, tipX);
                int right = Math.max(rootX, tipX);

                context.fill(left, tipY, right, tipY + 1, color(i == 0 ? primary : secondary, 205 - i * 28));
                context.fill(left, tipY + 1, right, tipY + 2, color(i == 0 ? primary : secondary, 120 - i * 18));
                context.fill(left, tipY + 2, right, tipY + 3, color(primary, 55));
            }
        }
    }

    private static void drawArm(GuiGraphics context, NanoHudConfig hud, boolean behind) {
        int h = (int) PANEL_HEIGHT;
        int primary = hud.primary;
        int secondary = hud.secondary;

        if (behind) {
            context.fill(-20, h - 26, 54, h + 40, 0xFF3E2C1D);
            context.fill(-12, h - 40, 44, h + 20, 0xFF5B3F26);
            context.fill(-12, h - 40, 44, h - 38, 0xFF7A5733);
            return;
        }

        context.fill(-2, h - 22, 34, h + 8, color(primary, 235));
        context.fill(2, h - 30, 26, h - 20, color(primary, 245));
        context.fill(-2, h - 22, 34, h - 20, 0x55FFFFFF);
        for (int i = 0; i < 3; i++) {
            int yy = h - 16 + i * 7;
            context.fill(30, yy, 46, yy + 5, color(secondary, 225));
            context.fill(30, yy, 46, yy + 1, 0x55FFFFFF);
        }
        context.fill(2, h - 6, 34, h + 6, 0xFF2A2119);
        context.fill(0, h - 34, 30, h - 32, color(secondary, 200));
        context.drawString(Minecraft.getInstance().font, "HAND", 6, h - 12, 0xFF2A2119, false);
        context.fill(0, h - 36, 2, h - 28, color(secondary, 180));
    }

    private static void drawCloud(GuiGraphics context, int x, int y, int primary, int secondary,
                                  boolean animated, double time) {
        int bob = animated ? (int) Math.round(Math.sin(time * 2.6) * 1.0) : 0;
        int glow = animated ? 30 + (int) (20 * (0.5 + 0.5 * Math.sin(time * 3.0))) : 25;
        context.fill(x - 1, y - 1, x + 21, y + 15, color(primary, glow));
        context.fill(x + 3, y + 6 + bob, x + 17, y + 14 + bob, color(primary, 235));
        context.fill(x, y + 8 + bob, x + 20, y + 14 + bob, color(primary, 240));
        context.fill(x + 5, y + 3 + bob, x + 13, y + 9 + bob, color(primary, 245));
        context.fill(x + 12, y + 5 + bob, x + 17, y + 9 + bob, color(secondary, 230));
        context.fill(x + 6, y + 4 + bob, x + 12, y + 6 + bob, 0x88FFFFFF);
    }

    private static void bar(GuiGraphics context, int x, int y, int width, int height, float ratio,
                            int fill, int background, double time, boolean animated) {
        context.fill(x, y, x + width, y + height, background);
        int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (filled <= 0) return;

        context.fill(x, y, x + filled, y + height, fill);
        context.fill(x, y, x + filled, y + 1, 0x33FFFFFF);
        context.fill(x, y + height - 1, x + filled, y + height, 0x33000000);

        if (animated && filled > 8) {
            int shine = x + (int) ((time * 52.0) % filled);
            context.fill(shine, y, Math.min(x + filled, shine + 9), y + height, 0x55FFFFFF);
        }

        for (int i = 1; i < 5; i++) {
            int tx = x + width * i / 5;
            context.fill(tx, y, tx + 1, y + height, 0x33000000);
        }
    }

    private static void hGradient(GuiGraphics context, int x, int y, int width, int height,
                                  int from, int to) {
        int slices = 16;
        for (int i = 0; i < slices; i++) {
            float t = i / (float) (slices - 1);
            int left = x + width * i / slices;
            int right = x + width * (i + 1) / slices;
            context.fill(left, y, right, y + height, mix(from, to, t));
        }
    }

    private static void outline(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }

    private static String facing(float yaw) {
        String[] directions = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int index = (int) Math.round(yaw / 45.0);
        index = ((index % 8) + 8) % 8;
        return directions[index];
    }

    private static int color(int rgb, int alpha) {
        return ((alpha & 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int alphaByte(double alpha) {
        return Math.max(0, Math.min(255, (int) alpha));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    private static int mix(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return argb(a, r, g, b);
    }
}
