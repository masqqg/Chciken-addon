package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Lets the camera look around independently of the body. With through-walls
 * enabled the camera is pushed along the view vector without collision, so it
 * can see behind a wall.
 */
public class ChickenFreeLook extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("sensitivity")
        .description("Mouse sensitivity for the free camera.")
        .defaultValue(1.0)
        .range(0.1, 5.0)
        .sliderRange(0.2, 3.0)
        .build());

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Push the camera forward through blocks so you can look behind walls.")
        .defaultValue(true)
        .build());

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far the camera moves forward when through-walls is enabled.")
        .defaultValue(2.0)
        .range(0.5, 8.0)
        .sliderRange(0.5, 5.0)
        .visible(throughWalls::get)
        .build());

    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;

    public ChickenFreeLook() {
        super(AddonTemplate.CATEGORY, "chicken-free-look", "Look around independently of your body; optionally see through walls.");
    }

    @Override
    public void onActivate() {
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
