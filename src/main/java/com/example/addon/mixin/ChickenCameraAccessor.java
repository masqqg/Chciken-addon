package com.example.addon.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface ChickenCameraAccessor {
    @Invoker("setPosition")
    void chicken$setPosition(Vec3 position);

    @Invoker("setRotation")
    void chicken$setRotation(float yaw, float pitch);

    @Accessor("detached")
    void chicken$setDetached(boolean detached);
}
