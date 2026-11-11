package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.lwjgl.glfw.GLFW;

/**
 * Settings and placement state for the standalone NanoVG HUD.
 *
 * <p>The HUD is rendered by {@code ChickenHudMixin} directly from the in-game
 * HUD pass; it deliberately does not use Meteor's HUD elements or its 2D
 * render event.</p>
 */
public class ChickenHud extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgContent = settings.createGroup("Content");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    public final Setting<Integer> x = sgGeneral.add(new IntSetting.Builder()
        .name("x")
        .description("HUD X position.")
        .defaultValue(10)
        .range(0, 4000)
        .sliderRange(0, 800)
        .build()
    );

    public final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
        .name("y")
        .description("HUD Y position.")
        .defaultValue(10)
        .range(0, 4000)
        .sliderRange(0, 800)
        .build()
    );

    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("HUD scale.")
        .defaultValue(1.0)
        .range(0.6, 2.5)
        .sliderRange(0.7, 1.8)
        .build()
    );

    public final Setting<Keybind> editorKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("editor-key")
        .description("Hold this key while dragging the HUD with the left mouse button.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_RIGHT_SHIFT))
        .build()
    );

    public final Setting<Integer> backgroundAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("background-alpha")
        .description("Opacity of the glass panel.")
        .defaultValue(205)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    public final Setting<Boolean> showPlayer = sgContent.add(new BoolSetting.Builder()
        .name("player-card")
        .description("Show the animated local-player card.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> showWatermark = sgContent.add(new BoolSetting.Builder()
        .name("watermark")
        .description("Show the cloud logo and CHICKEN CLIENT wordmark.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> showStats = sgContent.add(new BoolSetting.Builder()
        .name("stats")
        .description("Show ping, FPS, speed, modules and coordinates.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> animations = sgContent.add(new BoolSetting.Builder()
        .name("animations")
        .description("Enable the cloud, rain, lightning, scanline and shimmer animations.")
        .defaultValue(true)
        .build()
    );

    public final Setting<SettingColor> primary = sgColors.add(new ColorSetting.Builder()
        .name("primary")
        .defaultValue(new SettingColor(0, 220, 255))
        .build()
    );

    public final Setting<SettingColor> secondary = sgColors.add(new ColorSetting.Builder()
        .name("secondary")
        .defaultValue(new SettingColor(255, 55, 95))
        .build()
    );

    public final Setting<SettingColor> accent = sgColors.add(new ColorSetting.Builder()
        .name("accent")
        .defaultValue(new SettingColor(255, 196, 64))
        .build()
    );

    private long openedAt = System.currentTimeMillis();
    private boolean dragging;
    private double dragX;
    private double dragY;

    public ChickenHud() {
        super(AddonTemplate.CATEGORY, "chicken-hud", "Standalone NanoVG HUD with an animated player card and cloud watermark.");
        runInMainMenu = true;
        autoSubscribe = false;
    }

    @Override
    public void onActivate() {
        openedAt = System.currentTimeMillis();
        dragging = false;
    }

    @Override
    public void onDeactivate() {
        if (mc.level != null && mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
    }

    public double openProgress() {
        if (!animations.get()) return 1.0;

        double t = Math.min(1.0, (System.currentTimeMillis() - openedAt) / 480.0);
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    public double panelX() {
        return dragging ? dragX : x.get();
    }

    public double panelY() {
        return dragging ? dragY : y.get();
    }

    public void beginDrag(double x, double y) {
        dragging = true;
        dragX = x;
        dragY = y;
    }

    public void dragTo(double mouseX, double mouseY) {
        if (!dragging) return;
        dragX = Math.max(0, mouseX);
        dragY = Math.max(0, mouseY);
    }

    public void endDrag() {
        if (!dragging) return;
        x.set((int) Math.round(dragX));
        y.set((int) Math.round(dragY));
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }
}
