package com.example.addon.mixin;

import com.example.addon.modules.NameProtect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void chicken$protectDisplayName(CallbackInfoReturnable<Component> cir) {
        Component fake = NameProtect.replaceDisplayName((Player) (Object) this, cir.getReturnValue());
        if (fake != null) cir.setReturnValue(fake);
    }
}
