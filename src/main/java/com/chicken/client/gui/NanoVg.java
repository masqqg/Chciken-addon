package com.chicken.client.gui;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;
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

/** Shared NanoVG context used by the modern menu. */
public final class NanoVg {
    private static long vg;
    private static int font = -1;
    private static ByteBuffer fontData;
    private static boolean failed;

    private static final NVGColor COLOR_A = NVGColor.malloc();
    private static final NVGColor COLOR_B = NVGColor.malloc();
    private static final NVGPaint PAINT = NVGPaint.malloc();
    private static final FloatBuffer BOUNDS = MemoryUtil.memAllocFloat(4);

    private NanoVg() {
    }

    public static boolean begin(int guiWidth, int guiHeight) {
        if (!ensure()) return false;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        int fbo = resolveFramebuffer(target);
        if (fbo < 0) return false;

        float dpr = (float) target.width / (float) guiWidth;

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, target.width, target.height);
        GL33C.glBindSampler(0, 0);

        // NanoVG coordinates must equal GUI coordinates: the frame size is the GUI
        // size and the device pixel ratio does the scaling to real pixels.
        nvgBeginFrame(vg, guiWidth, guiHeight, dpr);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        return font != -1;
    }

    public static void end() {
        if (vg == 0) return;
        nvgEndFrame(vg);
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ZERO);
    }

    public static void roundRect(float x, float y, float w, float h, float r, int color) {
        setColor(COLOR_A, color);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);
    }

    public static void roundGradient(float x, float y, float w, float h, float r, int top, int bottom) {
        setColor(COLOR_A, top);
        setColor(COLOR_B, bottom);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgLinearGradient(vg, x, y, x, y + h, COLOR_A, COLOR_B, PAINT);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    public static void strokeRoundGradient(float x, float y, float w, float h, float r, float width, int from, int to) {
        setColor(COLOR_A, from);
        setColor(COLOR_B, to);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgStrokeWidth(vg, width);
        nvgLinearGradient(vg, x, y, x + w, y + h, COLOR_A, COLOR_B, PAINT);
        nvgStrokePaint(vg, PAINT);
        nvgStroke(vg);
    }

    public static void rect(float x, float y, float w, float h, int color) {
        setColor(COLOR_A, color);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, w, h);
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);
    }

    public static void circle(float cx, float cy, float r, int color) {
        setColor(COLOR_A, color);
        nvgBeginPath(vg);
        nvgCircle(vg, cx, cy, r);
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);
    }

    public static void line(float x1, float y1, float x2, float y2, float width, int color) {
        setColor(COLOR_A, color);
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgStrokeWidth(vg, width);
        nvgStrokeColor(vg, COLOR_A);
        nvgStroke(vg);
    }

    /** Chicken emblem drawn from gradients and circles. */
    public static void logo(float cx, float cy, float size, float alpha) {
        nvgSave(vg);
        nvgTranslate(vg, cx, cy);
        nvgScale(vg, size, size);
        nvgGlobalAlpha(vg, alpha);

        roundGradient(-0.5f, -0.5f, 1.0f, 1.0f, 0.3f, argb(255, 255, 196, 0), argb(255, 255, 138, 0));
        circle(0.0f, 0.06f, 0.34f, argb(30, 255, 255, 255));
        circle(-0.16f, -0.02f, 0.055f, argb(255, 24, 20, 20));
        circle(-0.1f, -0.3f, 0.075f, argb(255, 255, 90, 90));
        circle(0.03f, -0.34f, 0.085f, argb(255, 255, 90, 90));
        circle(0.16f, -0.29f, 0.07f, argb(255, 255, 90, 90));

        nvgBeginPath(vg);
        nvgMoveTo(vg, 0.24f, 0.05f);
        nvgLineTo(vg, 0.44f, 0.12f);
        nvgLineTo(vg, 0.23f, 0.19f);
        setColor(COLOR_A, argb(255, 255, 150, 30));
        nvgFillColor(vg, COLOR_A);
        nvgFill(vg);

        nvgRestore(vg);
    }

    public static void text(String text, float x, float y, float size, int color) {
        if (font == -1 || text == null || text.isEmpty()) return;
        setColor(COLOR_A, color);
        nvgFontSize(vg, size);
        nvgFontFaceId(vg, font);
        nvgFillColor(vg, COLOR_A);
        nvgText(vg, x, y, text);
    }

    public static float textWidth(String text, float size) {
        if (font == -1 || text == null || text.isEmpty()) return 0.0f;
        nvgFontSize(vg, size);
        nvgFontFaceId(vg, font);
        return nvgTextBounds(vg, 0.0f, 0.0f, text, BOUNDS);
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    public static int alpha(int color, int a) {
        return (color & 0xFFFFFF) | ((a & 255) << 24);
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return argb(a, r, g, b);
    }

    public static float easeOut(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static void setColor(NVGColor target, int argb) {
        nvgRGBA((byte) ((argb >> 16) & 255), (byte) ((argb >> 8) & 255),
            (byte) (argb & 255), (byte) ((argb >>> 24) & 255), target);
    }

    private static int resolveFramebuffer(RenderTarget target) {
        GpuTexture colorTexture = target.getColorTexture();
        GpuTexture depthTexture = target.getDepthTexture();
        if (!(colorTexture instanceof GlTexture color) || !(depthTexture instanceof GlTexture depth)) return -1;
        if (!(RenderSystem.getDevice() instanceof GlDevice device)) return -1;
        DirectStateAccess stateAccess = device.directStateAccess();
        return color.getFbo(stateAccess, depth);
    }

    private static boolean ensure() {
        if (failed) return false;
        if (vg != 0) return true;
        try {
            vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (vg == 0) {
                failed = true;
                return false;
            }
            fontData = loadFont();
            if (fontData != null) font = nvgCreateFontMem(vg, "chicken", fontData, false);
            return true;
        } catch (Throwable throwable) {
            failed = true;
            return false;
        }
    }

    private static ByteBuffer loadFont() {
        try (InputStream input = NanoVg.class.getResourceAsStream("/assets/template/inter.ttf")) {
            if (input == null) return null;
            byte[] bytes = input.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (Throwable throwable) {
            return null;
        }
    }
}
