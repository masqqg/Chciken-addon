package com.example.addon.mixin;

import com.example.addon.hud.NanoHudRenderer;
import com.example.addon.modules.ChickenHud;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, priority = 500)
public abstract class ChickenHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chicken$renderNanoHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        ChickenHud hud = Modules.get().get(ChickenHud.class);
        if (hud != null && hud.isActive()) NanoHudRenderer.render(hud);
    }
}
