package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import com.chicken.client.modules.FreeLook;
import com.chicken.client.modules.Freecam;
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

        Freecam freecam = ChickenClient.module(Freecam.class);
        if (freecam != null && freecam.isEnabled()) {
            freecam.turn(yaw, pitch);
            ci.cancel();
            return;
        }

        FreeLook freeLook = ChickenClient.module(FreeLook.class);
        if (freeLook != null && freeLook.isEnabled()) {
            freeLook.turn(yaw, pitch);
            ci.cancel();
        }
    }
}
