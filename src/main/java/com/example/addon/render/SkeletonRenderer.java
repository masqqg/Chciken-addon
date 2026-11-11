package com.example.addon.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a detailed, articulated human skeleton around a player.
 *
 * <p>All geometry is built in a local frame (x right, y up, z forward) and
 * converted to world space once per vertex. Bones are tapered boxes, joints are
 * small cubes, the rib cage is built from curved rib pairs, and the skull has a
 * cranium, a brow, dark eye sockets, a nasal cavity, teeth and a jaw.</p>
 */
public final class SkeletonRenderer {
    private static final Color SOCKET = new Color(18, 15, 13, 245);

    private SkeletonRenderer() {
    }

    public static void render(Render3DEvent event, Player player, SkeletonPose pose,
                              double scale, double thickness, Color bone, Color accent,
                              boolean glow, boolean details) {
        new Painter(event, player, scale, thickness, bone, accent, glow, details).draw(pose);
    }

    private static final class Painter {
        private final Render3DEvent event;
        private final Vec3 origin;
        private final Vec3 right;
        private final Vec3 forward;
        private final Vec3 up;
        private final double scale;
        private final double thickness;
        private final Color bone;
        private final Color accent;
        private final boolean glow;
        private final boolean details;

        Painter(Render3DEvent event, Player player, double scale, double thickness,
                Color bone, Color accent, boolean glow, boolean details) {
            this.event = event;
            this.origin = new Vec3(player.getX(), player.getY(), player.getZ());
            double yaw = Math.toRadians(player.getYRot());
            this.right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
            this.forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            this.up = new Vec3(0.0, 1.0, 0.0);
            this.scale = scale;
            this.thickness = thickness;
            this.bone = bone;
            this.accent = accent;
            this.glow = glow;
            this.details = details;
        }

        void draw(SkeletonPose pose) {
            double lean = pose.bodyLean;
            double roll = pose.bodyRoll;
            double twist = pose.bodyTwist;

            Vec3 pelvis = local(0.0, 0.90 + pose.rootLift, 0.0);
            Vec3 torso = local(Math.sin(roll), Math.cos(lean) * Math.cos(roll), Math.sin(lean) * Math.cos(roll))
                .scale(0.55);
            Vec3 neck = pelvis.add(torso);
            Vec3 chest = pelvis.add(torso.scale(0.85));

            Vec3 twistRight = right.scale(Math.cos(twist)).add(forward.scale(Math.sin(twist)));
            Vec3 twistForward = forward.scale(Math.cos(twist)).subtract(right.scale(Math.sin(twist)));

            // Pelvis and hip sockets.
            cube(pelvis, twistRight, up, twistForward, 0.155, 0.075, 0.090);
            Vec3 hipL = pelvis.add(twistRight.scale(-0.105)).add(local(0.0, -0.045, 0.0));
            Vec3 hipR = pelvis.add(twistRight.scale(0.105)).add(local(0.0, -0.045, 0.0));
            cube(hipL, right, up, forward, 0.050, 0.050, 0.050);
            cube(hipR, right, up, forward, 0.050, 0.050, 0.050);

            // Legs.
            leg(hipL, -1, pose.legPitchL, pose.legOutL, pose.kneeL, pose.ankleL);
            leg(hipR, 1, pose.legPitchR, pose.legOutR, pose.kneeR, pose.ankleR);

            // Spine: seven vertebrae stepping up the torso.
            Vec3 top = chest;
            for (int i = 0; i < 7; i++) {
                double f = i / 6.0;
                Vec3 vertebra = pelvis.add(torso.scale(0.10 + f * 0.82))
                    .add(twistRight.scale(Math.sin(f * Math.PI * 1.6) * 0.012));
                cube(vertebra, twistRight, up, twistForward, 0.032, 0.016, 0.030);
                top = vertebra;
            }

            // Rib cage and sternum.
            Vec3 shoulderCenter = chest.add(twistRight.scale(0.0));
            ribcage(pelvis, torso, twistRight, twistForward, shoulderCenter);

            Vec3 shoulderL = shoulderCenter.subtract(twistRight.scale(0.195)).add(local(0.0, 0.04, 0.0));
            Vec3 shoulderR = shoulderCenter.add(twistRight.scale(0.195)).add(local(0.0, 0.04, 0.0));
            cube(shoulderL, right, up, forward, 0.050, 0.050, 0.050);
            cube(shoulderR, right, up, forward, 0.050, 0.050, 0.050);

            // Clavicles.
            bone(shoulderL, neck.subtract(twistRight.scale(0.055)), thickness * 0.9);
            bone(shoulderR, neck.add(twistRight.scale(0.055)), thickness * 0.9);

            // Arms.
            arm(shoulderL, -1, pose.armPitchL, pose.armOutL, pose.elbowL, shoulderL.add(local(0.0, 0.0, 0.0)));
            arm(shoulderR, 1, pose.armPitchR, pose.armOutR, pose.elbowR, shoulderR.add(local(0.0, 0.0, 0.0)));

            skull(neck, pose);
            if (top.lengthSqr() >= 0.0 && glow) {
                // Spine glow beads make the back read at night without hiding the bones.
                for (int i = 0; i < 4; i++) {
                    Vec3 bead = pelvis.add(torso.scale(0.2 + i * 0.18));
                    glowQuad(bead, 0.016);
                }
            }
        }

        private void leg(Vec3 hip, int side, double pitch, double out, double knee, double ankle) {
            Vec3 thigh = limbDir(pitch, out, side);
            Vec3 kneePos = hip.add(thigh.scale(0.38));

            bone(hip, kneePos, thickness * 1.35);
            cube(kneePos, right, up, forward, 0.048, 0.048, 0.048);
            if (details) {
                cube(kneePos.add(forward.scale(0.040)), right, up, forward, 0.026, 0.030, 0.014);
            }

            Vec3 shin = limbDir(pitch - knee, out * 0.6, side);
            Vec3 anklePos = kneePos.add(shin.scale(0.38));

            // Tibia and fibula run side by side.
            Vec3 lateral = right.scale(0.016);
            bone(kneePos.add(lateral), anklePos.add(lateral), thickness);
            bone(kneePos.subtract(lateral), anklePos.subtract(lateral), thickness * 0.72);

            cube(anklePos, right, up, forward, 0.048, 0.045, 0.048);

            Vec3 toe = anklePos.add(forward.scale(0.15)).add(local(0.0, -0.055, 0.0));
            Vec3 heel = anklePos.add(forward.scale(-0.055)).add(local(0.0, -0.055, 0.0));
            bone(anklePos, toe, thickness * 1.15);
            bone(anklePos, heel, thickness * 0.9);

            if (details) {
                for (int i = -1; i <= 1; i++) {
                    Vec3 base = toe.add(right.scale(i * 0.030));
                    bone(base, base.add(forward.scale(0.055)).add(local(0.0, -0.012, 0.0)), thickness * 0.42);
                }
            }
        }

        private void arm(Vec3 shoulder, int side, double pitch, double out, double elbow, Vec3 unused) {
            Vec3 upper = limbDir(pitch, out, side);
            Vec3 elbowPos = shoulder.add(upper.scale(0.30));

            bone(shoulder, elbowPos, thickness * 1.25);
            cube(elbowPos, right, up, forward, 0.042, 0.042, 0.042);

            Vec3 lower = limbDir(pitch + elbow, out * 0.7, side);
            Vec3 wrist = elbowPos.add(lower.scale(0.27));

            Vec3 lateral = right.scale(0.014);
            bone(elbowPos.add(lateral), wrist.add(lateral), thickness * 0.95);
            bone(elbowPos.subtract(lateral), wrist.subtract(lateral), thickness * 0.72);

            cube(wrist, right, up, forward, 0.038, 0.036, 0.038);

            // Palm and fingers follow the forearm direction.
            Vec3 handDir = lower.normalize();
            Vec3 palm = wrist.add(handDir.scale(0.055));
            cube(palm, right, up, forward, 0.050, 0.035, 0.042);

            if (details) {
                for (int i = -1; i <= 1; i++) {
                    Vec3 knuckle = palm.add(handDir.scale(0.045)).add(right.scale(i * 0.026));
                    Vec3 tip = knuckle.add(handDir.scale(0.055));
                    bone(palm, knuckle, thickness * 0.42);
                    bone(knuckle, tip, thickness * 0.34);
                }
                Vec3 thumbBase = palm.add(right.scale(side * 0.045)).add(forward.scale(0.02));
                bone(thumbBase, thumbBase.add(handDir.scale(0.05)).add(right.scale(side * 0.02)),
                    thickness * 0.40);
            }
        }

        private void ribcage(Vec3 pelvis, Vec3 torso, Vec3 twistRight, Vec3 twistForward, Vec3 chest) {
            Vec3 sternumTop = chest.add(twistForward.scale(0.105)).add(local(0.0, 0.0, 0.0));
            Vec3 sternumBottom = pelvis.add(torso.scale(0.60)).add(twistForward.scale(0.095));
            bone(sternumTop, sternumBottom, thickness * 0.9);

            int pairs = details ? 6 : 4;
            for (int i = 0; i < pairs; i++) {
                double f = i / (double) (pairs - 1);
                double y = 0.98 + f * 0.40;
                double width = 0.10 + Math.sin(f * Math.PI) * 0.10;

                Vec3 spinePoint = local(0.0, y, 0.0).add(pelvis.subtract(local(0.0, 0.90, 0.0)));
                spinePoint = pelvis.add(torso.scale(0.18 + f * 0.72));

                for (int side = -1; side <= 1; side += 2) {
                    Vec3 mid = spinePoint
                        .add(twistRight.scale(side * width * 0.70))
                        .add(twistForward.scale(0.045))
                        .add(local(0.0, 0.020, 0.0));
                    Vec3 front = sternumBottom
                        .add(twistRight.scale(side * width * 0.28))
                        .add(local(0.0, f * 0.34, 0.0));

                    bone(spinePoint, mid, thickness * 0.62);
                    bone(mid, front, thickness * 0.52);
                }
            }
        }

        private void skull(Vec3 neck, SkeletonPose pose) {
            double headYaw = pose.headYaw;
            double pitch = pose.headPitch;

            Vec3 hRight = right.scale(Math.cos(headYaw)).add(forward.scale(Math.sin(headYaw)));
            Vec3 hForwardFlat = forward.scale(Math.cos(headYaw)).subtract(right.scale(Math.sin(headYaw)));
            Vec3 hForward = hForwardFlat.scale(Math.cos(pitch)).add(up.scale(Math.sin(pitch)));
            Vec3 hUp = up.scale(Math.cos(pitch)).subtract(hForwardFlat.scale(Math.sin(pitch)));

            Vec3 center = neck.add(hUp.scale(0.185)).add(hForward.scale(0.015));

            // Neck vertebrae.
            bone(neck, center.subtract(hUp.scale(0.10)), thickness * 0.85);
            cube(neck.add(hUp.scale(0.04)), hRight, hUp, hForward, 0.045, 0.020, 0.040);

            // Cranium, face and jaw.
            cube(center.add(hUp.scale(0.050)), hRight, hUp, hForward, 0.100, 0.088, 0.108);
            cube(center.subtract(hUp.scale(0.040)).add(hForward.scale(0.010)),
                hRight, hUp, hForward, 0.083, 0.052, 0.100);
            cube(center.subtract(hUp.scale(0.108)).add(hForward.scale(0.020)),
                hRight, hUp, hForward, 0.074, 0.032, 0.086);

            if (!details) return;

            Vec3 front = center.add(hForward.scale(0.106));

            // Brow ridge.
            cube(center.add(hUp.scale(0.012)).add(hForward.scale(0.098)),
                hRight, hUp, hForward, 0.086, 0.010, 0.012);

            // Eye sockets.
            plane(front.add(hRight.scale(-0.045)).add(hUp.scale(0.028)), hRight, hUp,
                -0.030, -0.019, 0.030, 0.019, SOCKET);
            plane(front.add(hRight.scale(0.045)).add(hUp.scale(0.028)), hRight, hUp,
                -0.030, -0.019, 0.030, 0.019, SOCKET);

            // Nasal cavity.
            plane(front.add(hUp.scale(-0.022)), hRight, hUp, -0.014, -0.030, 0.014, 0.012, SOCKET);

            // Cheekbones.
            cube(center.add(hRight.scale(-0.072)).add(hUp.scale(-0.035)).add(hForward.scale(0.060)),
                hRight, hUp, hForward, 0.022, 0.020, 0.030);
            cube(center.add(hRight.scale(0.072)).add(hUp.scale(-0.035)).add(hForward.scale(0.060)),
                hRight, hUp, hForward, 0.022, 0.020, 0.030);

            // Teeth.
            for (int i = -2; i <= 2; i++) {
                cube(front.add(hRight.scale(i * 0.024)).add(hUp.scale(-0.070)).add(hForward.scale(-0.020)),
                    hRight, hUp, hForward, 0.010, 0.012, 0.010);
            }

            // Jaw sides.
            bone(center.add(hRight.scale(-0.070)).add(hUp.scale(-0.060)).add(hForward.scale(0.010)),
                center.add(hRight.scale(-0.055)).add(hUp.scale(-0.118)).add(hForward.scale(0.026)),
                thickness * 0.6);
            bone(center.add(hRight.scale(0.070)).add(hUp.scale(-0.060)).add(hForward.scale(0.010)),
                center.add(hRight.scale(0.055)).add(hUp.scale(-0.118)).add(hForward.scale(0.026)),
                thickness * 0.6);
        }

        /** A flat quad on the plane defined by two axes. */
        private void plane(Vec3 center, Vec3 axisA, Vec3 axisB,
                           double a0, double b0, double a1, double b1, Color color) {
            quad(center.add(axisA.scale(a0)).add(axisB.scale(b0)),
                center.add(axisA.scale(a1)).add(axisB.scale(b0)),
                center.add(axisA.scale(a1)).add(axisB.scale(b1)),
                center.add(axisA.scale(a0)).add(axisB.scale(b1)), color);
        }

        private void glowQuad(Vec3 center, double size) {
            Color c = withAlpha(accent, 40);
            quad(center.add(right.scale(-size)), center.add(right.scale(size)),
                center.add(right.scale(size)).add(up.scale(size * 2.0)),
                center.add(right.scale(-size)).add(up.scale(size * 2.0)), c);
        }

        private Vec3 limbDir(double pitch, double out, int side) {
            double x = Math.sin(out) * side;
            double y = -Math.cos(pitch) * Math.cos(out);
            double z = Math.sin(pitch) * Math.cos(out);
            return new Vec3(x, y, z).normalize();
        }

        private Vec3 local(double x, double y, double z) {
            return right.scale(x).add(up.scale(y)).add(forward.scale(z));
        }

        private Vec3 world(Vec3 local) {
            return origin.add(local.scale(scale));
        }

        private void bone(Vec3 a, Vec3 b, double width) {
            Vec3 dir = b.subtract(a);
            double length = dir.length();
            if (length < 1.0E-4) return;
            dir = dir.scale(1.0 / length);

            Vec3 side = dir.cross(up);
            if (side.lengthSqr() < 1.0E-3) side = dir.cross(forward);
            side = side.normalize();
            Vec3 top = side.cross(dir).normalize();

            Vec3 h1 = side.scale(width);
            Vec3 h2 = top.scale(width * 0.85);

            Vec3 a0 = a.subtract(h1).subtract(h2);
            Vec3 a1 = a.add(h1).subtract(h2);
            Vec3 a2 = a.add(h1).add(h2);
            Vec3 a3 = a.subtract(h1).add(h2);
            Vec3 b0 = b.subtract(h1).subtract(h2);
            Vec3 b1 = b.add(h1).subtract(h2);
            Vec3 b2 = b.add(h1).add(h2);
            Vec3 b3 = b.subtract(h1).add(h2);

            Color lit = bone;
            Color mid = shade(bone, 0.74f);
            Color dark = shade(bone, 0.55f);

            quad(a0, a1, b1, b0, mid);
            quad(a1, a2, b2, b1, lit);
            quad(a2, a3, b3, b2, mid);
            quad(a3, a0, b0, b3, mid);
            quad(a0, a3, a2, a1, dark);
            quad(b0, b1, b2, b3, dark);

            if (glow) {
                Color edge = withAlpha(accent, 55);
                line(a0, b0, edge);
                line(a1, b1, edge);
                line(a2, b2, edge);
                line(a3, b3, edge);
            }
        }

        private void cube(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
                          double hx, double hy, double hz) {
            Vec3 x = axisX.normalize().scale(hx);
            Vec3 y = axisY.normalize().scale(hy);
            Vec3 z = axisZ.normalize().scale(hz);

            Color lit = bone;
            Color mid = shade(bone, 0.76f);
            Color dark = shade(bone, 0.56f);

            quad(at(center, x, y, z, -1, -1, 1), at(center, x, y, z, 1, -1, 1),
                at(center, x, y, z, 1, 1, 1), at(center, x, y, z, -1, 1, 1), lit);
            quad(at(center, x, y, z, 1, -1, -1), at(center, x, y, z, -1, -1, -1),
                at(center, x, y, z, -1, 1, -1), at(center, x, y, z, 1, 1, -1), dark);
            quad(at(center, x, y, z, 1, -1, 1), at(center, x, y, z, 1, -1, -1),
                at(center, x, y, z, 1, 1, -1), at(center, x, y, z, 1, 1, 1), mid);
            quad(at(center, x, y, z, -1, -1, -1), at(center, x, y, z, -1, -1, 1),
                at(center, x, y, z, -1, 1, 1), at(center, x, y, z, -1, 1, -1), mid);
            quad(at(center, x, y, z, -1, 1, 1), at(center, x, y, z, 1, 1, 1),
                at(center, x, y, z, 1, 1, -1), at(center, x, y, z, -1, 1, -1), mid);
            quad(at(center, x, y, z, -1, -1, -1), at(center, x, y, z, 1, -1, -1),
                at(center, x, y, z, 1, -1, 1), at(center, x, y, z, -1, -1, 1), dark);
        }

        private static Vec3 at(Vec3 center, Vec3 x, Vec3 y, Vec3 z, int sx, int sy, int sz) {
            return center.add(x.scale(sx)).add(y.scale(sy)).add(z.scale(sz));
        }

        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Color color) {
            Vec3 wa = world(a);
            Vec3 wb = world(b);
            Vec3 wc = world(c);
            Vec3 wd = world(d);
            event.renderer.quad(wa.x, wa.y, wa.z, wb.x, wb.y, wb.z,
                wc.x, wc.y, wc.z, wd.x, wd.y, wd.z, color);
        }

        private void line(Vec3 a, Vec3 b, Color color) {
            Vec3 wa = world(a);
            Vec3 wb = world(b);
            event.renderer.line(wa.x, wa.y, wa.z, wb.x, wb.y, wb.z, color);
        }

        private static Color shade(Color color, float factor) {
            return new Color((int) (color.r * factor), (int) (color.g * factor), (int) (color.b * factor), color.a);
        }

        private static Color withAlpha(Color color, int alpha) {
            return new Color(color.r, color.g, color.b, Math.max(0, Math.min(255, alpha)));
        }
    }
}
