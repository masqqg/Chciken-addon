package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;

/**
 * Cosmetic hats drawn on top of the player's head.
 *
 * <p>Five styles share one billboarded geometry kit: a gold crown with gems, a
 * striped party hat with a pompom, a glowing halo, a top hat and a pair of
 * curved devil horns. Hats follow the player's yaw, bob gently and can sparkle.
 * Everything is client side and purely visual.</p>
 */
public class PlayerHat extends Module {
    public enum Style {
        Crown,
        Party,
        Halo,
        TopHat,
        Horns
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
        .name("self")
        .description("Wear the hat yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> others = sgGeneral.add(new BoolSetting.Builder()
        .name("others")
        .description("Put hats on other players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away other players can be.")
        .defaultValue(32.0)
        .range(4.0, 96.0)
        .sliderRange(8.0, 64.0)
        .build()
    );

    private final Setting<Style> style = sgLook.add(new EnumSetting.Builder<Style>()
        .name("style")
        .description("Which hat to draw.")
        .defaultValue(Style.Crown)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall hat size.")
        .defaultValue(1.0)
        .range(0.5, 2.0)
        .sliderRange(0.6, 1.6)
        .build()
    );

    private final Setting<Boolean> bob = sgLook.add(new BoolSetting.Builder()
        .name("bob")
        .description("Gentle floating motion.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("glow")
        .description("Soft aura around the hat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sparkles = sgLook.add(new BoolSetting.Builder()
        .name("sparkles")
        .description("Tiny orbiting sparks around the hat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> primary = sgLook.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(255, 196, 0))
        .build()
    );

    private final Setting<SettingColor> secondary = sgLook.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 138, 0))
        .build()
    );

    private final Setting<SettingColor> accent = sgLook.add(new ColorSetting.Builder()
        .name("accent")
        .defaultValue(new SettingColor(255, 249, 226))
        .build()
    );

    public PlayerHat() {
        super(AddonTemplate.CATEGORY, "player-hat", "Cosmetic crowns, party hats, halos, top hats and horns for players.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (self.get()) drawHat(event, mc.player);

        if (others.get()) {
            double rangeSq = range.get() * range.get();

            for (Player player : mc.level.players()) {
                if (player == mc.player) continue;
                if (mc.player.distanceToSqr(player) > rangeSq) continue;
                drawHat(event, player);
            }
        }
    }

    private void drawHat(Render3DEvent event, Player player) {
        double time = System.nanoTime() / 1_000_000_000.0;
        double s = scale.get();

        double yaw = Math.toRadians(player.getYRot());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double wobble = bob.get() ? Math.sin(time * 2.2 + player.getId() * 0.7) * 0.018 * s : 0.0;
        double baseY = player.getY() + 1.79 * s + wobble;
        double cx = player.getX();
        double cz = player.getZ();

        switch (style.get()) {
            case Crown -> drawCrown(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, s);
            case Party -> drawParty(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, s);
            case Halo -> drawHalo(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, s, time);
            case TopHat -> drawTopHat(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, s);
            case Horns -> drawHorns(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, s);
        }

        if (sparkles.get()) drawSparkles(event, cx, baseY, cz, s, time);
    }

    private void drawCrown(Render3DEvent event, double cx, double baseY, double cz,
                           double rightX, double rightZ, double forwardX, double forwardZ, double s) {
        Color gold = primary.get();
        Color gem = accent.get();
        double radius = 0.30 * s;
        double bandTop = baseY + 0.10 * s;

        band(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, radius, 0.10 * s, 14,
            withAlpha(gold, 240), withAlpha(gold, 160));

        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0 + Math.PI / 8.0;
            double[] p0 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, angle - 0.24, radius);
            double[] p1 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, angle + 0.24, radius);
            double[] mid = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, angle, radius * 0.3);

            double height = (i % 2 == 0 ? 0.24 : 0.15) * s;
            double tipY = bandTop + height;
            Color base = withAlpha(gold, 245);
            Color tip = withAlpha(gem, 255);

            event.renderer.quad(p0[0], bandTop, p0[1], p1[0], bandTop, p1[1],
                mid[0], tipY, mid[1], mid[0], tipY, mid[1], base, base, tip, tip);

            if (i % 2 == 0) {
                double[] gemPos = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, angle, radius + 0.005);
                event.renderer.quad(
                    gemPos[0] - 0.022 * s, baseY + 0.02 * s, gemPos[1],
                    gemPos[0] + 0.022 * s, baseY + 0.02 * s, gemPos[1],
                    gemPos[0] + 0.022 * s, baseY + 0.08 * s, gemPos[1],
                    gemPos[0] - 0.022 * s, baseY + 0.08 * s, gemPos[1],
                    withAlpha(gem, 245));
            }
        }

        if (glow.get()) drawGlow(event, cx, bandTop, cz, radius * 1.9, withAlpha(gold, 26));
    }

    private void drawParty(Render3DEvent event, double cx, double baseY, double cz,
                           double rightX, double rightZ, double forwardX, double forwardZ, double s) {
        double radius = 0.25 * s;
        double height = 0.58 * s;
        int segments = 12;

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double[] p0 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a0, radius);
            double[] p1 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a1, radius);

            Color color = i % 2 == 0 ? withAlpha(primary.get(), 240) : withAlpha(secondary.get(), 225);
            event.renderer.quad(p0[0], baseY, p0[1], p1[0], baseY, p1[1],
                cx, baseY + height, cz, cx, baseY + height, cz,
                color, color, withAlpha(accent.get(), 235), withAlpha(accent.get(), 235));
        }

        band(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, radius * 1.02, 0.045 * s, segments,
            withAlpha(accent.get(), 235), withAlpha(secondary.get(), 190));
        drawGlow(event, cx, baseY + height, cz, 0.075 * s, withAlpha(accent.get(), 235));

        if (glow.get()) drawGlow(event, cx, baseY + height * 0.5, cz, radius * 2.0, withAlpha(primary.get(), 22));
    }

    private void drawHalo(Render3DEvent event, double cx, double baseY, double cz,
                          double rightX, double rightZ, double forwardX, double forwardZ,
                          double s, double time) {
        double y = baseY + 0.26 * s;
        double radius = 0.36 * s;
        int segments = 30;

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double[] p0 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a0, radius);
            double[] p1 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a1, radius);

            double wave0 = Math.sin(time * 1.8 + a0 * 2.0) * 0.014 * s;
            double wave1 = Math.sin(time * 1.8 + a1 * 2.0) * 0.014 * s;

            event.renderer.line(p0[0], y + wave0, p0[1], p1[0], y + wave1, p1[1],
                withAlpha(primary.get(), 225));
        }

        if (glow.get()) {
            drawGlow(event, cx, y, cz, radius * 1.7, withAlpha(primary.get(), 30));
            drawGlow(event, cx, y, cz, radius * 0.9, withAlpha(accent.get(), 40));
        }
    }

    private void drawTopHat(Render3DEvent event, double cx, double baseY, double cz,
                            double rightX, double rightZ, double forwardX, double forwardZ, double s) {
        Color dark = new Color(24, 22, 28);
        double brimRadius = 0.42 * s;
        double crownRadius = 0.25 * s;

        band(event, cx, baseY, cz, rightX, rightZ, forwardX, forwardZ, brimRadius, 0.02 * s, 18,
            withAlpha(dark, 235), withAlpha(dark, 170));
        disc(event, cx, baseY + 0.02 * s, cz, rightX, rightZ, forwardX, forwardZ, brimRadius, 18, withAlpha(dark, 200));

        band(event, cx, baseY + 0.02 * s, cz, rightX, rightZ, forwardX, forwardZ, crownRadius, 0.34 * s, 16,
            withAlpha(dark, 240), withAlpha(dark, 165));
        band(event, cx, baseY + 0.05 * s, cz, rightX, rightZ, forwardX, forwardZ, crownRadius * 1.03, 0.05 * s, 16,
            withAlpha(primary.get(), 235), withAlpha(secondary.get(), 200));
        disc(event, cx, baseY + 0.37 * s, cz, rightX, rightZ, forwardX, forwardZ, crownRadius, 16,
            withAlpha(dark, 235));

        if (glow.get()) drawGlow(event, cx, baseY + 0.20 * s, cz, crownRadius * 2.2, withAlpha(primary.get(), 20));
    }

    private void drawHorns(Render3DEvent event, double cx, double baseY, double cz,
                           double rightX, double rightZ, double forwardX, double forwardZ, double s) {
        Color horn = primary.get();
        Color tip = accent.get();

        for (int side = -1; side <= 1; side += 2) {
            double rootX = cx + rightX * side * 0.17 * s;
            double rootZ = cz + rightZ * side * 0.17 * s;
            double y = baseY - 0.04 * s;

            double dirX = rightX * side * 0.42 - forwardX * 0.10;
            double dirZ = rightZ * side * 0.42 - forwardZ * 0.10;

            double previousX = rootX;
            double previousZ = rootZ;
            double previousY = y;
            double previousWidth = 0.06 * s;

            int segments = 4;
            for (int i = 1; i <= segments; i++) {
                double f = i / (double) segments;
                double curve = Math.sin(f * Math.PI * 0.8) * 0.10 * s;

                double pointX = rootX + dirX * 0.42 * s * f - forwardX * curve;
                double pointZ = rootZ + dirZ * 0.42 * s * f - forwardZ * curve;
                double pointY = y + f * 0.34 * s;
                double width = 0.06 * s * Math.pow(1.0 - f, 1.3);

                int alpha = (int) (235 * (1.0 - f * 0.55));
                Color near = withAlpha(horn, alpha);
                Color far = withAlpha(mix(horn, tip, (float) f), alpha);

                event.renderer.quad(
                    previousX - previousWidth, previousY, previousZ - previousWidth,
                    previousX + previousWidth, previousY, previousZ + previousWidth,
                    pointX + width, pointY, pointZ + width,
                    pointX - width, pointY, pointZ - width,
                    near, near, far, far);

                event.renderer.quad(
                    previousX + previousWidth, previousY, previousZ - previousWidth,
                    previousX - previousWidth, previousY, previousZ + previousWidth,
                    pointX - width, pointY, pointZ + width,
                    pointX + width, pointY, pointZ - width,
                    near, near, far, far);

                previousX = pointX;
                previousY = pointY;
                previousZ = pointZ;
                previousWidth = width;
            }
        }

        if (glow.get()) drawGlow(event, cx, baseY + 0.10 * s, cz, 0.5 * s, withAlpha(horn, 18));
    }

    private void drawSparkles(Render3DEvent event, double cx, double baseY, double cz, double s, double time) {
        for (int i = 0; i < 6; i++) {
            double angle = time * 1.4 + i * Math.PI / 3.0;
            double radius = 0.38 * s;
            double px = cx + Math.cos(angle) * radius;
            double pz = cz + Math.sin(angle) * radius;
            double py = baseY + 0.20 * s + Math.sin(time * 2.0 + i) * 0.05 * s;

            int alpha = (int) (110 + 100 * (0.5 + 0.5 * Math.sin(time * 3.0 + i * 1.3)));
            double size = 0.012 * s * (1.0 + 0.5 * Math.sin(time * 2.4 + i));

            event.renderer.quad(px - size, py, pz, px, py - size, pz, px + size, py, pz, px, py + size, pz,
                withAlpha(accent.get(), alpha));
            event.renderer.quad(px, py - size, pz - size, px, py + size, pz - size,
                px, py + size, pz + size, px, py - size, pz + size,
                withAlpha(primary.get(), alpha / 2));
        }
    }

    private void drawGlow(Render3DEvent event, double cx, double cy, double cz, double radius, Color color) {
        event.renderer.quad(cx - radius, cy, cz, cx, cy - radius * 0.6, cz,
            cx + radius, cy, cz, cx, cy + radius * 0.6, cz, color);
        event.renderer.quad(cx, cy - radius * 0.6, cz - radius, cx, cy + radius * 0.6, cz - radius,
            cx, cy + radius * 0.6, cz + radius, cx, cy - radius * 0.6, cz + radius, color);
    }

    private static void band(Render3DEvent event, double cx, double y, double cz,
                             double rightX, double rightZ, double forwardX, double forwardZ,
                             double radius, double height, int segments, Color top, Color bottom) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double[] p0 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a0, radius);
            double[] p1 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a1, radius);

            event.renderer.quad(
                p0[0], y, p0[1],
                p1[0], y, p1[1],
                p1[0], y + height, p1[1],
                p0[0], y + height, p0[1],
                bottom, bottom, top, top);
        }
    }

    private static void disc(Render3DEvent event, double cx, double cy, double cz,
                             double rightX, double rightZ, double forwardX, double forwardZ,
                             double radius, int segments, Color color) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double[] p0 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a0, radius);
            double[] p1 = polar(cx, cz, rightX, rightZ, forwardX, forwardZ, a1, radius);

            event.renderer.quad(cx, cy, cz, p0[0], cy, p0[1], p1[0], cy, p1[1], cx, cy, cz, color);
        }
    }

    private static double[] polar(double cx, double cz, double rightX, double rightZ,
                                  double forwardX, double forwardZ, double angle, double radius) {
        double out = Math.cos(angle) * radius;
        double back = Math.sin(angle) * radius;
        return new double[]{cx + rightX * out + forwardX * back, cz + rightZ * out + forwardZ * back};
    }

    private static Color mix(Color from, Color to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, Math.max(0, Math.min(255, alpha)));
    }
}
