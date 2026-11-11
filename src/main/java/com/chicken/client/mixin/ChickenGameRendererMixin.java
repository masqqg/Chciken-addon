package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import com.chicken.client.modules.FreeLook;
import com.chicken.client.modules.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ChickenGameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void chicken$freecamHands(CallbackInfo ci) {
        Freecam freecam = ChickenClient.module(Freecam.class);
        if (freecam != null && freecam.isEnabled() && !freecam.renderHands()) {
            ci.cancel();
            return;
        }

        FreeLook freeLook = ChickenClient.module(FreeLook.class);
        if (freeLook != null && freeLook.isEnabled() && freeLook.isThroughWalls()) ci.cancel();
    }
}
