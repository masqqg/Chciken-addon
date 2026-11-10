package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * A camera that leaves the player behind. Movement has no collision and the
 * player is frozen while the camera is detached, so you can inspect a base or
 * watch a mining job from outside.
 */
public class ChickenFreecam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Camera movement speed.")
        .defaultValue(1.0)
        .range(0.1, 10.0)
        .sliderRange(0.2, 4.0)
        .build());

    private final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("sensitivity")
        .description("Mouse sensitivity while the camera is detached.")
        .defaultValue(1.0)
        .range(0.1, 5.0)
        .sliderRange(0.2, 3.0)
        .build());

    private final Setting<Boolean> showHands = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hands")
        .description("Render the first-person hand while in freecam.")
        .defaultValue(false)
        .build());

    private final Vector3d pos = new Vector3d();
    private final Vector3d prevPos = new Vector3d();

    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;

    public ChickenFreecam() {
        super(AddonTemplate.CATEGORY, "chicken-freecam", "Detaches the camera and lets you fly through blocks.");
    }

    @Override
    public void onActivate() {
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
    public void onDeactivate() {
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
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

        // Minecraft: yaw 0 looks toward +Z, right is -X when looking south.
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
        return !isActive() || showHands.get();
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
