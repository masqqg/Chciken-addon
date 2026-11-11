package com.example.addon.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Textured world-space geometry for the cosmetic modules.
 *
 * <p>Meteor's {@code Renderer3D} can only draw flat coloured triangles and
 * lines, which is why the earlier wings and karambit looked like coloured
 * wireframes. This renderer feeds real textured quads into the world's vertex
 * buffer with the game's own {@code entityTranslucent} pipeline, so the art can
 * carry baked shading, gradients, soft alpha edges and metallic highlights
 * exactly like a vanilla entity texture.</p>
 *
 * <p>Coordinates are world space; the event's matrices are already
 * camera-relative, the same space {@code Renderer3D} draws in.</p>
 */
public final class TexturedRenderer {
    public static final Identifier FEATHER = Identifier.fromNamespaceAndPath("chicken", "textures/wings/feather.png");
    public static final Identifier MEMBRANE = Identifier.fromNamespaceAndPath("chicken", "textures/wings/membrane.png");
    public static final Identifier GLOW = Identifier.fromNamespaceAndPath("chicken", "textures/common/glow.png");
    public static final Identifier BLADE = Identifier.fromNamespaceAndPath("chicken", "textures/karambit/blade.png");
    public static final Identifier GRIP = Identifier.fromNamespaceAndPath("chicken", "textures/karambit/grip.png");

    private MultiBufferSource.BufferSource buffers;
    private VertexConsumer consumer;
    private Matrix4f matrix;

    private TexturedRenderer() {
    }

    /**
     * Opens a textured batch. Returns {@code null} when the current render pass
     * cannot supply a buffer source, so callers simply skip drawing instead of
     * crashing.
     */
    public static TexturedRenderer begin(Render3DEvent event, Identifier texture, boolean emissive) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PoseStack.Pose pose = event.matrices.last();

            TexturedRenderer renderer = new TexturedRenderer();
            renderer.buffers = mc.renderBuffers().bufferSource();
            renderer.matrix = new Matrix4f(pose.pose());
            renderer.consumer = renderer.buffers.getBuffer(emissive
                ? RenderTypes.entityTranslucentEmissive(texture)
                : RenderTypes.entityTranslucent(texture));

            return renderer;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Full-bright, so cosmetics stay readable in caves and at night. */
    public void quad(double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     double x3, double y3, double z3,
                     double x4, double y4, double z4,
                     float u0, float v0, float u1, float v1,
                     Color color, boolean doubleSided) {
        VertexConsumer vertex = consumer;
        Matrix4f m = matrix;
        if (vertex == null || m == null) return;

        vertex(vertex, m, x1, y1, z1, u0, v0, color);
        vertex(vertex, m, x2, y2, z2, u0, v1, color);
        vertex(vertex, m, x3, y3, z3, u1, v1, color);
        vertex(vertex, m, x4, y4, z4, u1, v0, color);

        if (doubleSided) {
            vertex(vertex, m, x4, y4, z4, u1, v0, color);
            vertex(vertex, m, x3, y3, z3, u1, v1, color);
            vertex(vertex, m, x2, y2, z2, u0, v1, color);
            vertex(vertex, m, x1, y1, z1, u0, v0, color);
        }
    }

    /** Sub-rectangle of a texture: useful for atlases and glow sprites. */
    public void sprite(double x, double y, double z, double halfWidth, double halfHeight,
                       Color color) {
        quad(x - halfWidth, y - halfHeight, z,
            x - halfWidth, y + halfHeight, z,
            x + halfWidth, y + halfHeight, z,
            x + halfWidth, y - halfHeight, z,
            0.0f, 1.0f, 1.0f, 0.0f, color, true);
    }

    public void flush() {
        try {
            if (buffers != null) buffers.endBatch();
        } catch (Throwable ignored) {
        }
    }

    private static void vertex(VertexConsumer vertex, Matrix4f matrix,
                               double x, double y, double z,
                               float u, float v, Color color) {
        vertex.addVertex(matrix, (float) x, (float) y, (float) z)
            .setColor(color.r, color.g, color.b, color.a)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(0.0f, 1.0f, 0.0f);
    }
}
