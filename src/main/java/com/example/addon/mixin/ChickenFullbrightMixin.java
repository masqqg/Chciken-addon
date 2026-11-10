package com.example.addon.mixin;

import com.example.addon.modules.ChickenFullbright;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public abstract class ChickenFullbrightMixin {
    @Shadow
    @Final
    private GpuTexture texture;

    @Inject(method = "updateLightTexture", at = @At("HEAD"), cancellable = true)
    private void chicken$fullbright(float partialTicks, CallbackInfo ci) {
        ChickenFullbright fullbright = Modules.get().get(ChickenFullbright.class);
        if (fullbright == null || !fullbright.isActive()) return;

        RenderSystem.getDevice().createCommandEncoder()
            .clearColorTexture(texture, ARGB.color(255, 255, 255, 255));
        ci.cancel();
    }
}
