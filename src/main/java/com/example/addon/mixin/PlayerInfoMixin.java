package com.example.addon.mixin;

import com.example.addon.modules.NameProtect;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void chicken$protectTabName(CallbackInfoReturnable<Component> cir) {
        Component fake = NameProtect.replaceTabName((PlayerInfo) (Object) this, cir.getReturnValue());
        if (fake != null) cir.setReturnValue(fake);
    }
}
