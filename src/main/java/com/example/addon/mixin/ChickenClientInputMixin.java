package com.example.addon.mixin;

import com.example.addon.modules.ChickenFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
        ChickenFreecam freecam = Modules.get().get(ChickenFreecam.class);
        if (freecam == null || !freecam.isActive()) return;

        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
