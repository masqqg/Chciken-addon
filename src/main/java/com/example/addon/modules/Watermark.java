package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
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
import net.minecraft.util.ARGB;

/**
 * Small animated brand watermark: a CC monogram with an X under the final C,
 * a gradient wordmark, a pulsing border and motion-blur ghosts.
 */
public class Watermark extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Watermark scale.")
        .defaultValue(1.0)
        .range(0.5, 2.5)
        .sliderRange(0.5, 1.8)
        .build());

    private final Setting<Integer> offsetX = sgGeneral.add(new IntSetting.Builder()
        .name("offset-x")
        .defaultValue(6)
        .range(0, 2000)
        .sliderRange(0, 400)
        .build());

    private final Setting<Integer> offsetY = sgGeneral.add(new IntSetting.Builder()
        .name("offset-y")
        .defaultValue(6)
        .range(0, 2000)
        .sliderRange(0, 400)
        .build());

    private final Setting<Double> animationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .description("Gradient and pulse speed.")
        .defaultValue(1.0)
        .range(0.1, 4.0)
        .sliderRange(0.2, 3.0)
        .build());

    private final Setting<Boolean> motionBlur = sgGeneral.add(new BoolSetting.Builder()
        .name("motion-blur")
        .description("Draw animated ghost copies behind the logo.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> blurCopies = sgGeneral.add(new IntSetting.Builder()
        .name("blur-copies")
        .defaultValue(4)
        .range(1, 8)
        .sliderRange(1, 6)
        .build());

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a dark translucent panel behind the watermark.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> particles = sgGeneral.add(new BoolSetting.Builder()
        .name("particles")
        .description("Animated accent dots around the watermark.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> primary = sgColors.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(0, 220, 255))
        .build());

    private final Setting<SettingColor> secondary = sgColors.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 65, 190))
        .build());

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(245, 248, 255))
        .build());

    public Watermark() {
        super(AddonTemplate.CATEGORY, "chicken-watermark", "Animated Chicken Client branding on screen.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        double time = (System.currentTimeMillis() % 100000L) / 1000.0;
        double speed = animationSpeed.get();
        double s = scale.get();
        double px = offsetX.get();
        double py = offsetY.get() + Math.sin(time * 2.1 * speed) * 1.5;

        TextRenderer text = TextRenderer.get();

        // Measure with the large logo scale, then with the wordmark scale.
        double logoScale = 2.0 * s;
        text.begin(logoScale);
        double cw = text.getWidth("C", true);
        double ch = text.getHeight(true);
        text.end();

        text.begin(s);
        double wordWidth = text.getWidth("CHICKEN CLIENT", true);
        double wordHeight = text.getHeight(true);
        text.end();

        double panelWidth = cw * 2.35 + 12.0 + wordWidth;
        double panelHeight = Math.max(ch * 1.45, wordHeight + 8.0);

        float phase = (float) ((Math.sin(time * speed) + 1.0) * 0.5);
        Color a = lerp(primary.get(), secondary.get(), phase);
        Color b = lerp(secondary.get(), primary.get(), phase);
        Color baseText = textColor.get();

        if (background.get()) {
            int alpha = 95 + (int) (25 * (0.5 + 0.5 * Math.sin(time * 3.0 * speed)));
            event.drawContext.fill((int) px - 5, (int) py - 5,
                (int) (px + panelWidth + 5), (int) (py + panelHeight + 5), ARGB.color(0, 0, 0, alpha));
            event.drawContext.fill((int) px - 5, (int) py - 5,
                (int) (px + panelWidth + 5), (int) py - 4, withAlpha(a, 180).getPacked());
            event.drawContext.fill((int) px - 5, (int) (py + panelHeight + 4),
                (int) (px + panelWidth + 5), (int) (py + panelHeight + 5), withAlpha(b, 130).getPacked());

            // A small highlight travelling along the bottom edge.
            double scan = (time * 52.0 * speed) % Math.max(1.0, panelWidth);
            int sx = (int) (px + scan);
            event.drawContext.fill(sx, (int) (py + panelHeight + 3), sx + 22,
                (int) (py + panelHeight + 5), withAlpha(a, 225).getPacked());
        }

        if (motionBlur.get()) {
            for (int i = blurCopies.get(); i >= 1; i--) {
                double ghostX = px - i * 2.2;
                double ghostY = py + i * 0.7;
                int alpha = 18 + i * 4;
                drawLogo(text, ghostX, ghostY, logoScale, withAlpha(a, alpha), withAlpha(b, alpha));
            }
        }

        drawLogo(text, px, py, logoScale, a, b);

        text.begin(s);
        double wx = px + cw * 2.45;
        double wy = py + (panelHeight - wordHeight) * 0.5;
        for (int i = 0; i < "CHICKEN CLIENT".length(); i++) {
            String character = String.valueOf("CHICKEN CLIENT".charAt(i));
            Color c = lerp(a, baseText, (float) i / Math.max(1, "CHICKEN CLIENT".length() - 1));
            double wave = Math.sin(time * 2.7 * speed + i * 0.38) * 0.7;
            text.render(character, wx, wy + wave, c, true);
            wx += text.getWidth(character, true);
        }
        text.end();

        if (particles.get()) {
            for (int i = 0; i < 5; i++) {
                double angle = time * (0.8 + i * 0.13) * speed + i * 1.3;
                double radius = 3.0 + i * 2.2;
                int dotX = (int) (px + cw + Math.cos(angle) * radius);
                int dotY = (int) (py + ch * 0.5 + Math.sin(angle) * radius);
                event.drawContext.fill(dotX, dotY, dotX + 2, dotY + 2,
                    withAlpha(i % 2 == 0 ? a : b, 120 + i * 20).getPacked());
            }
        }
    }

    private void drawLogo(TextRenderer text, double x, double y, double logoScale, Color a, Color b) {
        text.begin(logoScale);
        double cw = text.getWidth("C", true);
        double ch = text.getHeight(true);

        text.render("C", x, y, a, true);
        text.render("C", x + cw * 0.82, y, b, true);

        // The X hangs just below the end of the second C.
        text.render("X", x + cw * 1.54, y + ch * 0.62, withAlpha(b, 235), true);
        text.end();
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
