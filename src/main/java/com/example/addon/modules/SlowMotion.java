package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Slow-motion echoes for enemy players.
 *
 * <p>Whenever another player walks, their position is sampled and the samples
 * are replayed behind them as fading ghost bodies, motion streaks and ripples
 * on the ground. The result reads like the world runs at a lower frame rate
 * around them: they move normally, but their previous selves stay visible for a
 * moment, dragging a trail behind every step.</p>
 *
 * <p>Only other players leave trails. Friends (Meteor's friend list) can be
 * skipped so the effect is reserved for enemies, and nothing here touches the
 * server: it is a purely visual client side effect.</p>
 */
public class SlowMotion extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");
    private final SettingGroup sgScreen = settings.createGroup("Screen");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a player can be and still leave echoes.")
        .defaultValue(48.0)
        .range(8.0, 128.0)
        .sliderRange(16.0, 80.0)
        .build()
    );

    private final Setting<Boolean> enemiesOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("enemies-only")
        .description("Skip players on the Meteor friends list, so only enemies leave trails.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxGhosts = sgGeneral.add(new IntSetting.Builder()
        .name("echoes")
        .description("How many afterimages each player keeps.")
        .defaultValue(6)
        .range(2, 14)
        .sliderRange(3, 10)
        .build()
    );

    private final Setting<Double> spacing = sgGeneral.add(new DoubleSetting.Builder()
        .name("spacing")
        .description("Distance in blocks between two echoes. Smaller is denser.")
        .defaultValue(0.32)
        .range(0.05, 1.5)
        .sliderRange(0.1, 0.8)
        .build()
    );

    private final Setting<Double> lifetime = sgGeneral.add(new DoubleSetting.Builder()
        .name("lifetime")
        .description("How long one echo stays visible in seconds.")
        .defaultValue(0.55)
        .range(0.1, 2.0)
        .sliderRange(0.2, 1.2)
        .build()
    );

    private final Setting<Boolean> bodies = sgLook.add(new BoolSetting.Builder()
        .name("ghost-bodies")
        .description("Draw the previous player bodies as translucent boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> streaks = sgLook.add(new BoolSetting.Builder()
        .name("motion-streaks")
        .description("Draw speed lines between the echoes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rings = sgLook.add(new BoolSetting.Builder()
        .name("ground-ripples")
        .description("Expanding rings at their feet every time an echo is left.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> opacity = sgLook.add(new IntSetting.Builder()
        .name("opacity")
        .description("Brightness of the newest echo.")
        .defaultValue(150)
        .range(20, 255)
        .sliderRange(40, 220)
        .build()
    );

    private final Setting<SettingColor> primary = sgLook.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(96, 214, 255))
        .build()
    );

    private final Setting<SettingColor> secondary = sgLook.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 196, 0))
        .build()
    );

    private final Setting<Boolean> warp = sgScreen.add(new BoolSetting.Builder()
        .name("time-warp-screen")
        .description("Soft vignette that sells the slowed-down world.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> warpStrength = sgScreen.add(new IntSetting.Builder()
        .name("warp-strength")
        .description("Opacity of the time-warp vignette.")
        .defaultValue(38)
        .range(4, 120)
        .sliderRange(10, 70)
        .visible(warp::get)
        .build()
    );

    private final Map<Integer, Trail> trails = new HashMap<>();
    private final List<Ripple> ripples = new ArrayList<>();

    public SlowMotion() {
        super(AddonTemplate.CATEGORY, "slow-motion",
            "Leaves fading slow-motion echoes behind enemy players as they walk.");
    }

    @Override
    public void onActivate() {
        trails.clear();
        ripples.clear();
    }

    @Override
    public void onDeactivate() {
        trails.clear();
        ripples.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            trails.clear();
            ripples.clear();
            return;
        }

        long now = System.nanoTime();
        double rangeSq = range.get() * range.get();
        int limit = maxGhosts.get();
        Set<Integer> seen = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            int id = player.getId();
            if (mc.player.distanceToSqr(player) > rangeSq) continue;
            if (enemiesOnly.get() && Friends.get().isFriend(player)) continue;

            seen.add(id);

            Trail trail = trails.get(id);
            if (trail == null) {
                trail = new Trail(player);
                trails.put(id, trail);
                continue;
            }

            double dx = player.getX() - trail.lastX;
            double dz = player.getZ() - trail.lastZ;
            double moved = Math.sqrt(dx * dx + dz * dz);
            boolean elapsed = now - trail.lastSample >= 45_000_000L;

            if (moved < spacing.get() && !(moved > 0.01 && elapsed)) continue;

            trail.ghosts.addLast(new Ghost(player.getX(), player.getY(), player.getZ(),
                player.getBbWidth(), player.getBbHeight(), now));
            while (trail.ghosts.size() > limit) trail.ghosts.removeFirst();

            trail.lastX = player.getX();
            trail.lastY = player.getY();
            trail.lastZ = player.getZ();
            trail.lastSample = now;

            if (rings.get()) {
                ripples.add(new Ripple(player.getX(), player.getY(), player.getZ(), now));
            }
        }

        trails.keySet().removeIf(id -> !seen.contains(id));

        long rippleLife = 400_000_000L;
        ripples.removeIf(ripple -> now - ripple.start > rippleLife);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;
        if (trails.isEmpty() && ripples.isEmpty()) return;

        long now = System.nanoTime();
        double life = Math.max(0.05, lifetime.get()) * 1_000_000_000.0;
        int maxAlpha = opacity.get();
        Color primaryColor = primary.get();
        Color secondaryColor = secondary.get();

        for (Trail trail : trails.values()) {
            double previousX = Double.NaN;
            double previousY = 0.0;
            double previousZ = Double.NaN;

            for (Ghost ghost : trail.ghosts) {
                double age = now - ghost.time;
                double progress = age / life;
                if (progress >= 1.0) continue;

                float fade = (float) Math.pow(1.0 - progress, 1.7);
                int alpha = (int) (maxAlpha * fade);
                if (alpha < 4) continue;

                if (bodies.get()) drawGhost(event, ghost, alpha, primaryColor, secondaryColor, fade);

                if (streaks.get() && !Double.isNaN(previousX)) {
                    Color streak = withAlpha(primaryColor, (int) (alpha * 0.55));
                    double y = ghost.y + ghost.height * 0.55;
                    event.renderer.line(previousX, previousY + ghost.height * 0.55, previousZ,
                        ghost.x, y, ghost.z, streak);

                    Color spark = withAlpha(secondaryColor, (int) (alpha * 0.35));
                    event.renderer.line(previousX, previousY + 0.08, previousZ, ghost.x, ghost.y + 0.08, ghost.z, spark);
                }

                previousX = ghost.x;
                previousY = ghost.y;
                previousZ = ghost.z;
            }
        }

        if (rings.get()) {
            for (Iterator<Ripple> iterator = ripples.iterator(); iterator.hasNext();) {
                Ripple ripple = iterator.next();
                double progress = (now - ripple.start) / 400_000_000.0;
                if (progress >= 1.0) {
                    iterator.remove();
                    continue;
                }

                double radius = 0.25 + progress * 1.35;
                double fade = Math.pow(1.0 - progress, 1.5);
                Color ringColor = withAlpha(mix(primaryColor, secondaryColor, (float) progress),
                    (int) (maxAlpha * fade * 0.8));

                int segments = 22;
                double y = ripple.y + 0.04;
                for (int i = 0; i < segments; i++) {
                    double a0 = Math.PI * 2.0 * i / segments;
                    double a1 = Math.PI * 2.0 * (i + 1) / segments;
                    event.renderer.line(
                        ripple.x + Math.cos(a0) * radius, y, ripple.z + Math.sin(a0) * radius,
                        ripple.x + Math.cos(a1) * radius, y, ripple.z + Math.sin(a1) * radius,
                        ringColor);
                }
            }
        }
    }

    /** Legs, torso and head as separate faint boxes, so it reads as a body. */
    private void drawGhost(Render3DEvent event, Ghost ghost, int alpha,
                           Color primaryColor, Color secondaryColor, float fade) {
        double half = Math.max(0.2, ghost.width / 2.0);
        double height = Math.max(1.0, ghost.height);
        double legTop = ghost.y + height * 0.45;
        double torsoTop = ghost.y + height * 0.82;

        Color side = withAlpha(primaryColor, (int) (alpha * 0.22));
        Color line = withAlpha(primaryColor, (int) (alpha * 0.85));
        Color accentSide = withAlpha(secondaryColor, (int) (alpha * 0.18));
        Color accentLine = withAlpha(secondaryColor, (int) (alpha * 0.7));

        event.renderer.box(ghost.x - half, ghost.y, ghost.z - half,
            ghost.x + half, legTop, ghost.z + half,
            accentSide, accentLine, ShapeMode.Both, 0);

        event.renderer.box(ghost.x - half * 1.05, legTop, ghost.z - half * 0.95,
            ghost.x + half * 1.05, torsoTop, ghost.z + half * 0.95,
            side, line, ShapeMode.Both, 0);

        double head = Math.min(0.34, height * 0.19);
        double headHalf = Math.min(0.24, half * 0.9);
        event.renderer.box(ghost.x - headHalf, torsoTop, ghost.z - headHalf,
            ghost.x + headHalf, Math.min(ghost.y + height, torsoTop + head), ghost.z + headHalf,
            withAlpha(primaryColor, (int) (alpha * 0.3)), line, ShapeMode.Both, 0);

        if (fade > 0.5) {
            Color core = withAlpha(primaryColor, (int) (alpha * 0.12));
            event.renderer.box(ghost.x - half * 0.35, ghost.y + 0.1, ghost.z - half * 0.35,
                ghost.x + half * 0.35, ghost.y + height * 0.95, ghost.z + half * 0.35,
                core, withAlpha(primaryColor, 0), ShapeMode.Sides, 0);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!warp.get() || mc.level == null || mc.player == null) return;
        if (trails.isEmpty()) return;

        int width = event.screenWidth;
        int height = event.screenHeight;
        if (width <= 0 || height <= 0) return;

        int strength = Math.max(4, Math.min(120, warpStrength.get()));
        int band = Math.max(24, height / 7);

        int edge = argb(strength / 2, 8, 18, 28);
        int cleared = argb(0, 8, 18, 28);

        event.drawContext.fillGradient(0, 0, width, band, edge, cleared);
        event.drawContext.fillGradient(0, height - band, width, height, cleared, edge);
        event.drawContext.fill(0, 0, Math.max(2, width / 90), height, argb(strength / 3, 8, 18, 28));
        event.drawContext.fill(width - Math.max(2, width / 90), 0, width, height, argb(strength / 3, 8, 18, 28));
    }

    private static final class Ghost {
        final double x;
        final double y;
        final double z;
        final float width;
        final float height;
        final long time;

        Ghost(double x, double y, double z, float width, float height, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.time = time;
        }
    }

    private static final class Trail {
        final Deque<Ghost> ghosts = new ArrayDeque<>();
        double lastX;
        double lastY;
        double lastZ;
        long lastSample;

        Trail(Player player) {
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            lastSample = System.nanoTime();
        }
    }

    private static final class Ripple {
        final double x;
        final double y;
        final double z;
        final long start;

        Ripple(double x, double y, double z, long start) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.start = start;
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

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }
}
