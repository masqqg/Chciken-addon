package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the Chicken Client module tick once per client tick. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void chicken$tick(CallbackInfo ci) {
        ChickenClient client = ChickenClient.get();
        if (client != null) client.onTick();
    }
}
