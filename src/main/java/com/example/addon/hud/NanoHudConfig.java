package com.example.addon.hud;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Standalone configuration for the Chicken Client HUD.
 *
 * <p>This intentionally has no Meteor dependency: the HUD keeps its own file,
 * toggle key and editor state so it can run independently of Meteor's module
 * and renderer systems.</p>
 */
public final class NanoHudConfig {
    public enum HudMode {
        NORMAL,
        LEFT_HAND
    }

    private static final Logger LOG = LoggerFactory.getLogger("ChickenClient/NanoHud");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("chicken-client").resolve("nano-hud.properties");

    private static NanoHudConfig instance;

    public boolean enabled = true;
    public int x = 10;
    public int y = 10;
    public double scale = 1.0;
    public int editorKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public int toggleKey = GLFW.GLFW_KEY_F7;
    public int settingsKey = GLFW.GLFW_KEY_P;
    // The client's own module GUI. Right shift is what people expect from a
    // client menu, and this addon opens its GUI after Meteor's handler has
    // already run, so right shift ends up on the Chicken GUI.
    public int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public int backgroundAlpha = 205;

    public boolean showPlayer = true;
    public boolean showWatermark = true;
    public boolean showStats = true;
    public boolean animations = true;
    public boolean lowHealthVignette = true;
    public boolean modernFont = true;
    public HudMode hudMode = HudMode.NORMAL;

    public double wingSpeed = 1.0;
    public double wingAmplitude = 1.0;

    // Chicken Client's main brand color is yellow.
    public int primary = 0xFFC400;
    public int secondary = 0xFF8A00;
    public int accent = 0xFFF3D0;

    public final long openedAt = System.currentTimeMillis();

    private boolean dragging;
    private double dragX;
    private double dragY;

    private NanoHudConfig() {
    }

    public static NanoHudConfig get() {
        if (instance == null) {
            instance = new NanoHudConfig();
            instance.load();
        }
        return instance;
    }

    public void toggle() {
        enabled = !enabled;
        save();
    }

    public double openProgress() {
        if (!animations) return 1.0;

        double t = Math.min(1.0, (System.currentTimeMillis() - openedAt) / 480.0);
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    public double panelX() {
        return dragging ? dragX : x;
    }

    public double panelY() {
        return dragging ? dragY : y;
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
        x = (int) Math.round(dragX);
        y = (int) Math.round(dragY);
        dragging = false;
        save();
    }

    public boolean isDragging() {
        return dragging;
    }

    private void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
        } catch (Throwable throwable) {
            LOG.warn("Could not read {}", FILE, throwable);
            return;
        }

        enabled = bool(properties, "enabled", enabled);
        x = integer(properties, "x", x);
        y = integer(properties, "y", y);
        scale = decimal(properties, "scale", scale);
        editorKey = integer(properties, "editor-key", editorKey);
        toggleKey = integer(properties, "toggle-key", toggleKey);
        settingsKey = integer(properties, "settings-key", settingsKey);
        guiKey = integer(properties, "gui-key", guiKey);

        // The settings menu moved from F8 to P; migrate existing configs once.
        if (settingsKey == GLFW.GLFW_KEY_F8) {
            settingsKey = GLFW.GLFW_KEY_P;
            save();
        }
        backgroundAlpha = integer(properties, "background-alpha", backgroundAlpha);
        showPlayer = bool(properties, "show-player", showPlayer);
        showWatermark = bool(properties, "show-watermark", showWatermark);
        showStats = bool(properties, "show-stats", showStats);
        animations = bool(properties, "animations", animations);
        lowHealthVignette = bool(properties, "low-health-vignette", lowHealthVignette);
        modernFont = bool(properties, "modern-font", modernFont);
        try {
            hudMode = HudMode.valueOf(properties.getProperty("hud-mode", hudMode.name()));
        } catch (Throwable ignored) {
            hudMode = HudMode.NORMAL;
        }
        wingSpeed = decimal(properties, "wing-speed", wingSpeed);
        wingAmplitude = decimal(properties, "wing-amplitude", wingAmplitude);
        primary = color(properties, "primary", primary);
        secondary = color(properties, "secondary", secondary);
        accent = color(properties, "accent", accent);

        // Migrate the old cyan/pink defaults so existing installs pick up the
        // yellow Chicken Client brand color automatically.
        if (primary == 0x00DCFF && secondary == 0xFF375F) {
            primary = 0xFFC400;
            secondary = 0xFF8A00;
            accent = 0xFFF3D0;
            save();
        }
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("scale", Double.toString(scale));
        properties.setProperty("editor-key", Integer.toString(editorKey));
        properties.setProperty("toggle-key", Integer.toString(toggleKey));
        properties.setProperty("settings-key", Integer.toString(settingsKey));
        properties.setProperty("gui-key", Integer.toString(guiKey));
        properties.setProperty("background-alpha", Integer.toString(backgroundAlpha));
        properties.setProperty("show-player", Boolean.toString(showPlayer));
        properties.setProperty("show-watermark", Boolean.toString(showWatermark));
        properties.setProperty("show-stats", Boolean.toString(showStats));
        properties.setProperty("animations", Boolean.toString(animations));
        properties.setProperty("low-health-vignette", Boolean.toString(lowHealthVignette));
        properties.setProperty("modern-font", Boolean.toString(modernFont));
        properties.setProperty("hud-mode", hudMode.name());
        properties.setProperty("wing-speed", Double.toString(wingSpeed));
        properties.setProperty("wing-amplitude", Double.toString(wingAmplitude));
        properties.setProperty("primary", Integer.toHexString(primary));
        properties.setProperty("secondary", Integer.toHexString(secondary));
        properties.setProperty("accent", Integer.toHexString(accent));

        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Chicken Client standalone HUD");
            }
        } catch (Throwable throwable) {
            LOG.warn("Could not save {}", FILE, throwable);
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double decimal(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int color(Properties properties, String key, int fallback) {
        try {
            return (int) Long.parseLong(properties.getProperty(key, Integer.toHexString(fallback)), 16);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
