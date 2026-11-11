package com.example.addon.mixin;

import com.example.addon.modules.PlayerSkeleton;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Collapses the vanilla player model while the skeleton replaces it.
 *
 * <p>The avatar render state carries the real entity id, so we only shrink the
 * bodies the skeleton module is actually drawing. Nothing is cancelled and no
 * entity state is touched: a tiny scale simply hides the skin and armor under
 * the bones, and it restores the moment the module is turned off.</p>
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerModelHideMixin {
    @Inject(method = "scale", at = @At("TAIL"))
    private void chicken$hideAvatarForSkeleton(AvatarRenderState state, PoseStack matrices, CallbackInfo ci) {
        PlayerSkeleton skeleton = Modules.get().get(PlayerSkeleton.class);
        if (skeleton == null || !skeleton.isActive() || !skeleton.hidesVanillaModel()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && state.id == mc.player.getId();

        if ((isSelf && skeleton.showsSelf()) || (!isSelf && skeleton.showsOthers())) {
            matrices.scale(0.0001f, 0.0001f, 0.0001f);
        }
    }
}
