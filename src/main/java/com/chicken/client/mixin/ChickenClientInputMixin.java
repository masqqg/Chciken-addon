package com.chicken.client.mixin;

import com.chicken.client.ChickenClient;
import com.chicken.client.modules.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class ChickenClientInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void chicken$freecamInput(CallbackInfo ci) {
        Freecam freecam = ChickenClient.module(Freecam.class);
        if (freecam == null || !freecam.isEnabled()) return;

        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
