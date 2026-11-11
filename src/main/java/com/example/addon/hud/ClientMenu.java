package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import net.minecraft.client.Minecraft;

/**
 * Opens the Chicken Client menus.
 *
 * <p>Both menus belong to this addon: the HUD settings screen and the module
 * GUI. Neither uses Meteor's GUI theme, widgets or screens, so the client has
 * its own interface instead of Meteor's right-shift GUI.</p>
 *
 * <p>On phones there is no right shift key, so both screens are also reachable
 * by tapping the chips on the HUD.</p>
 */
public final class ClientMenu {
    private ClientMenu() {
    }

    /** The standalone HUD settings screen. */
    public static void openHudScreen(Minecraft mc) {
        if (mc.screen instanceof NanoHudScreen) return;

        mc.execute(() -> {
            try {
                mc.setScreen(new NanoHudScreen());
            } catch (Throwable throwable) {
                AddonTemplate.LOG.warn("Could not open the Chicken HUD menu", throwable);
            }
        });
    }

    /** The Chicken Client module GUI. */
    public static void openModulesGui(Minecraft mc) {
        if (mc.screen instanceof ChickenGuiScreen) return;

        mc.execute(() -> {
            try {
                mc.setScreen(new ChickenGuiScreen());
            } catch (Throwable throwable) {
                AddonTemplate.LOG.warn("Could not open the Chicken module GUI", throwable);
            }
        });
    }
}
