package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the end of the level render pass to the client. */
@Mixin(LevelRenderer.class)
public abstract class ChickenRenderMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"), require = 0)
    private void chicken$renderModules(CallbackInfo ci) {
        ChickenClient client = ChickenClient.get();
        if (client != null) client.onRender3D();
    }
}
