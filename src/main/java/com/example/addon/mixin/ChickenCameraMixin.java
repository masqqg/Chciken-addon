package com.example.addon.mixin;

import com.example.addon.modules.ChickenFreeLook;
import com.example.addon.modules.ChickenFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
        ChickenFreecam freecam = Modules.get().get(ChickenFreecam.class);
        ChickenCameraAccessor accessor = (ChickenCameraAccessor) (Object) this;

        if (freecam != null && freecam.isActive()) {
            Vec3 position = freecam.getCameraPosition(tickDelta);
            accessor.chicken$setPosition(position);
            accessor.chicken$setRotation(freecam.getYaw(tickDelta), freecam.getPitch(tickDelta));
            accessor.chicken$setDetached(true);
            return;
        }

        ChickenFreeLook freeLook = Modules.get().get(ChickenFreeLook.class);
        if (freeLook != null && freeLook.isActive()) {
            if (freeLook.isThroughWalls()) {
                accessor.chicken$setPosition(freeLook.getCameraPosition(tickDelta));
                accessor.chicken$setDetached(true);
            }
            accessor.chicken$setRotation(freeLook.getYaw(tickDelta), freeLook.getPitch(tickDelta));
        }
    }
}
