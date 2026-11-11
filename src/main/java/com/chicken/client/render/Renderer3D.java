package com.chicken.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** World space box renderer on top of the vanilla RenderType pipeline. */
public final class Renderer3D {
    private static MultiBufferSource.BufferSource buffers;

    private Renderer3D() {
    }

    public static VertexConsumer begin() {
        buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        return buffers.getBuffer(RenderTypes.LINES);
    }

    public static void end() {
        if (buffers == null) return;
        buffers.endBatch(RenderTypes.LINES);
        buffers = null;
    }

    public static void box(PoseStack pose, VertexConsumer consumer, AABB box, int argb) {
        VoxelShape shape = Shapes.create(box);
        ShapeRenderer.renderShape(pose, consumer, shape, 0.0, 0.0, 0.0, argb, 1.0f);
    }
}
