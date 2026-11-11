package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import net.minecraft.client.Minecraft;

/**
 * Opens the two Chicken Client menus.
 *
 * <p>On phones (PojavLauncher and friends) there is no right shift key, so the
 * Meteor GUI could never be opened and the touch keyboard usually does not feed
 * GLFW's key state either. Both menus are therefore reachable by tapping the
 * chips on the HUD as well as by their keybinds.</p>
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

    /** Meteor's own module GUI (normally right shift). */
    public static void openMeteorGui(Minecraft mc) {
        mc.execute(() -> {
            try {
                if (mc.screen != null) mc.setScreen(null);
                Tabs.get().getFirst().openScreen(GuiThemes.get());
            } catch (Throwable throwable) {
                AddonTemplate.LOG.warn("Could not open the Meteor GUI", throwable);
            }
        });
    }
}
