package com.example.addon.mixin;

import com.example.addon.modules.ChickenFreeLook;
import com.example.addon.modules.ChickenFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ChickenGameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void chicken$freecamHands(CallbackInfo ci) {
        ChickenFreecam freecam = Modules.get().get(ChickenFreecam.class);
        if (freecam != null && freecam.isActive() && !freecam.renderHands()) {
            ci.cancel();
            return;
        }

        ChickenFreeLook freeLook = Modules.get().get(ChickenFreeLook.class);
        if (freeLook != null && freeLook.isActive() && freeLook.isThroughWalls()) ci.cancel();
    }
}
