package com.example.addon.modules;

import com.example.addon.AddonTemplate;
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

/**
 * Cinematic crimson wings for the local player and nearby players.
 *
 * <p>Every wing is built from a curved arm, layered primary feathers, a bright
 * leading rib, trailing ribbons and drifting embers. The flap uses a double beat
 * so it reads like a real wing instead of a slow metronome.</p>
 */
public class RedWings extends Module {
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
        .defaultValue(9)
        .range(3, 16)
        .sliderRange(5, 13)
        .build()
    );

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("glow")
        .description("Soft halo behind every feather.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ribs = sgLook.add(new BoolSetting.Builder()
        .name("ribs")
        .description("Bright leading rib along each feather.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trail = sgLook.add(new BoolSetting.Builder()
        .name("trail")
        .description("Fading ribbons behind the wing tips.")
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

    public RedWings() {
        super(AddonTemplate.CATEGORY, "red-wings", "Layered crimson wings with glow, ribs and ember trails.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (self.get()) drawWings(event, mc.player);

        if (others.get()) {
            double rangeSq = range.get() * range.get();

            for (Player player : mc.level.players()) {
                if (player == mc.player) continue;
                if (mc.player.distanceToSqr(player) > rangeSq) continue;
                drawWings(event, player);
            }
        }
    }

    private void drawWings(Render3DEvent event, Player player) {
        double time = System.nanoTime() / 1_000_000_000.0;
        double speed = animationSpeed.get();
        double s = scale.get();
        double spread = this.spread.get();
        double curve = this.curve.get();

        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double baseX = player.getX() - forwardX * 0.16 * s;
        double baseY = player.getY() + 1.18 * s;
        double baseZ = player.getZ() - forwardZ * 0.16 * s;

        double motion = Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
            + player.getDeltaMovement().z * player.getDeltaMovement().z);
        double lean = Math.min(0.22, motion * 0.05);

        double flap = flap(time, speed);

        if (aura.get()) drawAura(event, baseX, baseY, baseZ, s, time, speed);

        for (int side = -1; side <= 1; side += 2) {
            drawWing(event, baseX, baseY, baseZ, rightX, rightZ, forwardX, forwardZ,
                side, flap + side * 0.06, s, spread, curve, lean, time, speed);
        }
    }

    private double flap(double time, double speed) {
        double base = time * 3.8 * speed;
        double wave = doubleBeat.get()
            ? Math.sin(base) * 0.58 + Math.sin(base * 2.0 + 0.9) * 0.16
            : Math.sin(base) * 0.68;
        return (wave + 0.22) * amplitude.get();
    }

    private void drawAura(Render3DEvent event, double x, double y, double z, double s, double time, double speed) {
        Color color = primary.get();
        double pulse = 0.5 + 0.5 * Math.sin(time * 2.4 * speed);
        int segments = 20;
        double radius = (0.95 + 0.14 * pulse) * s;
        int alpha = 10 + (int) (10 * pulse);

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            event.renderer.quad(
                x, y, z,
                x, y + Math.sin(a0) * radius, z + Math.cos(a0) * radius * 0.55,
                x, y + Math.sin(a1) * radius, z + Math.cos(a1) * radius * 0.55,
                x, y, z,
                withAlpha(color, alpha));
        }
    }

    private void drawWing(Render3DEvent event, double baseX, double baseY, double baseZ,
                          double rightX, double rightZ, double forwardX, double forwardZ,
                          int side, double flap, double s, double spread, double curve, double lean,
                          double time, double speed) {
        int count = feathers.get();
        Color primary = this.primary.get();
        Color secondary = this.secondary.get();
        Color accent = this.accent.get();

        double rootX = baseX + rightX * side * 0.13 * s;
        double rootY = baseY;
        double rootZ = baseZ + rightZ * side * 0.13 * s;

        // Curved wing arm from the shoulder outwards.
        double armLength = 0.52 * spread * s;
        double armLift = (0.14 + flap * 0.30) * s;
        double armBack = 0.10 * s;

        double prevX = rootX;
        double prevY = rootY;
        double prevZ = rootZ;

        for (int segment = 1; segment <= 3; segment++) {
            double t = segment / 3.0;
            double bend = Math.sin(t * Math.PI * 0.5);

            double nextX = rootX + rightX * side * armLength * t - forwardX * armBack * t;
            double nextY = rootY + armLift * bend - lean * 0.18 * s * t;
            double nextZ = rootZ + rightZ * side * armLength * t - forwardZ * armBack * t;

            double width = (0.060 - t * 0.030) * s;
            Color armColor = withAlpha(mix(primary, secondary, (float) (0.2 + t * 0.45)), 195 - segment * 12);
            featherQuad(event, prevX, prevY, prevZ, nextX, nextY, nextZ, width, width * 0.8, armColor, armColor);

            prevX = nextX;
            prevY = nextY;
            prevZ = nextZ;
        }

        double shoulderX = prevX;
        double shoulderY = prevY;
        double shoulderZ = prevZ;

        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : i / (double) (count - 1);
            double spreadDistance = (0.26 + t * 0.92) * spread;
            double back = 0.05 + t * 0.60;
            double lift = (0.08 + Math.sin(t * Math.PI) * 0.34) * s + flap * (0.20 + t * 0.42) * s;

            double tipX = shoulderX + rightX * side * spreadDistance * s - forwardX * back * s;
            double tipY = shoulderY + lift - lean * 0.35 * s;
            double tipZ = shoulderZ + rightZ * side * spreadDistance * s - forwardZ * back * s;

            double midX = (shoulderX + tipX) * 0.5 - forwardX * curve * 0.20 * s;
            double midY = (shoulderY + tipY) * 0.5 + curve * 0.16 * s;
            double midZ = (shoulderZ + tipZ) * 0.5 - forwardZ * curve * 0.20 * s;

            double rootWidth = (0.030 + (1.0 - t) * 0.040) * s;
            double midWidth = rootWidth * 0.78;
            double tipWidth = (0.009 + t * 0.022) * s;

            Color rootColor = mix(primary, secondary, (float) (t * 0.72));
            Color midColor = mix(secondary, accent, (float) (0.10 + t * 0.35));
            Color tipColor = mix(accent, primary, (float) (0.10 + t * 0.40));

            int rootAlpha = 215 - (int) (t * 50);
            int midAlpha = 195 - (int) (t * 55);
            int tipAlpha = 165 - (int) (t * 60);

            if (glow.get()) {
                Color halo = withAlpha(primary, 26 + (int) (t * 20));
                featherQuad(event, shoulderX, shoulderY, shoulderZ, tipX, tipY, tipZ,
                    rootWidth * 2.6, tipWidth * 2.6 + 0.02 * s, halo, withAlpha(secondary, 18));
            }

            featherQuad(event, shoulderX, shoulderY, shoulderZ, midX, midY, midZ,
                rootWidth, midWidth, withAlpha(rootColor, rootAlpha), withAlpha(midColor, midAlpha));
            featherQuad(event, midX, midY, midZ, tipX, tipY, tipZ,
                midWidth, tipWidth, withAlpha(midColor, midAlpha), withAlpha(tipColor, tipAlpha));

            // Crossed ribbon for volume, so the wings do not disappear edge-on.
            if (t < 0.55) {
                double crossWidth = rootWidth * 0.55;
                event.renderer.quad(
                    shoulderX - forwardX * crossWidth, shoulderY, shoulderZ - forwardZ * crossWidth,
                    shoulderX + forwardX * crossWidth, shoulderY, shoulderZ + forwardZ * crossWidth,
                    tipX + forwardX * crossWidth * 0.6, tipY, tipZ + forwardZ * crossWidth * 0.6,
                    tipX - forwardX * crossWidth * 0.6, tipY, tipZ - forwardZ * crossWidth * 0.6,
                    withAlpha(rootColor, 95), withAlpha(rootColor, 95),
                    withAlpha(tipColor, 60), withAlpha(tipColor, 60));
            }

            if (ribs.get()) {
                Color rib = withAlpha(mix(accent, Color.WHITE, 0.35f), 135 - (int) (t * 50));
                event.renderer.line(shoulderX, shoulderY, shoulderZ, midX, midY, midZ, rib);
                event.renderer.line(midX, midY, midZ, tipX, tipY, tipZ, rib);
            }

            if (trail.get()) {
                double tx = tipX;
                double ty = tipY;
                double tz = tipZ;

                for (int segment = 0; segment < 3; segment++) {
                    double length = (0.16 + segment * 0.10) * s;
                    double nextX = tx - forwardX * length - rightX * side * length * 0.25;
                    double nextY = ty - (0.09 + segment * 0.06) * s;
                    double nextZ = tz - forwardZ * length - rightZ * side * length * 0.25;

                    int alpha = Math.max(0, 70 - segment * 20);
                    featherQuad(event, tx, ty, tz, nextX, nextY, nextZ,
                        (0.018 - segment * 0.004) * s, (0.009 - segment * 0.002) * s,
                        withAlpha(secondary, alpha), withAlpha(primary, Math.max(0, alpha - 28)));

                    tx = nextX;
                    ty = nextY;
                    tz = nextZ;
                }
            }

            if (embers.get() && t > 0.30) {
                drawEmber(event, tipX, tipY, tipZ, t, 0.0, time, speed, primary, secondary);
                if (t > 0.65) {
                    drawEmber(event, tipX, tipY, tipZ, t, 1.9, time, speed, secondary, accent);
                }
            }
        }
    }

    private void drawEmber(Render3DEvent event, double x, double y, double z, double t, double offset,
                           double time, double speed, Color from, Color to) {
        double phase = time * (1.5 + t * 1.0) * speed + t * 6.0 + offset;
        double rise = (Math.sin(phase) * 0.5 + 0.5) * 0.45;
        double drift = Math.sin(phase * 1.3) * 0.20;
        double driftZ = Math.cos(phase * 0.9) * 0.20;
        double size = 0.014 + (Math.sin(phase * 2.1) * 0.5 + 0.5) * 0.022;

        int alpha = (int) (55 + 125 * (Math.sin(phase * 1.7) * 0.5 + 0.5));
        Color color = withAlpha(mix(from, to, (float) t), alpha);

        event.renderer.quad(
            x - size, y + rise, z - size,
            x + size, y + rise, z - size,
            x + size, y + rise + size * 2.2, z + size,
            x - size, y + rise + size * 2.2, z + size,
            color);
        event.renderer.quad(
            x - size, y + rise, z + size,
            x + size, y + rise, z + size,
            x + size, y + rise + size * 2.2, z - size,
            x - size, y + rise + size * 2.2, z - size,
            color);

        event.renderer.line(x, y + rise, z, x + drift, y + rise + 0.26, z + driftZ,
            withAlpha(to, Math.max(0, alpha / 2)));
    }

    private static void featherQuad(Render3DEvent event,
                                    double x0, double y0, double z0,
                                    double x1, double y1, double z1,
                                    double w0, double w1, Color c0, Color c1) {
        event.renderer.quad(
            x0, y0 - w0, z0,
            x0, y0 + w0, z0,
            x1, y1 + w1, z1,
            x1, y1 - w1, z1,
            c0, c0, c1, c1);
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
