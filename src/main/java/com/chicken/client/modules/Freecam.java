package com.chicken.client.modules;

import com.chicken.client.ChickenClient;
import com.chicken.client.core.Category;
import com.chicken.client.core.Module;
import com.chicken.client.settings.BoolSetting;
import com.chicken.client.settings.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Detached, collision-free camera that leaves the player behind. */
public final class Freecam extends Module {
    private final DoubleSetting speed = settings().add(
        new DoubleSetting("speed", "Camera movement speed.", 1.0, 0.1, 10.0));
    private final DoubleSetting sensitivity = settings().add(
        new DoubleSetting("sensitivity", "Mouse sensitivity while detached.", 1.0, 0.1, 5.0));
    private final BoolSetting showHands = settings().add(
        new BoolSetting("show-hands", "Render the first person hand while in freecam.", false));

    private final Vector3d pos = new Vector3d();
    private final Vector3d prevPos = new Vector3d();

    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;

    public Freecam() {
        super(Category.MOVEMENT, "freecam", "Detaches the camera and lets you fly through blocks.");
    }

    @Override
    public void onActivate() {
        Minecraft mc = ChickenClient.mc();
        if (mc.player == null) return;

        Vec3 eye = mc.player.getEyePosition();
        pos.set(eye.x, eye.y, eye.z);
        prevPos.set(pos);
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        lastYaw = yaw;
        lastPitch = pitch;
    }

    @Override
    public void onTick() {
        Minecraft mc = ChickenClient.mc();
        if (mc.player == null || mc.screen != null) return;

        double forward = 0.0;
        double strafe = 0.0;
        double vertical = 0.0;

        if (mc.options.keyUp.isDown()) forward += 1.0;
        if (mc.options.keyDown.isDown()) forward -= 1.0;
        if (mc.options.keyRight.isDown()) strafe += 1.0;
        if (mc.options.keyLeft.isDown()) strafe -= 1.0;
        if (mc.options.keyJump.isDown()) vertical += 1.0;
        if (mc.options.keyShift.isDown()) vertical -= 1.0;

        if (forward != 0.0 || strafe != 0.0) {
            double length = Math.sqrt(forward * forward + strafe * strafe);
            forward /= length;
            strafe /= length;
        }

        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double dx = (-sin * forward - cos * strafe) * speed.get() * 0.5;
        double dz = (cos * forward - sin * strafe) * speed.get() * 0.5;
        double dy = vertical * speed.get() * 0.5;

        if (mc.options.keySprint.isDown()) {
            dx *= 2.0;
            dy *= 2.0;
            dz *= 2.0;
        }

        prevPos.set(pos);
        pos.add(dx, dy, dz);
    }

    public void turn(double deltaYaw, double deltaPitch) {
        lastYaw = yaw;
        lastPitch = pitch;

        yaw += deltaYaw * 0.15 / sensitivity.get();
        pitch += deltaPitch * 0.15 / sensitivity.get();
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
    }

    public boolean renderHands() {
        return !isEnabled() || showHands.get();
    }

    public Vec3 getCameraPosition(float tickDelta) {
        return new Vec3(
            Mth.lerp(tickDelta, prevPos.x, pos.x),
            Mth.lerp(tickDelta, prevPos.y, pos.y),
            Mth.lerp(tickDelta, prevPos.z, pos.z)
        );
    }

    public float getYaw(float tickDelta) {
        return Mth.lerp(tickDelta, lastYaw, yaw);
    }

    public float getPitch(float tickDelta) {
        return Mth.lerp(tickDelta, lastPitch, pitch);
    }
}
