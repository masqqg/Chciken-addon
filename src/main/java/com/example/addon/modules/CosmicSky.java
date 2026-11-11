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

    private final Setting<Boolean> nebula = sgGeneral.add(new BoolSetting.Builder()
        .name("nebula")
        .description("Draw soft drifting nebula clouds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> comets = sgGeneral.add(new BoolSetting.Builder()
        .name("comets")
        .description("Draw long-tailed comets across the sky.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> moons = sgGeneral.add(new BoolSetting.Builder()
        .name("moons")
        .description("Draw small moons orbiting the planets.")
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

    private final SettingGroup sgHell = settings.createGroup("Hell Gate");

    private final Setting<Boolean> hellGate = sgHell.add(new BoolSetting.Builder()
        .name("hell-gate")
        .description("A dark burning void with a small white aperture that a four-tailed star blazes through.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hellSmoke = sgHell.add(new BoolSetting.Builder()
        .name("hell-smoke")
        .description("Layered smoke and ember veins swirling around the aperture.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hellEmbers = sgHell.add(new BoolSetting.Builder()
        .name("tail-sparks")
        .description("Sparkles drifting along the star tails.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> hellAperture = sgHell.add(new DoubleSetting.Builder()
        .name("aperture-size")
        .description("Size of the white opening.")
        .defaultValue(3.2)
        .range(1.0, 9.0)
        .sliderRange(1.5, 6.0)
        .build()
    );

    private final Setting<Double> hellStarSize = sgHell.add(new DoubleSetting.Builder()
        .name("star-size")
        .description("Size of the star core.")
        .defaultValue(6.0)
        .range(2.0, 16.0)
        .sliderRange(3.0, 11.0)
        .build()
    );

    private final Setting<Double> hellTailLength = sgHell.add(new DoubleSetting.Builder()
        .name("tail-length")
        .description("Length of the four tails.")
        .defaultValue(26.0)
        .range(6.0, 60.0)
        .sliderRange(10.0, 40.0)
        .build()
    );

    private final Setting<SettingColor> hellColor = sgHell.add(new ColorSetting.Builder()
        .name("hell-color")
        .description("Colour of the burning void.")
        .defaultValue(new SettingColor(126, 26, 12))
        .build()
    );

    private final Setting<SettingColor> hellStarColor = sgHell.add(new ColorSetting.Builder()
        .name("star-color")
        .description("Core colour of the star and the aperture.")
        .defaultValue(new SettingColor(255, 251, 238))
        .build()
    );

    private final Setting<SettingColor> hellTailColor = sgHell.add(new ColorSetting.Builder()
        .name("tail-color")
        .description("Colour of the tails and the aperture glow.")
        .defaultValue(new SettingColor(255, 172, 58))
        .build()
    );

    private static final double[] HELL_TAIL_LENGTH = {1.0, 0.82, 0.94, 0.74};
    private static final double[] HELL_TAIL_BEND = {0.55, -0.42, 0.35, -0.6};

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

        if (hellGate.get()) drawHellGate(renderer, eye, dist, time, spd, s);
        if (stars.get()) drawStars(renderer, eye, dist, time, spd);
        if (nebula.get()) drawNebula(renderer, eye, dist, time, spd, s);
        if (aurora.get()) drawAurora(renderer, eye, dist, time, spd, s);
        if (planets.get()) drawPlanets(renderer, eye, dist, time, spd, s);
        if (comets.get()) drawComets(renderer, eye, dist, time, spd, s);
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

            if (moons.get()) {
                double orbit = time * 0.35 * speed + phase * 2.0;
                Vec3 moon = center.add(right.scale(Math.cos(orbit) * radius * 2.15))
                    .add(up.scale(Math.sin(orbit) * radius * 1.25));
                drawDisc(renderer, moon, right, up, radius * 0.22,
                    withAlpha(Color.WHITE, 225), withAlpha(b, 150), 14);
            }
        }
    }

    private void drawNebula(Renderer3D renderer, Vec3 eye, double dist, double time, double speed, double scale) {
        Color a = primary.get();
        Color b = secondary.get();

        for (int patch = 0; patch < 3; patch++) {
            double angle = Math.PI * 0.35 + patch * 0.95;
            Vec3 dir = new Vec3(Math.cos(angle), 0.18 + patch * 0.16, Math.sin(angle)).normalize();
            Vec3 center = eye.add(dir.scale(dist * 0.88));
            Vec3 right = perpendicular(dir);
            Vec3 up = right.cross(dir).normalize();
            double radius = (13.0 + patch * 5.0) * scale;

            for (int layer = 0; layer < 6; layer++) {
                double drift = time * 0.04 * speed + layer * 0.45 + patch * 1.3;
                Vec3 cloud = center
                    .add(right.scale(Math.cos(drift) * layer * 0.7))
                    .add(up.scale(Math.sin(drift) * layer * 0.55));
                double layerRadius = radius * (1.0 - layer * 0.11);
                Color color = withAlpha(mix(a, b, (patch + layer) / 9.0f), 10 + layer * 3);
                drawDisc(renderer, cloud, right, up, layerRadius, color, withAlpha(color, 4), 14);
            }
        }
    }

    private void drawComets(Renderer3D renderer, Vec3 eye, double dist, double time, double speed, double scale) {
        for (int i = 0; i < 3; i++) {
            double cycle = (time * (0.055 + i * 0.018) * speed + i * 0.41) % 1.0;
            double yaw = -Math.PI * 0.7 + i * 1.75 + cycle * 2.8;
            double pitch = 0.72 + i * 0.22;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            Vec3 start = eye.add(dir.scale(dist));
            Vec3 direction = new Vec3(-0.5 - i * 0.15, -0.12, 0.68).normalize();
            double length = (12.0 + i * 5.0) * scale;

            for (int segment = 0; segment < 8; segment++) {
                double t0 = segment / 8.0;
                double t1 = (segment + 1) / 8.0;
                Vec3 p0 = start.add(direction.scale(length * t0));
                Vec3 p1 = start.add(direction.scale(length * t1));
                Color color = withAlpha(mix(primary.get(), Color.WHITE, (float) (1.0 - t0)),
                    (int) (185 * (1.0 - t0)));
                renderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, color);
            }

            Vec3 head = start.add(direction.scale(length));
            drawDisc(renderer, head, perpendicular(dir), perpendicular(dir).cross(dir).normalize(),
                0.55 * scale, withAlpha(Color.WHITE, 235), withAlpha(secondary.get(), 170), 10);
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
            Color color = withAlpha(mix(mix(Color.WHITE, primary.get(), (i % 5) / 5.0f), secondary.get(), (i % 3) / 6.0f), (int) (185 * twinkle));

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

    /**
     * A hellish vista: a dark burning void with a small white aperture, and a
     * huge four-tailed star blazing out of it. Everything is billboarded around
     * the player like the rest of the sky layers, so it sits at the horizon.
     */
    private void drawHellGate(Renderer3D renderer, Vec3 eye, double dist, double time, double speed, double scale) {
        Vec3 dir = new Vec3(-0.52, 0.40, 0.78).normalize();
        Vec3 center = eye.add(dir.scale(dist * 0.92));
        Vec3 right = perpendicular(dir);
        Vec3 up = right.cross(dir).normalize();

        Color hell = hellColor.get();
        Color starColor = hellStarColor.get();
        Color tailColor = hellTailColor.get();
        Color smoke = new Color(10, 5, 3);

        double pulse = 0.9 + 0.1 * Math.sin(time * 1.7 * speed);
        double aperture = hellAperture.get() * scale * pulse;
        double star = hellStarSize.get() * scale * pulse;
        double tailLength = hellTailLength.get() * scale;

        if (hellSmoke.get()) {
            // Smoke gets darker the further it drifts from the aperture light.
            for (int i = 8; i >= 1; i--) {
                double radius = aperture * (1.8 + i * 1.6);
                int alpha = 5 + (9 - i) * 2;
                drawDisc(renderer, center, right, up, radius,
                    withAlpha(hell, alpha), withAlpha(mix(hell, smoke, 0.6f), alpha + 3), 30);
            }

            // Ember veins crawling outwards through the smoke.
            int veins = 22;
            for (int i = 0; i < veins; i++) {
                double angle = Math.PI * 2.0 * i / veins + time * 0.05 * speed;
                double inner = aperture * 1.3;
                double outer = aperture * (3.4 + (i % 5) * 1.3);
                double wobble = Math.sin(time * 0.8 * speed + i * 1.7) * aperture * 0.3;

                Vec3 p0 = center.add(right.scale(Math.cos(angle) * inner)).add(up.scale(Math.sin(angle) * inner));
                Vec3 p1 = center
                    .add(right.scale(Math.cos(angle + 0.16) * outer + wobble))
                    .add(up.scale(Math.sin(angle + 0.16) * outer - wobble * 0.5));

                renderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z,
                    withAlpha(mix(hell, tailColor, 0.45f), 24 + (i % 4) * 9));
            }
        }

        // The white opening: a warm halo stepping down into a hard white core.
        drawDisc(renderer, center, right, up, aperture * 2.5,
            withAlpha(tailColor, 22), withAlpha(tailColor, 16), 40);
        drawDisc(renderer, center, right, up, aperture * 1.65,
            withAlpha(mix(tailColor, starColor, 0.45f), 48), withAlpha(tailColor, 34), 40);
        drawDisc(renderer, center, right, up, aperture,
            withAlpha(starColor, 190), withAlpha(starColor, 225), 36);
        drawDisc(renderer, center, right, up, aperture * 0.58,
            withAlpha(Color.WHITE, 240), withAlpha(Color.WHITE, 255), 30);

        // Two slowly counter-rotating halo rings.
        for (int ring = 0; ring < 2; ring++) {
            double radius = star * (1.8 + ring * 0.75);
            int segments = 44;
            int alpha = 42 - ring * 14;
            double spin = time * (0.10 + ring * 0.06) * speed;

            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2.0 * i / segments + spin;
                double a1 = Math.PI * 2.0 * (i + 1) / segments + spin;
                Vec3 p0 = center.add(right.scale(Math.cos(a0) * radius * 0.55)).add(up.scale(Math.sin(a0) * radius));
                Vec3 p1 = center.add(right.scale(Math.cos(a1) * radius * 0.55)).add(up.scale(Math.sin(a1) * radius));
                renderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, withAlpha(tailColor, alpha));
            }
        }

        // Star glow and core.
        drawDisc(renderer, center, right, up, star * 1.9,
            withAlpha(starColor, 20), withAlpha(tailColor, 14), 36);
        drawDisc(renderer, center, right, up, star * 0.95,
            withAlpha(starColor, 55), withAlpha(tailColor, 40), 36);
        drawDisc(renderer, center, right, up, star * 0.46,
            withAlpha(starColor, 150), withAlpha(starColor, 120), 28);
        drawDisc(renderer, center, right, up, star * 0.2,
            withAlpha(Color.WHITE, 245), withAlpha(Color.WHITE, 255), 22);

        // Four long rays for the starburst.
        for (int ray = 0; ray < 4; ray++) {
            double angle = ray * Math.PI / 2.0 + time * 0.06 * speed;
            double length = star * (ray % 2 == 0 ? 3.4 : 1.6);

            Vec3 d = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 side = right.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle)));
            Vec3 a = center.add(side.scale(star * 0.09));
            Vec3 b = center.subtract(side.scale(star * 0.09));
            Vec3 tip = center.add(d.scale(length));

            renderer.quad(a.x, a.y, a.z, b.x, b.y, b.z, tip.x, tip.y, tip.z, tip.x, tip.y, tip.z,
                withAlpha(starColor, 110), withAlpha(starColor, 110), withAlpha(tailColor, 0), withAlpha(tailColor, 0));
        }

        // Four curved, tapering tails with drifting sparks.
        int segments = 18;

        for (int tailIndex = 0; tailIndex < 4; tailIndex++) {
            double baseAngle = tailIndex * Math.PI / 2.0 + Math.PI / 4.0 + time * 0.08 * speed;
            double length = tailLength * HELL_TAIL_LENGTH[tailIndex];
            double bend = HELL_TAIL_BEND[tailIndex];

            Vec3 tailDir = right.scale(Math.cos(baseAngle)).add(up.scale(Math.sin(baseAngle)));
            Vec3 tailSide = right.scale(-Math.sin(baseAngle)).add(up.scale(Math.cos(baseAngle)));

            Vec3 previous = center.add(tailDir.scale(star * 0.3));
            double previousWidth = star * 0.45;

            for (int i = 1; i <= segments; i++) {
                double f = i / (double) segments;
                double curl = Math.sin(f * Math.PI * 1.35) * bend * length * 0.2;
                Vec3 point = center.add(tailDir.scale(star * 0.3 + length * f)).add(tailSide.scale(curl));
                double width = star * 0.45 * Math.pow(1.0 - f, 1.4);

                Color nearColor = mix(starColor, tailColor, (float) Math.min(1.0, f * 1.5));
                Color farColor = mix(tailColor, hell, (float) Math.min(1.0, f * 1.1));
                int alpha = (int) (220 * Math.pow(1.0 - f, 1.4));

                Vec3 p0 = previous.add(tailSide.scale(previousWidth));
                Vec3 p1 = previous.subtract(tailSide.scale(previousWidth));
                Vec3 p2 = point.subtract(tailSide.scale(width));
                Vec3 p3 = point.add(tailSide.scale(width));

                renderer.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z,
                    withAlpha(nearColor, alpha), withAlpha(nearColor, alpha),
                    withAlpha(farColor, alpha), withAlpha(farColor, alpha));

                if (i % 3 == 0) {
                    renderer.line(previous.x, previous.y, previous.z, point.x, point.y, point.z,
                        withAlpha(starColor, alpha / 2));
                }

                previous = point;
                previousWidth = width;
            }

            if (hellEmbers.get()) {
                for (int spark = 0; spark < 7; spark++) {
                    double f = 0.12 + spark * 0.12;
                    double twinkle = 0.5 + 0.5 * Math.sin(time * 2.6 * speed + spark * 1.7 + tailIndex);
                    if (twinkle < 0.4) continue;

                    double curl = Math.sin(f * Math.PI * 1.35) * bend * length * 0.2;
                    Vec3 point = center.add(tailDir.scale(star * 0.3 + length * f)).add(tailSide.scale(curl));
                    double size = (0.35 + twinkle * 0.75) * scale;
                    int alpha = (int) (210 * (1.0 - f) * twinkle);

                    renderer.quad(point.x - size, point.y, point.z, point.x, point.y - size, point.z,
                        point.x + size, point.y, point.z, point.x, point.y + size, point.z,
                        withAlpha(starColor, alpha));
                    renderer.quad(point.x, point.y - size, point.z - size, point.x, point.y + size, point.z - size,
                        point.x, point.y + size, point.z + size, point.x, point.y - size, point.z + size,
                        withAlpha(tailColor, alpha * 3 / 4));
                }
            }
        }
    }

    private static void drawDisc(Renderer3D renderer, Vec3 center, Vec3 right, Vec3 up, double radius, Color a, Color b) {
        drawDisc(renderer, center, right, up, radius, a, b, 28);
    }

    private static void drawDisc(Renderer3D renderer, Vec3 center, Vec3 right, Vec3 up, double radius, Color a, Color b, int segments) {
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
