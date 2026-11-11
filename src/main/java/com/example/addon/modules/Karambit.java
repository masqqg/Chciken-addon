package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person karambit replacement. The mesh is built from intentionally
 * simple quads so it works without a resource pack or a custom item model.
 *
 * <p>The blade has a curved spine with a hooked tip, a bright bevel, an
 * animated cutting edge, a spinning flourish, a swing trail and an impact
 * flash. The handle is a wrapped grip with a ring pommel and a gloved hand.</p>
 */
public class Karambit extends Module {
    private static final float[][] SPINE = {
        {0.000f, 0.000f, 0.090f},
        {0.018f, 0.115f, 0.104f},
        {0.062f, 0.228f, 0.114f},
        {0.142f, 0.330f, 0.120f},
        {0.252f, 0.418f, 0.114f},
        {0.382f, 0.480f, 0.100f},
        {0.505f, 0.514f, 0.080f},
        {0.598f, 0.503f, 0.052f},
        {0.655f, 0.458f, 0.022f},
        {0.674f, 0.398f, 0.005f}
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall size of the karambit.")
        .defaultValue(1.0d)
        .range(0.4d, 2.5d)
        .sliderRange(0.4d, 2.0d)
        .build()
    );

    private final Setting<Double> swingStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("swing-strength")
        .description("How far the blade travels during an attack.")
        .defaultValue(1.0d)
        .range(0.2d, 2.0d)
        .sliderRange(0.2d, 1.8d)
        .build()
    );

    private final Setting<Boolean> idleSway = sgGeneral.add(new BoolSetting.Builder()
        .name("idle-sway")
        .description("Gentle breathing and sway while standing still.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> spin = sgGeneral.add(new DoubleSetting.Builder()
        .name("spin-speed")
        .description("Continuous finger spin around the ring. 0 disables it.")
        .defaultValue(24.0d)
        .range(0.0d, 360.0d)
        .sliderRange(0.0d, 180.0d)
        .build()
    );

    private final Setting<Boolean> motionBlur = sgGeneral.add(new BoolSetting.Builder()
        .name("motion-blur")
        .description("Adds dark blade copies behind fast swings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ghostCount = sgGeneral.add(new IntSetting.Builder()
        .name("blur-copies")
        .description("Number of motion-blur copies.")
        .defaultValue(4)
        .range(0, 8)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Boolean> trail = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-trail")
        .description("Fading arc left behind a swing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> impactFlash = sgGeneral.add(new BoolSetting.Builder()
        .name("impact-flash")
        .description("Bright spark burst at the tip when a swing peaks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showHand = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hand")
        .description("Draws a dark gloved hand around the handle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glow = sgRender.add(new BoolSetting.Builder()
        .name("edge-glow")
        .description("Bright animated edge along the blade.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> posX = sgRender.add(new DoubleSetting.Builder()
        .name("offset-x")
        .description("Horizontal hand offset.")
        .defaultValue(0.0d)
        .range(-0.5d, 0.5d)
        .sliderRange(-0.3d, 0.3d)
        .build()
    );

    private final Setting<Double> posY = sgRender.add(new DoubleSetting.Builder()
        .name("offset-y")
        .description("Vertical hand offset.")
        .defaultValue(0.0d)
        .range(-0.5d, 0.5d)
        .sliderRange(-0.3d, 0.3d)
        .build()
    );

    private final Setting<Double> posZ = sgRender.add(new DoubleSetting.Builder()
        .name("offset-z")
        .description("Forward/back hand offset.")
        .defaultValue(0.0d)
        .range(-0.5d, 0.5d)
        .sliderRange(-0.3d, 0.3d)
        .build()
    );

    private final Setting<Double> rotX = sgRender.add(new DoubleSetting.Builder()
        .name("rotation-x")
        .description("Blade pitch.")
        .defaultValue(0.0d)
        .range(-90.0d, 90.0d)
        .sliderRange(-60.0d, 60.0d)
        .build()
    );

    private final Setting<Double> rotY = sgRender.add(new DoubleSetting.Builder()
        .name("rotation-y")
        .description("Blade yaw.")
        .defaultValue(0.0d)
        .range(-90.0d, 90.0d)
        .sliderRange(-60.0d, 60.0d)
        .build()
    );

    private final Setting<Double> rotZ = sgRender.add(new DoubleSetting.Builder()
        .name("rotation-z")
        .description("Blade roll.")
        .defaultValue(0.0d)
        .range(-90.0d, 90.0d)
        .sliderRange(-60.0d, 60.0d)
        .build()
    );

    private final Setting<SettingColor> bladeColor = sgColors.add(new ColorSetting.Builder()
        .name("blade-color")
        .description("Main blade colour.")
        .defaultValue(new SettingColor(198, 210, 226))
        .build()
    );

    private final Setting<SettingColor> edgeColor = sgColors.add(new ColorSetting.Builder()
        .name("edge-color")
        .description("Cutting edge and highlight colour.")
        .defaultValue(new SettingColor(235, 248, 255))
        .build()
    );

    private final Setting<SettingColor> handleColor = sgColors.add(new ColorSetting.Builder()
        .name("handle-color")
        .description("Handle colour.")
        .defaultValue(new SettingColor(24, 27, 33))
        .build()
    );

    private final Setting<SettingColor> ringColor = sgColors.add(new ColorSetting.Builder()
        .name("ring-color")
        .description("Metal ring colour.")
        .defaultValue(new SettingColor(174, 184, 198))
        .build()
    );

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Guard, trail and glow accent.")
        .defaultValue(new SettingColor(76, 210, 255))
        .build()
    );

    private final Setting<SettingColor> handColor = sgColors.add(new ColorSetting.Builder()
        .name("hand-color")
        .description("Glove colour.")
        .defaultValue(new SettingColor(31, 34, 41))
        .build()
    );

    public Karambit() {
        super(AddonTemplate.CATEGORY, "karambit", "Replaces the held item with an animated 3D karambit.");
    }

    /** Called by the ItemInHandRenderer mixin after vanilla decides to draw the main hand. */
    public void renderFirstPerson(AbstractClientPlayer player, float tickDelta, float swingProgress,
                                  float equipProgress, PoseStack matrices, SubmitNodeCollector queue) {
        float sign = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float swing = clamp01(swingProgress);
        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
        float arc = (float) Math.sin(swing * Math.PI);
        float size = 0.55f * scale.get().floatValue();

        matrices.pushPose();

        // Same anchor as vanilla's first-person item, then our own pose.
        matrices.translate(sign * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f);
        matrices.translate(sign * (-0.12f + posX.get().floatValue()), -0.08f + posY.get().floatValue(), 0.06f + posZ.get().floatValue());
        matrices.mulPose(Axis.YP.rotationDegrees(sign * -28.0f + rotY.get().floatValue()));
        matrices.mulPose(Axis.XP.rotationDegrees(12.0f + rotX.get().floatValue()));
        matrices.mulPose(Axis.ZP.rotationDegrees(sign * 17.0f + rotZ.get().floatValue()));

        if (idleSway.get()) {
            matrices.translate(0.0f, (float) Math.sin(time * 1.9f) * 0.012f, 0.0f);
            matrices.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(time * 1.4f) * 1.8f));
        }

        // Finger spin around the ring pommel.
        double spinSpeed = spin.get();
        if (spinSpeed > 0.001) {
            matrices.translate(0.0f, -0.60f, 0.0f);
            matrices.mulPose(Axis.ZP.rotationDegrees(sign * (float) (time * spinSpeed % 360.0)));
            matrices.translate(0.0f, 0.60f, 0.0f);
        }

        // Swing arc. The base pose is shared by the ghosts and the solid blade.
        if (arc > 0.001f) {
            float s = arc * swingStrength.get().floatValue();
            matrices.translate(sign * 0.12f * s, -0.05f * s, -0.16f * s);
            matrices.mulPose(Axis.ZP.rotationDegrees(sign * 62.0f * s));
            matrices.mulPose(Axis.YP.rotationDegrees(sign * -34.0f * s));
            matrices.mulPose(Axis.XP.rotationDegrees(-26.0f * s));
        }

        final float trailArc = arc;
        final float trailSign = sign;

        if (motionBlur.get() && arc > 0.01f && ghostCount.get() > 0) {
            for (int i = ghostCount.get(); i >= 1; i--) {
                float ghostSwing = clamp01(swing - i * 0.075f);
                float ghostArc = (float) Math.sin(ghostSwing * Math.PI);

                matrices.pushPose();
                matrices.translate(sign * 0.09f * ghostArc, -0.03f * ghostArc, -0.006f * i);
                matrices.mulPose(Axis.ZP.rotationDegrees(sign * 38.0f * ghostArc));
                matrices.mulPose(Axis.YP.rotationDegrees(sign * -22.0f * ghostArc));
                matrices.scale(size, size, size);

                float fade = 0.35f + 0.65f * (1.0f - (float) i / (ghostCount.get() + 1));
                int ghost = rgba(accentColor.get(), fade * 0.55f);
                float ghostTime = time + i * 0.03f;
                queue.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, vc) ->
                    buildBlade(vc, pose, ghost, ghost, false, ghostTime));
                matrices.popPose();
            }
        }

        if (trail.get() && trailArc > 0.05f) {
            matrices.pushPose();
            matrices.scale(size, size, size);
            queue.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, vc) ->
                buildTrail(vc, pose, trailArc, trailSign, time));
            matrices.popPose();
        }

        matrices.scale(size, size, size);
        final float modelArc = arc;
        queue.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, vc) ->
            buildModel(vc, pose, time, modelArc));

        matrices.popPose();
    }

    private void buildModel(VertexConsumer vc, PoseStack.Pose pose, float time, float arc) {
        Color blade = bladeColor.get();
        Color edge = edgeColor.get();
        Color handle = handleColor.get();
        Color ring = ringColor.get();
        Color accent = accentColor.get();
        Color glove = handColor.get();

        // Ring pommel with a small inner highlight ring.
        buildRing(vc, pose, 0.0f, -0.665f, 0.115f, 0.068f, 0.050f, ring, accent);
        buildRing(vc, pose, 0.0f, -0.665f, 0.072f, 0.058f, 0.052f, accent, ring);

        // Handle and wrapped grip ridges.
        box(vc, pose, -0.052f, -0.590f, -0.052f, 0.052f, 0.020f, 0.052f,
            rgba(handle, 1.0f), rgba(handle, 0.55f), rgba(handle, 0.7f));
        for (int i = 0; i < 7; i++) {
            float y = -0.545f + i * 0.078f;
            box(vc, pose, -0.057f, y, 0.050f, 0.057f, y + 0.020f, 0.060f,
                rgba(accent, 0.5f), rgba(handle, 0.34f), rgba(handle, 0.44f));
        }

        // Spine inlay on the back of the handle.
        box(vc, pose, -0.020f, -0.560f, -0.058f, 0.020f, -0.030f, -0.050f,
            rgba(accent, 0.75f), rgba(accent, 0.30f), rgba(accent, 0.45f));

        // Guard.
        box(vc, pose, -0.130f, -0.025f, -0.064f, 0.130f, 0.034f, 0.064f,
            rgba(accent, 0.95f), rgba(accent, 0.5f), rgba(accent, 0.65f));

        if (showHand.get()) {
            // Cuff, palm, fingers and thumb. Kept blocky on purpose; it reads as a glove.
            box(vc, pose, -0.105f, -0.575f, -0.090f, 0.095f, -0.500f, 0.090f,
                rgba(glove, 0.55f), rgba(glove, 0.32f), rgba(glove, 0.42f));
            box(vc, pose, -0.095f, -0.535f, -0.082f, 0.080f, -0.175f, 0.082f,
                rgba(glove, 1.0f), rgba(glove, 0.48f), rgba(glove, 0.62f));
            for (int i = 0; i < 3; i++) {
                box(vc, pose, 0.065f, -0.435f + i * 0.072f, -0.086f, 0.145f,
                    -0.390f + i * 0.072f, 0.086f, rgba(glove, 0.82f),
                    rgba(glove, 0.38f), rgba(glove, 0.52f));
                box(vc, pose, 0.058f, -0.452f + i * 0.072f, -0.090f, 0.150f,
                    -0.430f + i * 0.072f, 0.090f, rgba(accent, 0.42f),
                    rgba(glove, 0.30f), rgba(glove, 0.40f));
            }
            // Thumb wrapped over the guard.
            box(vc, pose, -0.055f, -0.390f, 0.075f, 0.045f, -0.300f, 0.128f,
                rgba(glove, 0.9f), rgba(glove, 0.42f), rgba(glove, 0.55f));
            box(vc, pose, 0.040f, -0.360f, 0.060f, 0.105f, -0.300f, 0.120f,
                rgba(glove, 0.78f), rgba(glove, 0.36f), rgba(glove, 0.48f));
        }

        buildBlade(vc, pose, rgba(blade, 1.0f), rgba(edge, 1.0f), true, time);

        // A small animated spark at the hooked tip.
        if (glow.get()) {
            float p = 0.6f + 0.4f * (float) Math.sin(time * 8.0f + arc * 5.0f);
            float s = 0.024f * p;
            int c = rgba(accent, 0.95f);
            quad(vc, pose, 0.674f - s, 0.398f, 0.012f, 0.674f, 0.398f - s, 0.012f,
                0.674f + s, 0.398f, 0.012f, 0.674f, 0.398f + s, 0.012f, c);
            quad(vc, pose, 0.674f - s, 0.398f, -0.012f, 0.674f, 0.398f - s, -0.012f,
                0.674f + s, 0.398f, -0.012f, 0.674f, 0.398f + s, -0.012f, c);
            quad(vc, pose,
                0.674f - s * 2.2f, 0.398f, 0.0f, 0.674f, 0.398f - s * 0.5f, 0.0f,
                0.674f + s * 2.2f, 0.398f, 0.0f, 0.674f, 0.398f + s * 0.5f, 0.0f,
                rgba(edge, 0.75f));
        }

        // Bright burst at the peak of a swing.
        if (impactFlash.get() && arc > 0.82f) {
            float p = (arc - 0.82f) / 0.18f;
            buildBurst(vc, pose, 0.68f, 0.40f, 0.06f * p, rgba(edge, p), rgba(accent, p * 0.7f));
        }
    }

    /** A fading arc left behind the blade during a swing. */
    private void buildTrail(VertexConsumer vc, PoseStack.Pose pose, float arc, float sign, float time) {
        int segments = 10;
        float extent = 0.75f * arc;
        float pivotX = 0.0f;
        float pivotY = -0.55f;

        float previousOuterX = 0.0f;
        float previousOuterY = 0.0f;
        float previousInnerX = 0.0f;
        float previousInnerY = 0.0f;

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = sign * (extent - 2.0f * extent * t);

            float[] outer = rotate(0.64f, 0.44f, pivotX, pivotY, angle);
            float[] inner = rotate(0.30f, 0.30f, pivotX, pivotY, angle);

            if (i > 0) {
                int alpha = (int) (110.0f * (1.0f - t) * (0.55f + 0.45f * (float) Math.sin(time * 2.0f + t)));
                int hot = rgba(edgeColor.get(), Math.max(0, alpha));
                int cool = rgba(accentColor.get(), Math.max(0, alpha / 3));
                quad(vc, pose,
                    previousOuterX, previousOuterY, 0.02f,
                    previousInnerX, previousInnerY, 0.02f,
                    inner[0], inner[1], 0.02f,
                    outer[0], outer[1], 0.02f,
                    cool, hot, hot, cool);
            }

            previousOuterX = outer[0];
            previousOuterY = outer[1];
            previousInnerX = inner[0];
            previousInnerY = inner[1];
        }
    }

    private static float[] rotate(float x, float y, float pivotX, float pivotY, float angle) {
        float dx = x - pivotX;
        float dy = y - pivotY;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new float[]{pivotX + dx * cos - dy * sin, pivotY + dx * sin + dy * cos};
    }

    private void buildBurst(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float radius,
                            int core, int halo) {
        int spikes = 6;

        for (int i = 0; i < spikes; i++) {
            float a = (float) (Math.PI * 2.0 * i / spikes);
            float nx = (float) Math.cos(a);
            float ny = (float) Math.sin(a);
            quad(vc, pose,
                cx - ny * radius * 0.35f, cy + nx * radius * 0.35f, 0.0f,
                cx + ny * radius * 0.35f, cy - nx * radius * 0.35f, 0.0f,
                cx + nx * radius, cy + ny * radius, 0.0f,
                cx + nx * radius * 0.2f, cy + ny * radius * 0.2f, 0.0f,
                halo, halo, core, core);
        }

        quad(vc, pose,
            cx - radius, cy, 0.0f,
            cx, cy - radius, 0.0f,
            cx + radius, cy, 0.0f,
            cx, cy + radius, 0.0f,
            core);
    }

    private void buildBlade(VertexConsumer vc, PoseStack.Pose pose, int frontColor, int edgeColor,
                            boolean highlight, float time) {
        float thickness = 0.034f;
        float half = thickness * 0.5f;

        for (int i = 0; i < SPINE.length - 1; i++) {
            float[] a = SPINE[i];
            float[] b = SPINE[i + 1];

            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.0001f) continue;

            float nx = -dy / len;
            float ny = dx / len;

            float aw = a[2] * 0.5f;
            float bw = b[2] * 0.5f;

            float alx = a[0] + nx * aw;
            float aly = a[1] + ny * aw;
            float arx = a[0] - nx * aw;
            float ary = a[1] - ny * aw;
            float blx = b[0] + nx * bw;
            float bly = b[1] + ny * bw;
            float brx = b[0] - nx * bw;
            float bry = b[1] - ny * bw;

            float zf = half;
            float zb = -half;
            int dark = rgba(bladeColor.get(), 0.50f);
            int mid = rgba(bladeColor.get(), 0.70f);

            // Front and back faces.
            quad(vc, pose, alx, aly, zf, arx, ary, zf, brx, bry, zf, blx, bly, zf, frontColor);
            quad(vc, pose, alx, aly, zb, blx, bly, zb, brx, bry, zb, arx, ary, zb, dark);

            // Cutting-edge, spine and back walls.
            quad(vc, pose, alx, aly, zf, blx, bly, zf, blx, bly, zb, alx, aly, zb, edgeColor);
            quad(vc, pose, arx, ary, zf, arx, ary, zb, brx, bry, zb, brx, bry, zf, mid);

            if (highlight) {
                float sheen = 0.78f + 0.22f * (float) Math.sin(time * 5.0f + i * 1.3f);
                int glint = rgba(this.edgeColor.get(), sheen);

                // Center ridge.
                quad(vc, pose,
                    a[0] - nx * 0.008f, a[1] - ny * 0.008f, zf + 0.004f,
                    a[0] + nx * 0.008f, a[1] + ny * 0.008f, zf + 0.004f,
                    b[0] + nx * 0.008f, b[1] + ny * 0.008f, zf + 0.004f,
                    b[0] - nx * 0.008f, b[1] - ny * 0.008f, zf + 0.004f, glint);

                // Bevel strip.
                quad(vc, pose,
                    alx, aly, zf + 0.006f, alx - nx * 0.016f, aly - ny * 0.016f, zf + 0.006f,
                    blx - nx * 0.016f, bly - ny * 0.016f, zf + 0.006f, blx, bly, zf + 0.006f,
                    glint);

                // Pulsing energy edge.
                float energy = 0.45f + 0.55f * (float) Math.sin(time * 4.2f - i * 0.8f);
                int pulse = rgba(accentColor.get(), Math.max(0.0f, energy) * 0.55f);
                quad(vc, pose,
                    alx - nx * 0.016f, aly - ny * 0.016f, zf + 0.007f,
                    alx - nx * 0.026f, aly - ny * 0.026f, zf + 0.007f,
                    blx - nx * 0.026f, bly - ny * 0.026f, zf + 0.007f,
                    blx - nx * 0.016f, bly - ny * 0.016f, zf + 0.007f, pulse);

                // Dark notch on the back of the heel for detail.
                if (i == 1) {
                    quad(vc, pose,
                        arx, ary, zf + 0.005f,
                        arx + nx * -0.012f, ary + ny * -0.012f, zf + 0.005f,
                        brx + nx * -0.012f, bry + ny * -0.012f, zf + 0.005f,
                        brx, bry, zf + 0.005f, rgba(handleColor.get(), 0.8f));
                }
            }
        }
    }

    private static void buildRing(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy,
                                  float outer, float inner, float thickness, Color metal, Color accent) {
        int segments = 18;
        float zf = thickness * 0.5f;
        float zb = -zf;

        for (int i = 0; i < segments; i++) {
            float a1 = (float) (i * Math.PI * 2.0 / segments);
            float a2 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            float ox1 = cx + c1 * outer;
            float oy1 = cy + s1 * outer;
            float ox2 = cx + c2 * outer;
            float oy2 = cy + s2 * outer;
            float ix1 = cx + c1 * inner;
            float iy1 = cy + s1 * inner;
            float ix2 = cx + c2 * inner;
            float iy2 = cy + s2 * inner;

            int face = rgba(metal, 0.95f);
            int side = rgba(metal, 0.62f);
            int shine = rgba(accent, 0.55f + 0.35f * (i % 2));

            quad(vc, pose, ox1, oy1, zf, ox2, oy2, zf, ix2, iy2, zf, ix1, iy1, zf, face);
            quad(vc, pose, ox1, oy1, zb, ix1, iy1, zb, ix2, iy2, zb, ox2, oy2, zb, side);
            quad(vc, pose, ox1, oy1, zf, ox1, oy1, zb, ox2, oy2, zb, ox2, oy2, zf, shine);
            quad(vc, pose, ix1, iy1, zf, ix2, iy2, zf, ix2, iy2, zb, ix1, iy1, zb, side);
        }
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            int front, int side, int back) {
        quad(vc, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, front);
        quad(vc, pose, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, back);
        quad(vc, pose, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, side);
        quad(vc, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, side);
        quad(vc, pose, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, front);
        quad(vc, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, back);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int color) {
        vertex(vc, pose, x1, y1, z1, color);
        vertex(vc, pose, x2, y2, z2, color);
        vertex(vc, pose, x3, y3, z3, color);
        vertex(vc, pose, x4, y4, z4, color);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int c1, int c2, int c3, int c4) {
        vertex(vc, pose, x1, y1, z1, c1);
        vertex(vc, pose, x2, y2, z2, c2);
        vertex(vc, pose, x3, y3, z3, c3);
        vertex(vc, pose, x4, y4, z4, c4);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int color) {
        vc.addVertex(pose, x, y, z).setColor(
            (color >> 16) & 0xFF,
            (color >> 8) & 0xFF,
            color & 0xFF,
            (color >>> 24) & 0xFF
        );
    }

    private static int rgba(Color color, float multiplier) {
        int r = clamp((int) (color.r * multiplier));
        int g = clamp((int) (color.g * multiplier));
        int b = clamp((int) (color.b * multiplier));
        int a = clamp(color.a);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
