package com.example.addon.hud;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;

/**
 * Routes key presses into the standalone HUD.
 *
 * <p>Meteor's {@link KeyEvent} is posted from the keyboard callback, so it also
 * sees keys injected by mobile launchers that never update GLFW's polled key
 * state. Polling {@code glfwGetKey} alone is why the HUD menu would not open on
 * a phone.</p>
 */
public final class HudKeyListener {
    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press) return;
        NanoHudRenderer.onKeyPressed(event.key());
    }
}
