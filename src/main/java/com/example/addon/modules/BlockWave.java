package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ripples the blocks around you like water.
 *
 * <p>Every jump, landing or step starts an expanding wave. The wave samples the
 * real block surface heights around the player once, then lifts and tints those
 * surfaces with an animated ring, so the terrain itself appears to flex and
 * shimmer. Everything is client side and purely visual.</p>
 */
public class BlockWave extends Module {
    private static final int MAX_WAVES = 8;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("How far the ripple travels in blocks.")
        .defaultValue(6.0)
        .range(1.0, 14.0)
        .sliderRange(2.0, 10.0)
        .build()
    );

    private final Setting<Double> duration = sgGeneral.add(new DoubleSetting.Builder()
        .name("duration")
        .description("Lifetime of one ripple in seconds.")
        .defaultValue(0.9)
        .range(0.15, 2.5)
        .sliderRange(0.3, 1.6)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("lift")
        .description("How far the block surfaces rise at the wave front.")
        .defaultValue(0.34)
        .range(0.0, 1.5)
        .sliderRange(0.05, 0.8)
        .build()
    );

    private final Setting<Double> band = sgGeneral.add(new DoubleSetting.Builder()
        .name("band-width")
        .description("Thickness of the moving ring. Larger is softer and wider.")
        .defaultValue(1.7)
        .range(0.3, 4.0)
        .sliderRange(0.6, 3.0)
        .build()
    );

    private final Setting<Boolean> onJump = sgGeneral.add(new BoolSetting.Builder()
        .name("on-jump")
        .description("Start a ripple when you jump.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onLand = sgGeneral.add(new BoolSetting.Builder()
        .name("on-land")
        .description("Start an inward ripple when you land.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onStep = sgGeneral.add(new BoolSetting.Builder()
        .name("on-step")
        .description("Small ripples while walking and sprinting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> stepInterval = sgGeneral.add(new DoubleSetting.Builder()
        .name("step-interval")
        .description("Distance in blocks between walking ripples.")
        .defaultValue(1.6)
        .range(0.5, 5.0)
        .sliderRange(0.8, 3.0)
        .visible(onStep::get)
        .build()
    );

    private final Setting<Boolean> outline = sgLook.add(new BoolSetting.Builder()
        .name("outline")
        .description("Draw a bright edge around every lifted block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shockRing = sgLook.add(new BoolSetting.Builder()
        .name("shock-ring")
        .description("Draw a glowing ring racing outwards at your feet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skirt = sgLook.add(new BoolSetting.Builder()
        .name("skirt")
        .description("Draw short glowing walls under the lifted surfaces.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> opacity = sgLook.add(new IntSetting.Builder()
        .name("opacity")
        .description("Maximum opacity of the survivors.")
        .defaultValue(165)
        .range(20, 255)
        .sliderRange(40, 220)
        .build()
    );

    private final Setting<SettingColor> primary = sgLook.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(255, 220, 90))
        .build()
    );

    private final Setting<SettingColor> secondary = sgLook.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 145, 0))
        .build()
    );

    private final List<Wave> waves = new ArrayList<>();

    private boolean wasOnGround;
    private double lastX;
    private double lastZ;
    private double stepDistance;

    public BlockWave() {
        super(AddonTemplate.CATEGORY, "block-wave",
            "Lifts and ripples the blocks around you when you jump, land or run.");
    }

    @Override
    public void onActivate() {
        waves.clear();
        stepDistance = 0.0;
        if (mc.player != null) {
            wasOnGround = mc.player.onGround();
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
        }
    }

    @Override
    public void onDeactivate() {
        waves.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            waves.clear();
            return;
        }

        var player = mc.player;
        boolean onGround = player.onGround();
        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        lastX = player.getX();
        lastZ = player.getZ();

        if (onGround) {
            if (!wasOnGround && onLand.get()) {
                spawn(player.getX(), player.getY(), player.getZ(), radius.get() * 0.85, true, 1.25);
            }

            if (onStep.get()) {
                stepDistance += Math.sqrt(dx * dx + dz * dz);
                double interval = Math.max(0.3, stepInterval.get());
                if (stepDistance >= interval) {
                    stepDistance = 0.0;
                    boolean sprinting = player.isSprinting();
                    spawn(player.getX(), player.getY(), player.getZ(),
                        radius.get() * (sprinting ? 0.85 : 0.6), false, sprinting ? 0.85 : 0.6);
                }
            }
        } else {
            if (wasOnGround && player.getDeltaMovement().y > 0.02 && onJump.get()) {
                spawn(player.getX(), player.getY(), player.getZ(), radius.get(), false, 1.0);
            }
        }

        wasOnGround = onGround;
    }

    private void spawn(double x, double y, double z, double waveRadius, boolean inward, double strength) {
        if (waves.size() >= MAX_WAVES) waves.remove(0);

        Wave wave = new Wave(x, y, z, waveRadius, inward, strength);
        buildDots(wave);
        if (!wave.dots.isEmpty()) waves.add(wave);
    }

    /** Samples the highest walkable surface per column once, at spawn time. */
    private void buildDots(Wave wave) {
        int range = (int) Math.ceil(wave.radius);
        int px = Mth.floor(wave.x);
        int py = Mth.floor(wave.y);
        int pz = Mth.floor(wave.z);
        int lower = Math.max(mc.level.getMinY(), py - 5);
        int upper = Math.min(mc.level.getMaxY() - 1, py + 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int ox = -range; ox <= range; ox++) {
            for (int oz = -range; oz <= range; oz++) {
                double distance = Math.sqrt(ox * ox + oz * oz);
                if (distance > wave.radius) continue;

                int x = px + ox;
                int z = pz + oz;

                for (int y = upper; y >= lower; y--) {
                    pos.set(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (!state.getFluidState().isEmpty()) break;

                    VoxelShape shape = state.getCollisionShape(mc.level, pos);
                    if (shape.isEmpty()) continue;

                    double top = y + shape.max(Direction.Axis.Y);
                    if (top <= y + 0.001) break;

                    wave.dots.add(new Dot(x + 0.5, top, z + 0.5, distance));
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (waves.isEmpty() || mc.level == null || mc.player == null) return;

        long now = System.nanoTime();
        double duration = Math.max(0.1, this.duration.get());
        double lift = height.get();
        double band = Math.max(0.05, this.band.get());
        int alpha = opacity.get();
        boolean drawOutline = outline.get();
        boolean drawSkirt = skirt.get();
        Color primaryColor = primary.get();
        Color secondaryColor = secondary.get();

        for (Iterator<Wave> iterator = waves.iterator(); iterator.hasNext();) {
            Wave wave = iterator.next();
            double age = (now - wave.startNanos) / 1_000_000_000.0;
            double progress = age / duration;
            if (progress >= 1.0) {
                iterator.remove();
                continue;
            }

            double eased = 1.0 - Math.pow(1.0 - progress, 2.2);
            double front = wave.inward ? wave.radius * (1.0 - eased) : wave.radius * eased;
            double fade = Math.pow(1.0 - progress, 1.4) * wave.strength;

            if (shockRing.get()) {
                double ringY = wave.y + 0.06;
                Color ringColor = withAlpha(mix(primaryColor, secondaryColor, (float) progress), (int) (fade * alpha * 0.9));
                int segments = 34;
                for (int i = 0; i < segments; i++) {
                    double a0 = Math.PI * 2.0 * i / segments;
                    double a1 = Math.PI * 2.0 * (i + 1) / segments;
                    event.renderer.line(
                        wave.x + Math.cos(a0) * front, ringY, wave.z + Math.sin(a0) * front,
                        wave.x + Math.cos(a1) * front, ringY, wave.z + Math.sin(a1) * front,
                        ringColor);
                }
            }

            for (Dot dot : wave.dots) {
                double delta = Math.abs(dot.distance - front);
                if (delta > band) continue;

                double strength = 1.0 - delta / band;
                strength *= strength;
                if (strength < 0.02) continue;

                double offset = (wave.inward ? -1.0 : 1.0) * strength * lift;
                double top = dot.y + offset;
                double a = strength * fade * alpha;
                if (a < 3.0) continue;

                Color surface = withAlpha(mix(secondaryColor, primaryColor, (float) strength), (int) a);
                Color edge = withAlpha(mix(primaryColor, Color.WHITE, 0.35f), (int) Math.min(255, a * 1.25));

                double half = 0.5;
                event.renderer.quad(
                    dot.x - half, top, dot.z - half,
                    dot.x + half, top, dot.z - half,
                    dot.x + half, top, dot.z + half,
                    dot.x - half, top, dot.z + half,
                    surface);

                if (drawSkirt && strength > 0.2) {
                    Color wall = withAlpha(surface, (int) (a * 0.35));
                    event.renderer.quad(
                        dot.x - half, dot.y, dot.z - half,
                        dot.x + half, dot.y, dot.z - half,
                        dot.x + half, top, dot.z - half,
                        dot.x - half, top, dot.z - half,
                        wall);
                    event.renderer.quad(
                        dot.x - half, dot.y, dot.z + half,
                        dot.x + half, dot.y, dot.z + half,
                        dot.x + half, top, dot.z + half,
                        dot.x - half, top, dot.z + half,
                        wall);
                    event.renderer.quad(
                        dot.x - half, dot.y, dot.z - half,
                        dot.x - half, dot.y, dot.z + half,
                        dot.x - half, top, dot.z + half,
                        dot.x - half, top, dot.z - half,
                        wall);
                    event.renderer.quad(
                        dot.x + half, dot.y, dot.z - half,
                        dot.x + half, dot.y, dot.z + half,
                        dot.x + half, top, dot.z + half,
                        dot.x + half, top, dot.z - half,
                        wall);
                }

                if (drawOutline && strength > 0.3) {
                    Color line = withAlpha(edge, (int) (a * 0.65));
                    event.renderer.quad(
                        dot.x - half, top + 0.01, dot.z - half,
                        dot.x + half, top + 0.01, dot.z - half,
                        dot.x + half, top + 0.01, dot.z + half,
                        dot.x - half, top + 0.01, dot.z + half,
                        line);
                }
            }
        }
    }

    private static final class Dot {
        final double x;
        final double y;
        final double z;
        final double distance;

        Dot(double x, double y, double z, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
        }
    }

    private static final class Wave {
        final double x;
        final double y;
        final double z;
        final double radius;
        final boolean inward;
        final double strength;
        final long startNanos = System.nanoTime();
        final List<Dot> dots = new ArrayList<>();

        Wave(double x, double y, double z, double radius, boolean inward, double strength) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.inward = inward;
            this.strength = strength;
        }
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
