package com.chicken.client.modules;

import com.chicken.client.ChickenClient;
import com.chicken.client.core.Category;
import com.chicken.client.core.Module;
import com.chicken.client.settings.BoolSetting;
import com.chicken.client.settings.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Look around independently of the body, optionally through walls. */
public final class FreeLook extends Module {
    private final DoubleSetting sensitivity = settings().add(
        new DoubleSetting("sensitivity", "Mouse sensitivity for the free camera.", 1.0, 0.1, 5.0));
    private final BoolSetting throughWalls = settings().add(
        new BoolSetting("through-walls", "Push the camera forward through blocks so you can look behind walls.", true));
    private final DoubleSetting distance = settings().add(
        new DoubleSetting("distance", "How far the camera moves forward when through-walls is enabled.", 2.0, 0.5, 8.0));

    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;

    public FreeLook() {
        super(Category.MOVEMENT, "free-look", "Look around independently of your body; optionally see through walls.");
        distance.visible(throughWalls::get);
    }

    @Override
    public void onActivate() {
        Minecraft mc = ChickenClient.mc();
        if (mc.player == null) return;

        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        lastYaw = yaw;
        lastPitch = pitch;
    }

    public void turn(double deltaYaw, double deltaPitch) {
        lastYaw = yaw;
        lastPitch = pitch;

        yaw += deltaYaw * 0.15 / sensitivity.get();
        pitch += deltaPitch * 0.15 / sensitivity.get();
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
    }

    public Vec3 getCameraPosition(float tickDelta) {
        Minecraft mc = ChickenClient.mc();
        if (mc.player == null) return Vec3.ZERO;

        Vec3 eye = mc.player.getEyePosition(tickDelta);
        if (!throughWalls.get()) return eye;

        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        return eye.add(look.scale(distance.get()));
    }

    public float getYaw(float tickDelta) {
        return Mth.lerp(tickDelta, lastYaw, yaw);
    }

    public float getPitch(float tickDelta) {
        return Mth.lerp(tickDelta, lastPitch, pitch);
    }

    public boolean isThroughWalls() {
        return throughWalls.get();
    }
}
