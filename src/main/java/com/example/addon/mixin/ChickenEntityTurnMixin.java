package com.example.addon.mixin;

import com.example.addon.modules.ChickenFreeLook;
import com.example.addon.modules.ChickenFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ChickenEntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void chicken$turnCamera(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;

        ChickenFreecam freecam = Modules.get().get(ChickenFreecam.class);
        if (freecam != null && freecam.isActive()) {
            freecam.turn(yaw, pitch);
            ci.cancel();
            return;
        }

        ChickenFreeLook freeLook = Modules.get().get(ChickenFreeLook.class);
        if (freeLook != null && freeLook.isActive()) {
            freeLook.turn(yaw, pitch);
            ci.cancel();
        }
    }
}
