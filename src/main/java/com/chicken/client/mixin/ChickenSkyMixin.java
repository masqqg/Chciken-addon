package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import com.chicken.client.modules.CosmicSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rewrites the sky render state so the cosmic palette is what actually gets drawn. */
@Mixin(SkyRenderer.class)
public class ChickenSkyMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void chicken$cosmicSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        CosmicSky module = ChickenClient.module(CosmicSky.class);
        if (module != null && module.isEnabled()) module.apply(state);
    }
}
