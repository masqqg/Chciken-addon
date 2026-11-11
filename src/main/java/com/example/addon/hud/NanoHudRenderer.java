package com.example.addon.hud;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Locale;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFontMem;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgFontFaceId;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha;
import static org.lwjgl.nanovg.NanoVG.nvgLineTo;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgMoveTo;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokePaint;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;
import static org.lwjgl.nanovg.NanoVG.nvgTextBounds;
import static org.lwjgl.nanovg.NanoVG.nvgTranslate;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;

/**
 * Standalone NanoVG HUD renderer.
 *
 * <p>It owns its context, bundled font, native GL frame and config. The only
 * hook is the mod's own {@code Gui.render} mixin. It does not import or call
 * Meteor's HUD, settings or 2D renderer.</p>
 */
public final class NanoHudRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("ChickenClient/NanoVG");

    private static final float PANEL_WIDTH = 272.0f;
    private static final float PANEL_HEIGHT = 208.0f;

    private static long vg;
    private static int font = -1;
    private static ByteBuffer fontData;
    private static boolean failed;

    private static final NVGColor COLOR_A = NVGColor.malloc();
    private static final NVGColor COLOR_B = NVGColor.malloc();
    private static final NVGPaint PAINT = NVGPaint.malloc();
    private static final FloatBuffer BOUNDS = MemoryUtil.memAllocFloat(4);

    private static boolean dragging;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static boolean toggleHeld;
    private static boolean settingsHeld;
    private static boolean scaleDownHeld;
    private static boolean scaleUpHeld;

    private static final float[] FPS_HISTORY = new float[28];
    private static int fpsIndex;

    private NanoHudRenderer() {
    }

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        NanoHudConfig hud = NanoHudConfig.get();

        boolean toggle = keyDown(mc, hud.toggleKey);
        if (toggle && !toggleHeld) hud.toggle();
        toggleHeld = toggle;

        boolean settings = keyDown(mc, hud.settingsKey);
        if (settings && !settingsHeld && !(mc.screen instanceof NanoHudScreen)) {
            mc.execute(() -> mc.setScreen(new NanoHudScreen()));
        }
        settingsHeld = settings;

        if (!hud.enabled) return;
        if (mc.level == null || mc.player == null || mc.getWindow().isIconified()) return;
        if (mc.options.hideGui) return;
        if (!ensureContext()) return;

        RenderTarget target = mc.getMainRenderTarget();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        if (guiWidth <= 0 || guiHeight <= 0 || target.width <= 0 || target.height <= 0) return;

        int fbo = resolveFramebuffer(target);
        if (fbo == -1) return;

        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
        boolean editor = keyDown(mc, hud.editorKey);
        boolean leftPressed = mc.mouseHandler.isLeftPressed();

        if (editor) {
            if (mc.screen == null && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();

            boolean down = keyDown(mc, GLFW.GLFW_KEY_LEFT_BRACKET);
            boolean up = keyDown(mc, GLFW.GLFW_KEY_RIGHT_BRACKET);
            if (down && !scaleDownHeld) {
                hud.scale = Math.max(0.6, hud.scale - 0.1);
                hud.save();
            }
            if (up && !scaleUpHeld) {
                hud.scale = Math.min(2.5, hud.scale + 0.1);
                hud.save();
            }
            scaleDownHeld = down;
            scaleUpHeld = up;
        }
        else {
            scaleDownHeld = false;
            scaleUpHeld = false;
            if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
        }

        handleDragging(hud, editor, leftPressed, mouseX, mouseY);

        float dpr = (float) target.width / guiWidth;

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, target.width, target.height);
        GL33C.glBindSampler(0, 0);

        nvgBeginFrame(vg, target.width, target.height, dpr);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);

        try {
            draw(hud, mc, editor);
        } catch (Throwable throwable) {
            LOG.error("NanoVG HUD draw failed", throwable);
        }

        nvgEndFrame(vg);

        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ZERO);
    }

    private static void handleDragging(NanoHudConfig hud, boolean editor, boolean leftPressed, double mouseX, double mouseY) {
        if (!editor) {
            if (dragging) {
                hud.endDrag();
                dragging = false;
            }
            return;
        }

        double scale = hud.scale;
        double panelX = hud.panelX();
        double panelY = hud.panelY();
        double panelWidth = PANEL_WIDTH * scale;
        double panelHeight = PANEL_HEIGHT * scale;

        if (leftPressed) {
            if (!dragging
                && mouseX >= panelX && mouseX <= panelX + panelWidth
                && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                dragging = true;
                dragOffsetX = mouseX - panelX;
                dragOffsetY = mouseY - panelY;
                hud.beginDrag(panelX, panelY);
            }

            if (dragging) hud.dragTo(mouseX - dragOffsetX, mouseY - dragOffsetY);
        }
        else if (dragging) {
            hud.endDrag();
            dragging = false;
        }
    }

    private static void draw(NanoHudConfig hud, Minecraft mc, boolean editor) {
        double time = System.nanoTime() / 1_000_000_000.0;
        double open = editor ? 1.0 : hud.openProgress();
        double scale = hud.scale;

        double panelX = hud.panelX() + (1.0 - open) * -26.0;
        double panelY = hud.panelY() + (1.0 - open) * -8.0;
        float alpha = (float) (0.08 + 0.92 * open);

        nvgSave(vg);
        nvgTranslate(vg, (float) panelX, (float) panelY);
        nvgScale(vg, (float) scale, (float) scale);
        nvgGlobalAlpha(vg, alpha);
        drawPanel(hud, mc, time, editor);
        nvgRestore(vg);
    }

    private static void drawPanel(NanoHudConfig hud, Minecraft mc, double time, boolean editor) {
        int primary = hud.primary;
        int secondary = hud.secondary;
        int accent = hud.accent;
        int bgAlpha = hud.backgroundAlpha;

        fillRound(-8.0f, -8.0f, PANEL_WIDTH + 16.0f, PANEL_HEIGHT + 16.0f, 22.0f, argb(0, 0, 0, 80));
        fillRound(-3.0f, -3.0f, PANEL_WIDTH + 6.0f, PANEL_HEIGHT + 6.0f, 19.0f, argb(0, 0, 0, 105));
        fillRound(0.0f, 0.0f, PANEL_WIDTH, PANEL_HEIGHT, 16.0f, argb(8, 12, 18, bgAlpha));
        fillRound(1.0f, 1.0f, PANEL_WIDTH - 2.0f, PANEL_HEIGHT - 2.0f, 15.0f, argb(14, 20, 30, (int) (bgAlpha * 0.72)));

        setColor(COLOR_A, argb(primary, 230));
        setColor(COLOR_B, argb(secondary, 230));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, 0.8f, 0.8f, PANEL_WIDTH - 1.6f, PANEL_HEIGHT - 1.6f, 15.5f);
        nvgStrokeWidth(vg, 1.4f);
        nvgLinearGradient(vg, 0.0f, 0.0f, PANEL_WIDTH, PANEL_HEIGHT, COLOR_A, COLOR_B, PAINT);
        nvgStrokePaint(vg, PAINT);
        nvgStroke(vg);

        fillGradient(12.0f, 0.0f, PANEL_WIDTH - 24.0f, 1.6f, argb(primary, 220), argb(secondary, 20), true);
        if (hud.animations) {
            float scanY = 8.0f + (float) ((time * 34.0) % (PANEL_HEIGHT - 16.0));
            fillGradient(18.0f, scanY, PANEL_WIDTH - 36.0f, 1.0f, argb(primary, 0), argb(primary, 75), false);
        }
        drawParticles(hud, time, primary, secondary);

        if (hud.showWatermark) {
            drawCloud(30.0f, 25.0f, 1.0f, time, hud, primary, secondary);
            drawWordmark("CHICKEN CLIENT", 58.0f, 16.0f, 12.5f, time, primary, secondary);
            text("NANO HUD  //  CHICKEN CLIENT", 58.0f, 33.0f, 7.2f, argb(190, 202, 218, 190));
        }

        fillGradient(16.0f, 48.0f, PANEL_WIDTH - 32.0f, 1.0f, argb(primary, 80), argb(secondary, 20), true);

        LocalPlayer player = mc.player;
        if (hud.showPlayer && player != null) drawPlayerCard(mc, player, time, primary, secondary, accent, hud.animations);
        if (hud.showStats && player != null) drawStats(mc, player, time, primary, secondary);

        fillRound(16.0f, PANEL_HEIGHT - 15.0f, 3.0f, 3.0f, 1.5f, argb(secondary, 220));
        text("CHICKEN CLIENT  •  STANDALONE NANO HUD", 22.0f, PANEL_HEIGHT - 18.0f, 6.4f, argb(150, 170, 190, 145));

        if (editor) drawEditorOverlay(primary, secondary);
    }

    private static void drawPlayerCard(Minecraft mc, LocalPlayer player, double time,
                                       int primary, int secondary, int accent, boolean animations) {
        float top = 58.0f;

        float health = Math.max(0.0f, player.getHealth());
        float maxHealth = Math.max(1.0f, player.getMaxHealth());
        float healthRatio = Math.min(1.0f, health / maxHealth);
        boolean danger = healthRatio < 0.35f;

        drawPlayerWings(time, animations, primary, secondary);

        fillRound(18.0f, top, 44.0f, 44.0f, 13.0f, argb(30, 42, 58, 220));
        fillRoundGradient(19.0f, top + 1.0f, 42.0f, 42.0f, 12.0f, argb(primary, 70), argb(secondary, 45), true);

        if (animations) {
            for (int i = 0; i < 10; i++) {
                double angle = time * 1.35 + i * Math.PI / 5.0;
                float auraX = 40.0f + (float) Math.cos(angle) * 14.0f;
                float auraY = top + 18.0f + (float) Math.sin(angle) * 14.0f;
                nvgBeginPath(vg);
                nvgCircle(vg, auraX, auraY, 1.1f);
                setColor(COLOR_A, argb(i % 2 == 0 ? primary : secondary, 115));
                nvgFillColor(vg, COLOR_A);
                nvgFill(vg);
            }
        }

        nvgBeginPath(vg);
        nvgCircle(vg, 40.0f, top + 18.0f, 7.8f);
        nvgRoundedRect(vg, 29.0f, top + 27.0f, 22.0f, 15.0f, 7.0f);
        setColor(COLOR_A, 235, 242, 255, 225);
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);

        text(player.getName().getString(), 72.0f, top + 2.0f, 12.2f, argb(242, 248, 255, 255));
        text("LOCAL PLAYER", 72.0f, top + 17.0f, 7.0f, argb(140, 170, 195, 190));

        int healthAlpha = danger
            ? 175 + (int) (60 * (0.5 + 0.5 * Math.sin(time * 7.0)))
            : 235;
        int healthColor = danger ? argb(255, 70, 90, healthAlpha) : argb(primary, healthAlpha);
        bar(72.0f, top + 29.0f, 142.0f, 6.0f, healthRatio, healthColor, argb(26, 34, 52, 220), time);
        bar(72.0f, top + 39.0f, 142.0f, 4.5f, Math.min(1.0f, player.getArmorValue() / 20.0f), argb(primary, 220), argb(20, 28, 40, 210), time);
        bar(72.0f, top + 47.0f, 142.0f, 3.5f, player.experienceProgress, argb(accent, 220), argb(20, 28, 40, 210), time);

        text(String.format(Locale.ROOT, "%.1f / %.0f", health, maxHealth), 218.0f, top + 28.0f, 6.7f, argb(230, 240, 250, 205));
        text(String.format(Locale.ROOT, "ARMOR %d", player.getArmorValue()), 218.0f, top + 39.0f, 6.3f, argb(primary, 205));
        if (danger) {
            text("LOW HP", 218.0f, top + 48.0f, 6.0f, argb(255, 105, 125, 210));
        }
        else {
            text(String.format(Locale.ROOT, "LVL %d", player.experienceLevel), 218.0f, top + 48.0f, 6.0f, argb(primary, 190));
        }
    }

    private static void drawStats(Minecraft mc, LocalPlayer player, double time, int primary, int secondary) {
        float top = 124.0f;
        float left = 18.0f;
        float col = 62.0f;

        int ping = -1;
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) ping = info.getLatency();
        }

        int fps = mc.getFps();
        FPS_HISTORY[fpsIndex] = fps;
        fpsIndex = (fpsIndex + 1) % FPS_HISTORY.length;

        double speed = Math.sqrt(player.getDeltaMovement().x * player.getDeltaMovement().x
            + player.getDeltaMovement().z * player.getDeltaMovement().z) * 20.0;

        stat("PING", ping < 0 ? "--" : ping + "ms", left, top);
        stat("FPS", Integer.toString(fps), left + col, top);
        stat("SPEED", String.format(Locale.ROOT, "%.1f", speed), left + col * 2.0f, top);
        stat("STATUS", "LIVE", left + col * 3.0f, top);

        fillGradient(16.0f, top + 26.0f, PANEL_WIDTH - 32.0f, 1.0f, argb(primary, 50), argb(secondary, 15), true);

        text(String.format(Locale.ROOT, "XYZ  %.0f  %.0f  %.0f", player.getX(), player.getY(), player.getZ()),
            18.0f, top + 34.0f, 7.0f, argb(165, 185, 205, 190));
        text(String.format(Locale.ROOT, "DIM  %s", player.level().dimension().toString()),
            18.0f, top + 45.0f, 6.4f, argb(135, 160, 185, 165));

        drawFpsGraph(primary, secondary, top);
    }

    private static void drawParticles(NanoHudConfig hud, double time, int primary, int secondary) {
        if (!hud.animations) return;

        for (int i = 0; i < 14; i++) {
            double fall = 5.0 + (i % 5) * 3.0;
            float px = 18.0f + ((i * 53) % (int) (PANEL_WIDTH - 36.0f));
            float py = (float) (PANEL_HEIGHT - 12.0 - ((time * fall + i * 23.0) % (PANEL_HEIGHT - 24.0)));
            float radius = 0.8f + (i % 3) * 0.4f;

            nvgBeginPath(vg);
            nvgCircle(vg, px, py, radius);
            setColor(COLOR_A, argb(i % 2 == 0 ? primary : secondary, 35 + (i % 4) * 16));
            nvgFillColor(vg, COLOR_A);
            nvgFill(vg);
        }
    }

    private static void drawPlayerWings(double time, boolean animations, int primary, int secondary) {
        float top = 58.0f;
        NanoHudConfig hud = NanoHudConfig.get();
        double speed = Math.max(0.15, hud.wingSpeed);
        double amplitude = Math.max(0.1, hud.wingAmplitude);
        float flap = animations
            ? (float) Math.sin(time * 6.5 * speed) * 2.4f * (float) amplitude
            : 0.0f;

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float rootX = 40.0f + side * 10.0f;
                float rootY = top + 19.0f;
                float tipX = 40.0f + side * (22.0f + i * 6.0f);
                float tipY = top + 9.0f + i * 5.0f + flap * (1.0f + i * 0.35f);

                nvgBeginPath(vg);
                nvgMoveTo(vg, rootX, rootY - 2.0f);
                nvgLineTo(vg, tipX, tipY);
                nvgLineTo(vg, rootX, rootY + 2.0f);
                setColor(COLOR_A, argb(i == 0 ? primary : secondary, 55 - i * 10));
                nvgFillColor(vg, COLOR_A);
                nvgFill(vg);

                line(rootX, rootY, tipX, tipY, 1.6f - i * 0.3f,
                    argb(i == 0 ? primary : secondary, 200 - i * 35));
            }
        }
    }

    private static void drawFpsGraph(int primary, int secondary, float top) {
        float gx = 150.0f;
        float gy = top + 30.0f;
        float gw = 104.0f;
        float gh = 26.0f;

        fillRound(gx, gy, gw, gh, 4.0f, argb(0, 0, 0, 120));
        setColor(COLOR_A, argb(primary, 20));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, gx, gy, gw, gh, 4.0f);
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);

        text("FPS GRAPH", gx + 3.0f, gy - 7.0f, 5.6f, argb(140, 170, 200, 175));

        float previousX = 0.0f;
        float previousY = 0.0f;
        for (int i = 0; i < FPS_HISTORY.length; i++) {
            float value = FPS_HISTORY[(fpsIndex + i) % FPS_HISTORY.length];
            float x = gx + 2.0f + (i / (float) (FPS_HISTORY.length - 1)) * (gw - 4.0f);
            float y = gy + gh - 2.0f - Math.min(1.0f, value / 240.0f) * (gh - 4.0f);

            if (i > 0) {
                line(previousX, previousY, x, y, 1.1f,
                    argb(i > FPS_HISTORY.length / 2 ? primary : secondary, 175));
            }
            previousX = x;
            previousY = y;
        }
    }

    private static void stat(String label, String value, float x, float y) {
        text(label, x, y, 6.2f, argb(130, 155, 180, 170));
        text(value, x, y + 10.0f, 9.0f, argb(235, 242, 255, 240));
    }

    private static void drawCloud(float cx, float cy, float scale, double time,
                                  NanoHudConfig hud, int primary, int secondary) {
        float bob = hud.animations ? (float) Math.sin(time * 2.1) * 1.3f : 0.0f;
        float pulse = hud.animations ? (float) (0.5 + 0.5 * Math.sin(time * 3.0)) : 0.5f;

        nvgSave(vg);
        nvgTranslate(vg, cx, cy + bob);
        nvgScale(vg, scale, scale);

        nvgBeginPath(vg);
        nvgCircle(vg, -8.0f, 2.0f, 11.0f + pulse);
        nvgCircle(vg, 8.0f, 2.0f, 12.0f + pulse);
        nvgRoundedRect(vg, -16.0f, 0.0f, 32.0f, 11.0f, 5.0f);
        setColor(COLOR_A, argb(primary, 18));
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);

        nvgBeginPath(vg);
        nvgCircle(vg, -8.0f, 1.0f, 8.5f);
        nvgCircle(vg, 7.0f, 0.0f, 10.0f);
        nvgCircle(vg, 16.0f, 4.0f, 6.0f);
        nvgRoundedRect(vg, -16.0f, 4.0f, 32.0f, 8.0f, 4.0f);
        setColor(COLOR_A, argb(primary, 245));
        setColor(COLOR_B, argb(secondary, 245));
        nvgLinearGradient(vg, -16.0f, -8.0f, 16.0f, 12.0f, COLOR_A, COLOR_B, PAINT);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);

        fillRound(-8.0f, -3.0f, 14.0f, 3.0f, 1.5f, argb(255, 255, 255, 55));
        if (hud.animations && Math.sin(time * 6.2) > 0.35) {
            nvgBeginPath(vg);
            nvgMoveTo(vg, 2.0f, 13.0f);
            nvgLineTo(vg, -1.0f, 18.0f);
            nvgLineTo(vg, 4.0f, 17.0f);
            nvgLineTo(vg, 1.0f, 24.0f);
            nvgStrokeWidth(vg, 1.5f);
            setColor(COLOR_A, 255, 244, 180, 230);
            nvgStrokeColor(vg, COLOR_A);
            nvgStroke(vg);
        }

        nvgRestore(vg);
    }

    private static void drawWordmark(String word, float x, float y, float size, double time,
                                     int primary, int secondary) {
        if (font == -1) return;

        float cursor = x;
        for (int i = 0; i < word.length(); i++) {
            String character = String.valueOf(word.charAt(i));
            float t = i / (float) Math.max(1, word.length() - 1);
            int color = mix(argb(primary, 255), argb(secondary, 255), t);
            float wave = (float) Math.sin(time * 2.8 + i * 0.38) * 0.55f;
            text(character, cursor, y + wave, size, color);
            cursor += textWidth(character, size);
        }
    }

    private static void drawEditorOverlay(int primary, int secondary) {
        float p = 8.0f;
        float len = 16.0f;

        line(p, p, p + len, p, 2.0f, argb(primary, 235));
        line(p, p, p, p + len, 2.0f, argb(primary, 235));
        line(PANEL_WIDTH - p - len, p, PANEL_WIDTH - p, p, 2.0f, argb(secondary, 235));
        line(PANEL_WIDTH - p, p, PANEL_WIDTH - p, p + len, 2.0f, argb(secondary, 235));
        line(p, PANEL_HEIGHT - p, p + len, PANEL_HEIGHT - p, 2.0f, argb(secondary, 235));
        line(p, PANEL_HEIGHT - p - len, p, PANEL_HEIGHT - p, 2.0f, argb(secondary, 235));
        line(PANEL_WIDTH - p - len, PANEL_HEIGHT - p, PANEL_WIDTH - p, PANEL_HEIGHT - p, 2.0f, argb(primary, 235));
        line(PANEL_WIDTH - p, PANEL_HEIGHT - p - len, PANEL_WIDTH - p, PANEL_HEIGHT - p, 2.0f, argb(primary, 235));

        fillRound(14.0f, PANEL_HEIGHT - 30.0f, 234.0f, 14.0f, 7.0f, argb(0, 0, 0, 170));
        text("RSHIFT + DRAG  •  [ ] SCALE  •  F7 HIDE  •  F8 SETTINGS", 20.0f, PANEL_HEIGHT - 27.0f, 6.2f, argb(230, 240, 255, 220));
    }

    private static void bar(float x, float y, float width, float height, float fill,
                            int fillColor, int background, double time) {
        fillRound(x, y, width, height, height * 0.5f, background);
        float value = Math.max(0.0f, Math.min(1.0f, fill));
        if (value <= 0.0f) return;

        fillRound(x, y, width * value, height, height * 0.5f, fillColor);
        float shine = (float) ((time * 90.0) % Math.max(1.0, width * value));
        fillRound(x + Math.max(0.0f, shine - 10.0f), y, 10.0f, height, height * 0.5f, argb(255, 255, 255, 70));
    }

    private static void fillGradient(float x, float y, float width, float height,
                                     int start, int end, boolean vertical) {
        setColor(COLOR_A, start);
        setColor(COLOR_B, end);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, width, height);
        nvgLinearGradient(vg, x, y, vertical ? y + height : x + width, vertical ? y : y, COLOR_A, COLOR_B, PAINT);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    private static void fillRoundGradient(float x, float y, float width, float height, float radius,
                                          int start, int end, boolean vertical) {
        setColor(COLOR_A, start);
        setColor(COLOR_B, end);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgLinearGradient(vg, x, y, vertical ? y + height : x + width, vertical ? y : y, COLOR_A, COLOR_B, PAINT);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    private static void fillRound(float x, float y, float width, float height, float radius, int color) {
        setColor(COLOR_A, color);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);
    }

    private static void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        setColor(COLOR_A, color);
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgStrokeWidth(vg, thickness);
        nvgStrokeColor(vg, COLOR_A);
        nvgStroke(vg);
    }

    private static void text(String text, float x, float y, float size, int color) {
        if (font == -1 || text == null || text.isEmpty()) return;

        setColor(COLOR_A, color);
        nvgFontSize(vg, size);
        nvgFontFaceId(vg, font);
        nvgFillColor(vg, COLOR_A);
        nvgText(vg, x, y, text);
    }

    private static float textWidth(String text, float size) {
        if (font == -1 || text == null || text.isEmpty()) return 0.0f;

        nvgFontSize(vg, size);
        nvgFontFaceId(vg, font);
        return nvgTextBounds(vg, 0.0f, 0.0f, text, BOUNDS);
    }

    private static int argb(int rgb, int alpha) {
        return ((alpha & 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int argb(int r, int g, int b, int a) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    private static int mix(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return argb(r, g, b, a);
    }

    private static void setColor(NVGColor target, int argb) {
        nvgRGBA((byte) ((argb >> 16) & 255), (byte) ((argb >> 8) & 255),
            (byte) (argb & 255), (byte) ((argb >>> 24) & 255), target);
    }

    private static void setColor(NVGColor target, int r, int g, int b, int a) {
        nvgRGBA((byte) r, (byte) g, (byte) b, (byte) a, target);
    }

    private static boolean keyDown(Minecraft mc, int key) {
        return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }

    private static int resolveFramebuffer(RenderTarget target) {
        GpuTexture colorTexture = target.getColorTexture();
        GpuTexture depthTexture = target.getDepthTexture();

        if (!(colorTexture instanceof GlTexture color) || !(depthTexture instanceof GlTexture depth)) return -1;
        if (!(RenderSystem.getDevice() instanceof GlDevice device)) return -1;

        DirectStateAccess stateAccess = device.directStateAccess();
        return color.getFbo(stateAccess, depth);
    }

    private static boolean ensureContext() {
        if (failed) return false;
        if (vg != 0) return true;

        try {
            vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (vg == 0) {
                failed = true;
                LOG.error("Could not create a NanoVG context");
                return false;
            }

            fontData = loadFont();
            if (fontData != null) {
                font = nvgCreateFontMem(vg, "inter", fontData, false);
                if (font == -1) LOG.warn("Could not register the bundled Inter font");
            }
            else {
                LOG.warn("Bundled Inter font was not found; NanoVG text will be hidden");
            }

            return true;
        } catch (Throwable throwable) {
            failed = true;
            LOG.error("NanoVG initialization failed", throwable);
            return false;
        }
    }

    private static ByteBuffer loadFont() {
        try (InputStream input = NanoHudRenderer.class.getResourceAsStream("/assets/template/inter.ttf")) {
            if (input == null) return null;

            byte[] bytes = input.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (Throwable throwable) {
            LOG.warn("Could not load the bundled Inter font", throwable);
            return null;
        }
    }
}
