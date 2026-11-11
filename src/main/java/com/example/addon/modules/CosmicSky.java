package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A cinematic skybox layer: planets with rings, drifting aurora ribbons,
 * twinkling stars and occasional shooting stars. The geometry is billboarded
 * around the player so it behaves like a distant atmosphere rather than a
 * nearby ESP shape.
 */
public class CosmicSky extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<Boolean> planets = sgGeneral.add(new BoolSetting.Builder()
        .name("planets")
        .description("Draw large ringed planets in the sky.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> aurora = sgGeneral.add(new BoolSetting.Builder()
        .name("aurora")
        .description("Draw slowly moving aurora ribbons along the horizon.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stars = sgGeneral.add(new BoolSetting.Builder()
        .name("stars")
        .description("Draw twinkling stars.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shootingStars = sgGeneral.add(new BoolSetting.Builder()
        .name("shooting-stars")
        .description("Draw occasional shooting stars.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> distance = sgLook.add(new DoubleSetting.Builder()
        .name("distance")
        .description("Sky distance from the player.")
        .defaultValue(72.0)
        .range(32.0, 160.0)
        .sliderRange(48.0, 120.0)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .defaultValue(1.0)
        .range(0.4, 2.5)
        .sliderRange(0.6, 1.8)
        .build()
    );

    private final Setting<Double> speed = sgLook.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .defaultValue(1.0)
        .range(0.1, 3.0)
        .sliderRange(0.3, 2.0)
        .build()
    );

    private final Setting<Integer> starCount = sgLook.add(new IntSetting.Builder()
        .name("star-count")
        .defaultValue(90)
        .range(20, 200)
        .sliderRange(40, 160)
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

    private final Setting<SettingColor> auroraColor = sgLook.add(new ColorSetting.Builder()
        .name("aurora-color")
        .defaultValue(new SettingColor(255, 220, 70))
        .build()
    );

    private static final double[][] PLANETS = {
        {-0.62, 0.34, 0.70, 4.8, 0.00},
        {0.78, 0.18, 0.55, 3.2, 0.55},
        {-0.18, 0.62, -0.76, 2.3, 1.20}
    };

    public CosmicSky() {
        super(AddonTemplate.CATEGORY, "cosmic-sky", "Cinematic planets, aurora ribbons and stars in the sky.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        Player player = mc.player;
        Vec3 eye = new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        double time = System.nanoTime() / 1_000_000_000.0;
        double spd = speed.get();
        double dist = distance.get();
        double s = scale.get();

        Renderer3D renderer = event.depthRenderer != null ? event.depthRenderer : event.renderer;

        if (stars.get()) drawStars(renderer, eye, dist, time, spd);
        if (aurora.get()) drawAurora(renderer, eye, dist, time, spd, s);
        if (planets.get()) drawPlanets(renderer, eye, dist, time, spd, s);
        if (shootingStars.get()) drawShootingStars(renderer, eye, dist, time, spd);
    }

    private void drawPlanets(Renderer3D renderer, Vec3 eye, double dist, double time, double speed, double scale) {
        Color a = primary.get();
        Color b = secondary.get();

        for (int i = 0; i < PLANETS.length; i++) {
            double[] p = PLANETS[i];
            Vec3 dir = new Vec3(p[0], p[1], p[2]).normalize();
            double radius = p[3] * scale;
            double phase = p[4];

            Vec3 center = eye.add(dir.scale(dist));
            Vec3 right = perpendicular(dir);
            Vec3 up = right.cross(dir).normalize();

            // Outer glow.
            drawDisc(renderer, center, right, up, radius * 1.55,
                withAlpha(a, 22), withAlpha(b, 8));

            // Planet surface, split into bands for a painted look.
            for (int band = 0; band < 5; band++) {
                double t0 = band / 5.0;
                double t1 = (band + 1) / 5.0;
                double y0 = -radius + t0 * radius * 2.0;
                double y1 = -radius + t1 * radius * 2.0;
                double h0 = Math.sqrt(Math.max(0.0, radius * radius - y0 * y0));
                double h1 = Math.sqrt(Math.max(0.0, radius * radius - y1 * y1));
                float mix = (float) ((t0 + t1) * 0.5);
                Color bandColor = mix(withAlpha(a, 235), withAlpha(b, 210), mix);

                renderer.quad(
                    center.x - right.x * h0 + up.x * y0, center.y - right.y * h0 + up.y * y0, center.z - right.z * h0 + up.z * y0,
                    center.x + right.x * h0 + up.x * y0, center.y + right.y * h0 + up.y * y0, center.z + right.z * h0 + up.z * y0,
                    center.x + right.x * h1 + up.x * y1, center.y + right.y * h1 + up.y * y1, center.z + right.z * h1 + up.z * y1,
                    center.x - right.x * h1 + up.x * y1, center.y - right.y * h1 + up.y * y1, center.z - right.z * h1 + up.z * y1,
                    bandColor
                );
            }

            // Thin ring, drawn as small billboard segments in the planet plane.
            if (i != 1) {
                int segments = 40;
                double ringRadius = radius * 1.55;
                double flatten = 0.26;
                Color ringColor = withAlpha(mix(a, Color.WHITE, 0.45f), 125);
                for (int segment = 0; segment < segments; segment++) {
                    double angle0 = Math.PI * 2.0 * segment / segments + time * 0.04 * speed + phase;
                    double angle1 = Math.PI * 2.0 * (segment + 1) / segments + time * 0.04 * speed + phase;
                    Vec3 p0 = center.add(right.scale(Math.cos(angle0) * ringRadius)).add(up.scale(Math.sin(angle0) * ringRadius * flatten));
                    Vec3 p1 = center.add(right.scale(Math.cos(angle1) * ringRadius)).add(up.scale(Math.sin(angle1) * ringRadius * flatten));
                    renderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, ringColor);
                }
            }
        }
    }

    private void drawAurora(Renderer3D renderer, Vec3 eye, double dist, double time, double speed, double scale) {
        Color color = auroraColor.get();

        for (int ribbon = 0; ribbon < 3; ribbon++) {
            double baseAngle = Math.PI * 0.30 + ribbon * 0.42;
            double height = (11.0 + ribbon * 3.5) * scale;
            int segments = 18;

            for (int i = 0; i < segments; i++) {
                double t0 = i / (double) segments;
                double t1 = (i + 1) / (double) segments;
                double wave0 = Math.sin(time * 0.45 * speed + t0 * 5.0 + ribbon) * 2.4 * scale;
                double wave1 = Math.sin(time * 0.45 * speed + t1 * 5.0 + ribbon) * 2.4 * scale;

                double a0 = baseAngle + (t0 - 0.5) * 1.15;
                double a1 = baseAngle + (t1 - 0.5) * 1.15;
                Vec3 d0 = new Vec3(Math.cos(a0), 0.33, Math.sin(a0)).normalize();
                Vec3 d1 = new Vec3(Math.cos(a1), 0.33, Math.sin(a1)).normalize();

                Vec3 p0 = eye.add(d0.scale(dist)).add(0.0, wave0, 0.0);
                Vec3 p1 = eye.add(d1.scale(dist)).add(0.0, wave1, 0.0);
                Vec3 p2 = p1.add(0.0, height, 0.0);
                Vec3 p3 = p0.add(0.0, height, 0.0);

                int alpha = 28 + (int) (18 * (0.5 + 0.5 * Math.sin(time * 1.3 + t0 * 4.0)));
                renderer.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z,
                    withAlpha(color, alpha), withAlpha(color, alpha),
                    withAlpha(color, 2), withAlpha(color, 2));
            }
        }
    }

    private void drawStars(Renderer3D renderer, Vec3 eye, double dist, double time, double speed) {
        int count = starCount.get();
        for (int i = 0; i < count; i++) {
            double seed = i * 0.61803398875;
            double yaw = (seed * 17.0) % (Math.PI * 2.0);
            double pitch = (seed * 9.0) % 1.35 + 0.08;
            double twinkle = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(time * 2.2 * speed + i * 1.7));
            double size = 0.045 + (i % 4) * 0.018;

            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            Vec3 center = eye.add(dir.scale(dist));
            Vec3 right = perpendicular(dir);
            Vec3 up = right.cross(dir).normalize();
            Color color = withAlpha(mix(Color.WHITE, primary.get(), (i % 5) / 5.0f), (int) (185 * twinkle));

            renderer.quad(
                center.x - right.x * size - up.x * size, center.y - right.y * size - up.y * size, center.z - right.z * size - up.z * size,
                center.x + right.x * size - up.x * size, center.y + right.y * size - up.y * size, center.z + right.z * size - up.z * size,
                center.x + right.x * size + up.x * size, center.y + right.y * size + up.y * size, center.z + right.z * size + up.z * size,
                center.x - right.x * size + up.x * size, center.y - right.y * size + up.y * size, center.z - right.z * size + up.z * size,
                color
            );
        }
    }

    private void drawShootingStars(Renderer3D renderer, Vec3 eye, double dist, double time, double speed) {
        for (int i = 0; i < 2; i++) {
            double cycle = (time * (0.12 + i * 0.035) * speed + i * 0.57) % 1.0;
            if (cycle > 0.18) continue;

            double yaw = -Math.PI * 0.8 + i * 1.9 + cycle * 2.2;
            double pitch = 0.75 + i * 0.2;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            Vec3 start = eye.add(dir.scale(dist));
            Vec3 direction = new Vec3(-0.45 - i * 0.2, -0.08, 0.7).normalize();
            Vec3 end = start.add(direction.scale(9.0));
            double alpha = Math.max(0.0, 1.0 - cycle / 0.18) * 220;

            renderer.line(start.x, start.y, start.z, end.x, end.y, end.z, withAlpha(Color.WHITE, (int) alpha));
            renderer.line(start.x, start.y, start.z, start.x + direction.x * 3.0, start.y + direction.y * 3.0, start.z + direction.z * 3.0,
                withAlpha(primary.get(), (int) (alpha * 0.35)));
        }
    }

    private static void drawDisc(Renderer3D renderer, Vec3 center, Vec3 right, Vec3 up, double radius, Color a, Color b) {
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double angle0 = Math.PI * 2.0 * i / segments;
            double angle1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = center.add(right.scale(Math.cos(angle0) * radius)).add(up.scale(Math.sin(angle0) * radius));
            Vec3 p1 = center.add(right.scale(Math.cos(angle1) * radius)).add(up.scale(Math.sin(angle1) * radius));
            renderer.quad(center.x, center.y, center.z, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z,
                center.x, center.y, center.z, i % 2 == 0 ? a : b);
        }
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) > 0.92 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        return direction.cross(reference).normalize();
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
