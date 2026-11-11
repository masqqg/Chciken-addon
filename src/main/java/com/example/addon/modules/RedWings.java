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
 * Cinematic crimson wings for the local player and/or nearby players.
 *
 * <p>Each wing is made from layered feather quads and animated with a slow
 * flap, a pulsing glow and ember particles. The geometry is drawn through the
 * regular 3D render pass so it follows the player through the world.</p>
 */
public class RedWings extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

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

    private final Setting<Double> animationSpeed = sgLook.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .defaultValue(1.0)
        .range(0.2, 3.0)
        .sliderRange(0.4, 2.0)
        .build()
    );

    private final Setting<Integer> feathers = sgLook.add(new IntSetting.Builder()
        .name("feathers")
        .defaultValue(7)
        .range(3, 12)
        .sliderRange(4, 10)
        .build()
    );

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("glow")
        .description("Draw a larger transparent glow layer behind the wings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> embers = sgLook.add(new BoolSetting.Builder()
        .name("embers")
        .description("Animated ember sparks around the wing tips.")
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
        .defaultValue(new SettingColor(255, 196, 0))
        .build()
    );

    public RedWings() {
        super(AddonTemplate.CATEGORY, "red-wings", "Animated crimson wings with glow and ember trails.");
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

        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double baseX = player.getX() - forwardX * 0.17;
        double baseY = player.getY() + 1.18 * s;
        double baseZ = player.getZ() - forwardZ * 0.17;

        double flap = Math.sin(time * 4.1 * speed) * 0.42
            + Math.sin(time * 8.3 * speed) * 0.07
            + 0.18;

        drawWing(event, baseX, baseY, baseZ, rightX, rightZ, forwardX, forwardZ, -1, flap, s, time, speed);
        drawWing(event, baseX, baseY, baseZ, rightX, rightZ, forwardX, forwardZ, 1, flap, s, time, speed);
    }

    private void drawWing(Render3DEvent event, double baseX, double baseY, double baseZ,
                          double rightX, double rightZ, double forwardX, double forwardZ,
                          int side, double flap, double s, double time, double speed) {
        int count = feathers.get();
        Color primary = this.primary.get();
        Color secondary = this.secondary.get();

        double rootX = baseX + rightX * side * 0.16 * s;
        double rootY = baseY;
        double rootZ = baseZ + rightZ * side * 0.16 * s;

        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : i / (double) (count - 1);
            double spread = 0.42 + t * 0.95;
            double back = 0.06 + t * 0.58;
            double lift = 0.08 + Math.sin(t * Math.PI) * 0.38 + flap * (0.22 + t * 0.34);

            double tipX = rootX + rightX * side * spread * s - forwardX * back * s;
            double tipY = rootY + lift * s;
            double tipZ = rootZ + rightZ * side * spread * s - forwardZ * back * s;

            double rootWidth = (0.035 + (1.0 - t) * 0.045) * s;
            double tipWidth = (0.012 + t * 0.024) * s;

            Color rootColor = mix(primary, secondary, (float) (t * 0.75));
            Color tipColor = mix(secondary, primary, (float) (0.15 + t * 0.5));

            int rootAlpha = 210 - (int) (t * 45);
            int tipAlpha = 170 - (int) (t * 55);

            if (glow.get()) {
                Color glowColor = withAlpha(primary, 28 + (int) (t * 18));
                quad(event, rootX, rootY - rootWidth * 2.4, rootZ,
                    rootX, rootY + rootWidth * 2.4, rootZ,
                    tipX, tipY + tipWidth * 2.4, tipZ,
                    tipX, tipY - tipWidth * 2.4, tipZ,
                    glowColor, glowColor, withAlpha(secondary, 24), withAlpha(secondary, 24));
            }

            quad(event, rootX, rootY - rootWidth, rootZ,
                rootX, rootY + rootWidth, rootZ,
                tipX, tipY + tipWidth, tipZ,
                tipX, tipY - tipWidth, tipZ,
                withAlpha(rootColor, rootAlpha), withAlpha(rootColor, rootAlpha),
                withAlpha(tipColor, tipAlpha), withAlpha(tipColor, tipAlpha));

            // A bright leading rib along each feather.
            event.renderer.line(rootX, rootY, rootZ, tipX, tipY, tipZ,
                withAlpha(mix(primary, Color.WHITE, 0.35f), 120));

            // A soft trailing ribbon from the wing tip.
            if (glow.get()) {
                event.renderer.line(tipX, tipY, tipZ,
                    tipX - forwardX * (0.18 + t * 0.22) * s,
                    tipY - 0.07 * s,
                    tipZ - forwardZ * (0.18 + t * 0.22) * s,
                    withAlpha(secondary, 55 - (int) (t * 20)));
            }

            if (embers.get()) {
                drawEmber(event, tipX, tipY, tipZ, t, time, speed, primary, secondary);
            }
        }
    }

    private void drawEmber(Render3DEvent event, double x, double y, double z, double t,
                           double time, double speed, Color primary, Color secondary) {
        double phase = time * (1.6 + t * 1.1) * speed + t * 6.0;
        double spread = Math.sin(phase * 1.7) * 0.22;
        double rise = (Math.sin(phase) * 0.5 + 0.5) * 0.35;
        double size = 0.018 + (Math.sin(phase * 2.1) * 0.5 + 0.5) * 0.022;

        Color color = withAlpha(mix(primary, secondary, (float) t), 80 + (int) (t * 100));
        event.renderer.quad(
            x - size, y + rise, z - size,
            x + size, y + rise, z - size,
            x + size, y + rise + size, z + size,
            x - size, y + rise + size, z + size,
            color
        );

        if (t > 0.75) {
            event.renderer.line(x, y, z, x + spread, y + rise + 0.15, z - spread,
                withAlpha(secondary, 65));
        }
    }

    private static void quad(Render3DEvent event,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             Color c1, Color c2, Color c3, Color c4) {
        event.renderer.quad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, c1, c2, c3, c4);
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
