package com.chicken.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Chicken Client / MasQ signature to the vanilla title screen so it
 * does not depend on Mod Menu metadata or caching.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chicken$renderMasqSignature(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        String signature = "Chicken Client  \u2022  by MasQ";
        int x = 6;
        int y = mc.getWindow().getGuiScaledHeight() - 14;
        int width = mc.font.width(signature) + 10;

        context.fill(x - 3, y - 4, x + width, y + 10, 0x88000000);
        context.fill(x - 3, y - 4, x - 2, y + 10, 0xFFFFC400);
        context.fill(x - 3, y - 4, x + width, y - 3, 0x99FFC400);
        context.drawString(mc.font, signature, x, y, 0xFFFFC400, true);
    }
}
