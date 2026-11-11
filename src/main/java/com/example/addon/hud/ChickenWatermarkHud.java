package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Chicken Client's animated watermark, implemented as a HUD element so it can be
 * positioned with the HUD editor and configured from its own settings screen.
 */
public class ChickenWatermarkHud extends HudElement {
    public static final HudElementInfo<ChickenWatermarkHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP,
        "chicken-watermark",
        "Animated Chicken Client cloud watermark.",
        ChickenWatermarkHud::new
    );

    private static final String WORDMARK = "CHICKEN CLIENT";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCloud = settings.createGroup("Cloud");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Watermark scale.")
        .defaultValue(1.0)
        .range(0.5, 3.0)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private final Setting<Double> animationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .description("Speed of every animation.")
        .defaultValue(1.0)
        .range(0.1, 4.0)
        .sliderRange(0.2, 3.0)
        .build()
    );

    private final Setting<Boolean> wordmark = sgGeneral.add(new BoolSetting.Builder()
        .name("wordmark")
        .description("Draw the CHICKEN CLIENT text next to the cloud.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw the animated dark panel behind the watermark.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> motionBlur = sgGeneral.add(new BoolSetting.Builder()
        .name("motion-blur")
        .description("Draw ghost copies behind the cloud.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> blurCopies = sgGeneral.add(new IntSetting.Builder()
        .name("blur-copies")
        .defaultValue(4)
        .range(1, 8)
        .sliderRange(1, 6)
        .visible(motionBlur::get)
        .build()
    );

    private final Setting<Boolean> particles = sgGeneral.add(new BoolSetting.Builder()
        .name("particles")
        .description("Orbiting accent dots around the cloud.")
        .defaultValue(true)
        .build()
    );

    // Cloud

    private final Setting<Boolean> cloud = sgCloud.add(new BoolSetting.Builder()
        .name("cloud")
        .description("Draw the animated cloud logo.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glow = sgCloud.add(new BoolSetting.Builder()
        .name("glow")
        .description("Soft pulsing glow around the cloud.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lightning = sgCloud.add(new BoolSetting.Builder()
        .name("lightning")
        .description("Flickering lightning bolt below the cloud.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rain = sgCloud.add(new BoolSetting.Builder()
        .name("rain")
        .description("Animated rain drops falling below the cloud.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rainDrops = sgCloud.add(new IntSetting.Builder()
        .name("rain-drops")
        .defaultValue(18)
        .range(4, 48)
        .sliderRange(4, 32)
        .visible(rain::get)
        .build()
    );

    // Colors

    private final Setting<SettingColor> primary = sgColors.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(0, 220, 255))
        .build()
    );

    private final Setting<SettingColor> secondary = sgColors.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 65, 190))
        .build()
    );

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(245, 248, 255))
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .defaultValue(new SettingColor(0, 0, 0, 115))
        .build()
    );

    public ChickenWatermarkHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double time = (System.currentTimeMillis() % 600000L) / 1000.0;
        double speed = animationSpeed.get();
        double s = scale.get();

        double unit = 1.55 * s;
        double cloudWidth = 33.0 * unit;
        double cloudHeight = 13.0 * unit;
        double cloudGap = 9.0 * unit;

        double textScale = s;
        double wordWidth = wordmark.get() ? renderer.textWidth(WORDMARK, shadow.get(), textScale) : 0;
        double wordHeight = wordmark.get() ? renderer.textHeight(shadow.get(), textScale) : 0;

        double extraHeight = 0;
        if (cloud.get() && (lightning.get() || rain.get())) extraHeight = 9.0 * unit;
        double cloudBlockHeight = cloud.get() ? cloudHeight + extraHeight : 0;

        double contentWidth = (cloud.get() ? cloudWidth + (wordmark.get() ? cloudGap : 0) : 0) + wordWidth;
        double contentHeight = Math.max(cloudBlockHeight, wordHeight);

        double padX = 8.0 * unit;
        double padY = 7.0 * unit;
        double panelWidth = Math.max(1, contentWidth + padX * 2.0);
        double panelHeight = Math.max(1, contentHeight + padY * 2.0);

        setSize(panelWidth, panelHeight);

        float phase = (float) ((Math.sin(time * 0.9 * speed) + 1.0) * 0.5);
        Color a = lerp(primary.get(), secondary.get(), phase);
        Color b = lerp(secondary.get(), primary.get(), phase);
        Color textBase = textColor.get();

        double drift = Math.sin(time * 0.8 * speed) * 1.6;
        double bob = Math.sin(time * 1.9 * speed) * 1.0;
        double cloudX = x + padX + drift;
        double cloudY = y + padY + bob + (contentHeight - cloudBlockHeight) * 0.5;
        double wordX = cloud.get() ? cloudX + cloudWidth + cloudGap : x + padX;
        double wordY = y + padY + (contentHeight - wordHeight) * 0.5;

        if (background.get()) drawBackground(renderer, time, speed, a, b);

        if (cloud.get()) {
            if (glow.get()) drawCloudGlow(renderer, cloudX, cloudY, cloudWidth, cloudHeight, a, b, time, speed);

            if (motionBlur.get()) {
                for (int i = blurCopies.get(); i >= 1; i--) {
                    double ghostX = cloudX - i * 2.4 * s;
                    double ghostY = cloudY + i * 0.7 * s;
                    int alpha = 14 + i * 4;
                    drawCloud(renderer, ghostX, ghostY, cloudWidth, cloudHeight, a, b, alpha / 255.0);
                }
            }

            drawCloud(renderer, cloudX, cloudY, cloudWidth, cloudHeight, a, b, 1.0);

            if (lightning.get()) drawLightning(renderer, cloudX, cloudY, cloudWidth, cloudHeight, a, b, time, speed);
            if (rain.get()) drawRain(renderer, cloudX, cloudY, cloudWidth, cloudHeight, a, b, time, speed, rainDrops.get());
        }

        if (wordmark.get()) drawWordmark(renderer, wordX, wordY, a, b, textBase, time, speed, textScale);

        if (particles.get()) {
            double centerX = cloud.get() ? cloudX + cloudWidth * 0.5 : x + getWidth() * 0.5;
            double centerY = cloud.get() ? cloudY + cloudHeight * 0.5 : y + getHeight() * 0.5;
            drawParticles(renderer, centerX, centerY, s, time, speed, a, b);
        }
    }

    private void drawBackground(HudRenderer renderer, double time, double speed, Color a, Color b) {
        Color bg = backgroundColor.get();
        double pulse = 0.5 + 0.5 * Math.sin(time * 2.2 * speed);
        int alpha = Math.min(255, bg.a + (int) (18 * pulse));
        renderer.quad(x, y, getWidth(), getHeight(), withAlpha(bg, alpha));

        double lineHeight = Math.max(1.0, 1.4 * scale.get());
        renderer.quad(x, y, getWidth(), lineHeight, withAlpha(a, 210), withAlpha(b, 210), withAlpha(b, 210), withAlpha(a, 210));
        renderer.quad(x, y + getHeight() - lineHeight, getWidth(), lineHeight,
            withAlpha(b, 150), withAlpha(a, 150), withAlpha(a, 150), withAlpha(b, 150));

        double scanWidth = Math.min(24.0 * scale.get(), getWidth());
        double scan = (time * 58.0 * speed) % Math.max(1.0, getWidth());
        double scanX = Math.min(x + scan, x + getWidth() - scanWidth);
        renderer.quad(scanX, y + getHeight() - lineHeight * 1.6, scanWidth, lineHeight * 1.6,
            withAlpha(a, 225), withAlpha(a, 20), withAlpha(a, 20), withAlpha(a, 225));

        double corner = Math.max(1.0, 2.0 * scale.get());
        renderer.quad(x, y, corner, corner, withAlpha(a, 235));
        renderer.quad(x + getWidth() - corner, y, corner, corner, withAlpha(b, 235));
        renderer.quad(x, y + getHeight() - corner, corner, corner, withAlpha(b, 235));
        renderer.quad(x + getWidth() - corner, y + getHeight() - corner, corner, corner, withAlpha(a, 235));
    }

    private void drawCloudGlow(HudRenderer renderer, double x, double y, double width, double height, Color a, Color b, double time, double speed) {
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.2 * speed);
        int alpha = 20 + (int) (34 * pulse);
        double grow = 2.5 * scale.get() + pulse * 1.5;

        drawPuff(renderer, x + width * 0.50, y + height * 0.50, height * 0.58 + grow,
            withAlpha(a, alpha), withAlpha(b, alpha), 1.0);
        drawPuff(renderer, x + width * 0.24, y + height * 0.58, height * 0.40 + grow,
            withAlpha(a, alpha / 2), withAlpha(b, alpha / 2), 1.0);
        drawPuff(renderer, x + width * 0.80, y + height * 0.60, height * 0.42 + grow,
            withAlpha(a, alpha / 2), withAlpha(b, alpha / 2), 1.0);
    }

    private void drawCloud(HudRenderer renderer, double x, double y, double width, double height, Color a, Color b, double alpha) {
        Color top = withAlpha(lerp(a, Color.WHITE, 0.28f), (int) (255 * alpha));
        Color bottom = withAlpha(lerp(b, Color.BLACK, 0.12f), (int) (220 * alpha));

        // Main cloud body.
        renderer.quad(x + width * 0.10, y + height * 0.43, width * 0.80, height * 0.57,
            top, top, bottom, bottom);

        // Three rounded puffs make the classic cloud silhouette.
        drawPuff(renderer, x + width * 0.26, y + height * 0.48, height * 0.36, top, bottom, alpha);
        drawPuff(renderer, x + width * 0.51, y + height * 0.40, height * 0.44, top, bottom, alpha);
        drawPuff(renderer, x + width * 0.77, y + height * 0.50, height * 0.34, top, bottom, alpha);

        // A small moving highlight keeps the cloud looking alive.
        double shine = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 430.0);
        renderer.quad(x + width * (0.34 + shine * 0.10), y + height * 0.24, width * 0.20, height * 0.08,
            withAlpha(Color.WHITE, (int) (65 * alpha)));
    }

    private void drawPuff(HudRenderer renderer, double cx, double cy, double radius, Color top, Color bottom, double alpha) {
        int bands = 8;
        for (int i = 0; i < bands; i++) {
            double t = (i + 0.5) / bands;
            double y = cy - radius + t * radius * 2.0;
            double half = radius * Math.sqrt(Math.max(0.0, 1.0 - Math.pow(t * 2.0 - 1.0, 2.0)));
            double bandHeight = (radius * 2.0 / bands) * 1.35;
            Color color = lerp(top, bottom, (float) (t * 0.9));
            renderer.quad(cx - half, y - bandHeight * 0.5, half * 2.0, bandHeight, color);
        }
    }

    private void drawLightning(HudRenderer renderer, double x, double y, double width, double height, Color a, Color b, double time, double speed) {
        double flicker = Math.sin(time * 7.5 * speed);
        if (flicker < 0.12) return;

        double strength = Math.min(1.0, (flicker - 0.12) / 0.88);
        Color boltColor = withAlpha(lerp(a, Color.WHITE, 0.55f), (int) (255 * strength));
        Color edgeColor = withAlpha(lerp(b, Color.WHITE, 0.25f), (int) (130 * strength));

        double[][] points = {
            {0.50, 0.91},
            {0.43, 1.08},
            {0.55, 1.18},
            {0.46, 1.34},
            {0.54, 1.47}
        };

        double previousX = x + width * points[0][0];
        double previousY = y + height * points[0][1];

        for (int i = 1; i < points.length; i++) {
            double nextX = x + width * points[i][0];
            double nextY = y + height * points[i][1];

            renderer.line(previousX, previousY, nextX, nextY, boltColor);
            renderer.line(previousX + 1.2 * scale.get(), previousY, nextX + 1.2 * scale.get(), nextY, edgeColor);
            renderer.line(previousX - 1.2 * scale.get(), previousY, nextX - 1.2 * scale.get(), nextY, edgeColor);

            previousX = nextX;
            previousY = nextY;
        }
    }

    private void drawRain(HudRenderer renderer, double x, double y, double width, double height,
                          Color a, Color b, double time, double speed, int drops) {
        for (int i = 0; i < drops; i++) {
            double seed = (i * 0.61803398875) % 1.0;
            double fallSpeed = 0.45 + (i % 6) * 0.075;
            double fall = (time * speed * fallSpeed + seed) % 1.0;
            double dropX = x + width * (0.06 + seed * 0.88) + Math.sin(time * 0.9 * speed + i) * 1.6;
            double dropY = y + height * 0.92 + fall * height * 0.62;

            int alpha = (int) (185 * (1.0 - fall));
            renderer.quad(dropX, dropY, Math.max(1.0, 0.8 * scale.get()), Math.max(2.0, 1.8 * scale.get()),
                withAlpha(lerp(a, b, (float) seed), alpha));
        }
    }

    private void drawWordmark(HudRenderer renderer, double x, double y, Color a, Color b, Color base,
                              double time, double speed, double textScale) {
        int shimmer = (int) ((time * 4.5 * speed) % WORDMARK.length());
        double cursor = x;

        for (int i = 0; i < WORDMARK.length(); i++) {
            String character = String.valueOf(WORDMARK.charAt(i));
            Color color = lerp(a, base, (float) i / Math.max(1, WORDMARK.length() - 1));
            if (i == shimmer) color = lerp(color, Color.WHITE, 0.72f);

            double wave = Math.sin(time * 2.8 * speed + i * 0.40) * 0.8;
            renderer.text(character, cursor, y + wave, color, shadow.get(), textScale);
            cursor += renderer.textWidth(character, shadow.get(), textScale);
        }

        // A soft pulse travels across the wordmark.
        double pulse = (time * 34.0 * speed) % Math.max(1.0, getWidth());
        renderer.quad(x + pulse, y + getHeight() - 3.0 * scale.get(), 18.0 * scale.get(),
            Math.max(1.0, 1.2 * scale.get()), withAlpha(b, 120));
    }

    private void drawParticles(HudRenderer renderer, double centerX, double centerY, double s,
                               double time, double speed, Color a, Color b) {
        for (int i = 0; i < 7; i++) {
            double angle = time * (0.7 + i * 0.13) * speed + i * 1.37;
            double radiusX = (12.0 + i * 4.0) * s;
            double radiusY = (5.0 + i * 2.0) * s;
            double dotX = centerX + Math.cos(angle) * radiusX;
            double dotY = centerY + Math.sin(angle) * radiusY;
            double twinkle = 0.5 + 0.5 * Math.sin(time * 4.0 * speed + i * 1.8);

            int alpha = 65 + (int) (150 * twinkle);
            double size = (i % 3 == 0 ? 2.4 : 1.6) * s;
            renderer.quad(dotX, dotY, size, size, withAlpha(i % 2 == 0 ? a : b, alpha));
        }
    }

    private static Color lerp(Color from, Color to, float t) {
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
