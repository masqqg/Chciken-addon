package com.example.addon.render;

import net.minecraft.world.entity.player.Player;

/**
 * Joint angles for the player skeleton, all in radians.
 *
 * <p>Pitch 0 points a limb straight down, PI/2 straight forward and PI straight
 * up. Out spreads a limb sideways. The walk builder turns the vanilla walk
 * animation into a full body cycle with arm swing, hip roll, torso twist, head
 * aim and an idle breathing sway.</p>
 */
public final class SkeletonPose {
    public double rootLift;

    public double bodyLean;
    public double bodyRoll;
    public double bodyTwist;

    public double headYaw;
    public double headPitch;

    public double armPitchL;
    public double armPitchR;
    public double armOutL;
    public double armOutR;
    public double elbowL;
    public double elbowR;

    public double legPitchL;
    public double legPitchR;
    public double legOutL;
    public double legOutR;
    public double kneeL;
    public double kneeR;
    public double ankleL;
    public double ankleR;

    public static SkeletonPose walk(Player player, float tickDelta, double time, double sway) {
        SkeletonPose pose = new SkeletonPose();

        double phase = player.walkAnimation.position(tickDelta) * Math.PI;
        double speed = Math.min(1.0, Math.max(0.0, player.walkAnimation.speed(tickDelta) * 1.4));
        double swing = Math.sin(phase);
        double idle = 1.0 - speed;

        // Legs alternate, and the knee only bends while the leg travels back.
        pose.legPitchL = swing * 0.85 * speed;
        pose.legPitchR = -swing * 0.85 * speed;
        pose.kneeL = Math.max(0.0, -swing) * 1.20 * speed;
        pose.kneeR = Math.max(0.0, swing) * 1.20 * speed;
        pose.ankleL = -Math.max(0.0, swing) * 0.22 * speed;
        pose.ankleR = -Math.max(0.0, -swing) * 0.22 * speed;
        pose.legOutL = 0.03;
        pose.legOutR = 0.03;

        // Arms counter-swing against the legs.
        pose.armPitchL = -swing * 0.70 * speed;
        pose.armPitchR = swing * 0.70 * speed;
        pose.armOutL = 0.10 + Math.sin(time * 1.6) * 0.03 * idle;
        pose.armOutR = 0.10 + Math.sin(time * 1.6 + 0.7) * 0.03 * idle;
        pose.elbowL = 0.30 + Math.max(0.0, -swing) * 0.35 * speed;
        pose.elbowR = 0.30 + Math.max(0.0, swing) * 0.35 * speed;

        // Hip roll and shoulder twist are the "swaying" part of the walk.
        pose.bodyTwist = -swing * 0.16 * speed * sway;
        pose.bodyRoll = swing * 0.075 * speed * sway + Math.sin(time * 1.1) * 0.014 * idle;
        pose.bodyLean = 0.06 * speed + Math.sin(time * 1.3) * 0.010 * idle;
        pose.rootLift = Math.abs(Math.sin(phase)) * 0.045 * speed + Math.sin(time * 1.7) * 0.007 * idle;

        float headYaw = player.getYHeadRot() - player.getYRot();
        while (headYaw > 180.0f) headYaw -= 360.0f;
        while (headYaw < -180.0f) headYaw += 360.0f;
        pose.headYaw = Math.toRadians(Math.max(-70.0f, Math.min(70.0f, headYaw)));
        pose.headPitch = Math.toRadians(player.getXRot()) * 0.65;

        double attack = player.getAttackAnim(tickDelta);
        if (attack > 0.0) {
            double arc = Math.sin(attack * Math.PI);
            pose.armPitchR += arc * 1.70;
            pose.armOutR += arc * 0.35;
            pose.elbowR += arc * 0.85;
            pose.bodyTwist -= arc * 0.18;
        }

        if (player.isCrouching()) {
            pose.bodyLean += 0.35;
            pose.legPitchL += 0.22;
            pose.legPitchR += 0.22;
            pose.kneeL += 0.50;
            pose.kneeR += 0.50;
            pose.rootLift -= 0.06;
        }

        if (player.isSwimming()) {
            pose.bodyLean += 1.15;
            pose.rootLift += 0.35;
            pose.legPitchL = -0.5;
            pose.legPitchR = -0.4;
            pose.kneeL = 0.25;
            pose.kneeR = 0.2;
            pose.armPitchL = 1.1;
            pose.armPitchR = 1.0;
        }

        return pose;
    }
}
