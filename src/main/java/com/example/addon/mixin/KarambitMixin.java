package com.example.addon.mixin;

import com.example.addon.modules.Karambit;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class KarambitMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void karambit$renderFirstPerson(AbstractClientPlayer player, float tickDelta, float pitch,
                                            InteractionHand hand, float swingProgress, ItemStack stack,
                                            float equipProgress, PoseStack matrices,
                                            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND || player.isScoping()) return;

        Karambit karambit = Modules.get().get(Karambit.class);
        if (karambit == null || !karambit.isActive()) return;

        karambit.renderFirstPerson(player, tickDelta, swingProgress, equipProgress, matrices, queue);
        ci.cancel();
    }
}
