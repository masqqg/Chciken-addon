package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.render.TexturedRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
 * Cinematic crimson wings built from painted feather cards.
 *
 * <p>Each feather is a real textured quad with the bundled feather art: a
 * tapered silhouette with soft alpha edges, a gradient from dark crimson at the
 * quill to a hot pink tip, a pale rachis and baked vane shading. Wings stack
 * three layers (long primaries, shorter coverts and a membrane arm), add an
 * emissive halo behind the tips and drifting ember sprites, so they read as
 * painted plumage rather than the coloured lines of the first version.</p>
 */
public class RedWings extends Module {
    private static final float FEATHER_ASPECT = 96.0f / 380.0f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");
    private final SettingGroup sgMotion = settings.createGroup("Motion");

    private final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
        .name("self")
        .description("Draw wings on your own player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> others = sgGeneral.add(new BoolSetting.Builder()
        .name("others")
        .description("Draw wings on other players.")
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

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("wing-scale")
        .defaultValue(1.0)
        .range(0.5, 2.0)
        .sliderRange(0.6, 1.6)
        .build()
    );

    private final Setting<Double> spread = sgLook.add(new DoubleSetting.Builder()
        .name("spread")
        .description("How wide the wings open.")
        .defaultValue(1.0)
        .range(0.4, 2.0)
        .sliderRange(0.6, 1.6)
        .build()
    );

    private final Setting<Double> curve = sgLook.add(new DoubleSetting.Builder()
        .name("curve")
        .description("How much the feathers bend backwards.")
        .defaultValue(0.45)
        .range(0.0, 1.5)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Integer> feathers = sgLook.add(new IntSetting.Builder()
        .name("feathers")
        .defaultValue(11)
        .range(3, 18)
        .sliderRange(6, 15)
        .build()
    );

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("glow")
        .description("Soft halo behind every feather tip.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> coverts = sgLook.add(new BoolSetting.Builder()
        .name("coverts")
        .description("Shorter second feather layer that adds depth.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trail = sgLook.add(new BoolSetting.Builder()
        .name("trail")
        .description("Fading plumage ribbons behind the wing tips.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> aura = sgLook.add(new BoolSetting.Builder()
        .name("aura")
        .description("Pulsing glow around the shoulders.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> embers = sgLook.add(new BoolSetting.Builder()
        .name("embers")
        .description("Drifting sparks around the wing tips.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> animationSpeed = sgMotion.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .defaultValue(1.0)
        .range(0.2, 3.0)
        .sliderRange(0.4, 2.0)
        .build()
    );

    private final Setting<Double> amplitude = sgMotion.add(new DoubleSetting.Builder()
        .name("flap-amplitude")
        .description("How far the wings travel on each beat.")
        .defaultValue(1.0)
        .range(0.3, 2.0)
        .sliderRange(0.5, 1.5)
        .build()
    );

    private final Setting<Boolean> doubleBeat = sgMotion.add(new BoolSetting.Builder()
        .name("double-beat")
        .description("Fast second beat after the main flap, like a real bird.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> primary = sgLook.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(255, 40, 55))
        .build()
    );

    private final Setting<SettingColor> secondary = sgLook.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 120, 0))
        .build()
    );

    private final Setting<SettingColor> accent = sgLook.add(new ColorSetting.Builder()
        .name("accent")
        .defaultValue(new SettingColor(255, 214, 90))
        .build()
    );

    private float cachedYaw;
    private double rightX;
    private double rightZ;
    private double forwardX;
    private double forwardZ;
    private Vec3 axisRight;
    private Vec3 axisForward;
    private Vec3 axisUp = new Vec3(0.0, 1.0, 0.0);

    public RedWings() {
        super(AddonTemplate.CATEGORY, "red-wings",
            "Layered painted crimson wings with textured feathers, glow and embers.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        TexturedRenderer plumage = TexturedRenderer.begin(event, TexturedRenderer.FEATHER, false);
        TexturedRenderer membrane = TexturedRenderer.begin(event, TexturedRenderer.MEMBRANE, false);

        if (plumage != null || membrane != null) {
            if (self.get()) drawWings(plumage, membrane, mc.player);
            if (others.get()) {
                double rangeSq = range.get() * range.get();
                for (Player player : mc.level.players()) {
                    if (player == mc.player) continue;
                    if (mc.player.distanceToSqr(player) > rangeSq) continue;
                    drawWings(plumage, membrane, player);
                }
            }
        }

        if (plumage != null) plumage.flush();
        if (membrane != null) membrane.flush();

        if (glow.get() || aura.get() || embers.get()) {
            TexturedRenderer light = TexturedRenderer.begin(event, TexturedRenderer.GLOW, true);
            if (light != null) {
                if (self.get()) drawLights(light, mc.player);
                if (others.get()) {
                    double rangeSq = range.get() * range.get();
                    for (Player player : mc.level.players()) {
                        if (player == mc.player) continue;
                        if (mc.player.distanceToSqr(player) > rangeSq) continue;
                        drawLights(light, player);
                    }
                }
                light.flush();
            }
        }
    }

    private void orient(Player player) {
        double yaw = Math.toRadians(player.getYRot());
        if (yaw != cachedYaw) {
            cachedYaw = (float) yaw;
            forwardX = -Math.sin(yaw);
            forwardZ = Math.cos(yaw);
            rightX = Math.cos(yaw);
            rightZ = Math.sin(yaw);
            axisForward = new Vec3(forwardX, 0.0, forwardZ);
            axisRight = new Vec3(rightX, 0.0, rightZ);
        }
    }

    private void drawWings(TexturedRenderer plumage, TexturedRenderer membrane, Player player) {
        if (plumage == null) return;

        orient(player);

        double time = System.nanoTime() / 1_000_000_000.0;
        double speed = animationSpeed.get();
        double s = scale.get();
        double spread = this.spread.get();
        double curve = this.curve.get();

        double motion = Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
            + player.getDeltaMovement().z * player.getDeltaMovement().z);
        double lean = Math.min(0.22, motion * 0.05);
        double flap = flap(time, speed);

        Vec3 base = new Vec3(
            player.getX() - forwardX * 0.16 * s,
            player.getY() + 1.18 * s,
            player.getZ() - forwardZ * 0.16 * s);

        for (int side = -1; side <= 1; side += 2) {
            Vec3 root = base.add(axisRight.scale(side * 0.13 * s));

            // Wing arm: a curved, textured membrane strip from shoulder to wrist.
            Vec3 previous = root;
            for (int segment = 1; segment <= 4; segment++) {
                double t = segment / 4.0;
                double arc = Math.sin(t * Math.PI * 0.5);
                Vec3 next = root
                    .add(axisRight.scale(side * (0.52 * spread * s) * t))
                    .add(axisForward.scale(-0.10 * s * t))
                    .add(axisUp.scale((0.14 + flap * 0.30) * s * arc - lean * 0.18 * s * t));

                if (membrane != null) {
                    double width = (0.085 - t * 0.030) * s;
                    card(membrane, previous, next, axisRight, width, width * 0.86,
                        0.0f, 0.0f, 1.0f, 1.0f, withAlpha(mix(primary.get(), secondary.get(), (float) (0.15 + t * 0.35)), 235));
                }

                previous = next;
            }

            Vec3 wrist = previous;

            int count = feathers.get();
            for (int i = 0; i < count; i++) {
                double t = count == 1 ? 0.5 : i / (double) (count - 1);

                double outward = (0.30 + t * 0.95) * spread;
                double back = curve * (0.16 + t * 0.85);
                double lift = 0.10 + Math.sin(t * Math.PI) * 0.34 + flap * (0.18 + t * 0.40);

                Vec3 tip = wrist
                    .add(axisRight.scale(side * outward * s))
                    .add(axisForward.scale(-back * s))
                    .add(axisUp.scale(lift * s - lean * 0.35 * s));

                Vec3 mid = wrist.add(tip).scale(0.5)
                    .add(axisForward.scale(-curve * 0.16 * s))
                    .add(axisUp.scale(curve * 0.10 * s));

                Color rootColor = mix(primary.get(), secondary.get(), (float) (0.10 + t * 0.55));
                Color tipColor = mix(secondary.get(), accent.get(), (float) (0.25 + t * 0.45));

                // Long primary feather: painted card, tapered by the texture itself.
                double length = root.distanceTo(tip);
                double rootWidth = Math.max(0.05, length * FEATHER_ASPECT * 1.05) * s;
                double tipWidth = rootWidth * 0.72;

                card(plumage, wrist, mid, axisUp, rootWidth, rootWidth * 0.9,
                    0.0f, 1.0f, 1.0f, 0.42f, withAlpha(rootColor, 245));
                card(plumage, mid, tip, axisUp, rootWidth * 0.9, tipWidth * 0.55,
                    0.0f, 0.45f, 1.0f, 0.0f, withAlpha(tipColor, 235));

                // Shorter covert layer, darker and closer to the wrist for depth.
                if (coverts.get() && t > 0.15) {
                    Vec3 covertTip = wrist.add(tip).scale(0.42)
                        .add(axisUp.scale(0.06 * s));
                    Color covert = mix(primary.get(), rootColor, 0.35f);
                    card(plumage, wrist, covertTip, axisUp, rootWidth * 0.86, rootWidth * 0.5,
                        0.0f, 1.0f, 1.0f, 0.30f, withAlpha(covert, 210));
                }

                // Trailing plumage ribbons.
                if (trail.get()) {
                    Vec3 ribbon = tip;
                    for (int segment = 0; segment < 3; segment++) {
                        double length2 = (0.18 + segment * 0.12) * s;
                        Vec3 next = ribbon
                            .add(axisForward.scale(-length2))
                            .add(axisRight.scale(side * length2 * 0.22))
                            .add(axisUp.scale(-(0.08 + segment * 0.05) * s));

                        int alpha = Math.max(0, 130 - segment * 38);
                        card(plumage, ribbon, next, axisUp,
                            (0.045 - segment * 0.010) * s, (0.020 - segment * 0.005) * s,
                            0.0f, 0.55f, 1.0f, 0.0f, withAlpha(mix(secondary.get(), primary.get(), 0.3f), alpha));

                        ribbon = next;
                    }
                }
            }
        }
    }

    /** Emissive pass: halos behind the wing tips plus drifting embers. */
    private void drawLights(TexturedRenderer light, Player player) {
        orient(player);

        double time = System.nanoTime() / 1_000_000_000.0;
        double speed = animationSpeed.get();
        double s = scale.get();
        double spread = this.spread.get();
        double flap = flap(time, speed);

        Vec3 base = new Vec3(
            player.getX() - forwardX * 0.16 * s,
            player.getY() + 1.18 * s,
            player.getZ() - forwardZ * 0.16 * s);

        double pulse = 0.5 + 0.5 * Math.sin(time * 2.4 * speed);

        for (int side = -1; side <= 1; side += 2) {
            Vec3 root = base.add(axisRight.scale(side * 0.13 * s));

            if (aura.get()) {
                light.sprite(root.x, root.y + 0.05, root.z, (0.75 + 0.12 * pulse) * s, (0.75 + 0.12 * pulse) * s,
                    withAlpha(primary.get(), 26 + (int) (18 * pulse)));
            }

            if (!glow.get() && !embers.get()) continue;

            int count = feathers.get();
            for (int i = 0; i < count; i++) {
                double t = count == 1 ? 0.5 : i / (double) (count - 1);
                double outward = (0.30 + t * 0.95) * spread;
                double back = curve.get() * (0.16 + t * 0.85);
                double lift = 0.10 + Math.sin(t * Math.PI) * 0.34 + flap * (0.18 + t * 0.40);

                Vec3 tip = root
                    .add(axisRight.scale(side * (0.52 * spread * s) * 1.0 + side * outward * s))
                    .add(axisForward.scale(-(0.10 * s) - back * s))
                    .add(axisUp.scale((0.14 + flap * 0.30) * s + lift * s));

                if (glow.get()) {
                    double size = (0.10 + t * 0.16) * s;
                    light.sprite(tip.x, tip.y, tip.z, size, size,
                        withAlpha(mix(secondary.get(), accent.get(), (float) t), 26 + (int) (22 * pulse)));
                }

                if (embers.get() && t > 0.30) {
                    ember(light, tip, t, 0.0, time, speed);
                    if (t > 0.65) ember(light, tip, t, 1.9, time, speed);
                }
            }
        }
    }

    private void ember(TexturedRenderer light, Vec3 tip, double t, double offset,
                       double time, double speed) {
        double phase = time * (1.5 + t * 1.0) * speed + t * 6.0 + offset;
        double rise = (Math.sin(phase) * 0.5 + 0.5) * 0.45;
        double drift = Math.sin(phase * 1.3) * 0.20;
        double driftZ = Math.cos(phase * 0.9) * 0.20;
        double size = 0.020 + (Math.sin(phase * 2.1) * 0.5 + 0.5) * 0.030;

        int alpha = (int) (90 + 130 * (Math.sin(phase * 1.7) * 0.5 + 0.5));
        Color color = withAlpha(mix(secondary.get(), accent.get(), (float) t), alpha);

        light.sprite(tip.x + drift, tip.y + rise, tip.z + driftZ, size, size * 1.6, color);
    }

    private double flap(double time, double speed) {
        double base = time * 3.8 * speed;
        double wave = doubleBeat.get()
            ? Math.sin(base) * 0.58 + Math.sin(base * 2.0 + 0.9) * 0.16
            : Math.sin(base) * 0.68;
        return (wave + 0.22) * amplitude.get();
    }

    /**
     * Draws one tapered textured card between two world points. The card is
     * oriented across {@code axis} and drawn from both sides so the plumage is
     * never invisibly back-facing.
     */
    private void card(TexturedRenderer renderer, Vec3 from, Vec3 to, Vec3 axis,
                      double widthFrom, double widthTo,
                      float u0, float v0, float u1, float v1, Color color) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0e-6) return;

        Vec3 across = direction.cross(axis);
        if (across.lengthSqr() < 1.0e-6) across = direction.cross(axisForward);
        if (across.lengthSqr() < 1.0e-6) return;
        across = across.normalize();

        Vec3 a = from.add(across.scale(-widthFrom));
        Vec3 b = from.add(across.scale(widthFrom));
        Vec3 c = to.add(across.scale(widthTo));
        Vec3 d = to.add(across.scale(-widthTo));

        renderer.quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z,
            u0, v1, u1, v0, color, true);
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
