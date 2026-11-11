package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import com.chicken.client.modules.FreeLook;
import com.chicken.client.modules.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class ChickenCameraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void chicken$applyCustomCamera(Level level, Entity entity, boolean thirdPerson,
                                            boolean inverseView, float tickDelta, CallbackInfo ci) {
        ChickenCameraAccessor accessor = (ChickenCameraAccessor) (Object) this;

        Freecam freecam = ChickenClient.module(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            Vec3 position = freecam.getCameraPosition(tickDelta);
            accessor.chicken$setPosition(position);
            accessor.chicken$setRotation(freecam.getYaw(tickDelta), freecam.getPitch(tickDelta));
            accessor.chicken$setDetached(true);
            return;
        }

        FreeLook freeLook = ChickenClient.module(FreeLook.class);
        if (freeLook != null && freeLook.isEnabled()) {
            if (freeLook.isThroughWalls()) {
                accessor.chicken$setPosition(freeLook.getCameraPosition(tickDelta));
                accessor.chicken$setDetached(true);
            }
            accessor.chicken$setRotation(freeLook.getYaw(tickDelta), freeLook.getPitch(tickDelta));
        }
    }
}
