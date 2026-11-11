package com.example.addon.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Entry point for the standalone Chicken Client HUD.
 *
 * <p>The HUD is drawn exclusively through Minecraft's own {@link GuiGraphics}
 * pipeline. Earlier versions rendered NanoVG straight into the main
 * framebuffer with raw OpenGL calls; that bypassed the render system's state
 * tracking and left the framebuffer/viewport in an inconsistent state, which
 * turned the whole world black as soon as a level was loaded. Nothing here
 * touches GL state anymore, so the HUD can never break world rendering.</p>
 */
public final class NanoHudRenderer {
    private static boolean scaleDownHeld;
    private static boolean scaleUpHeld;

    private static boolean dragging;
    private static boolean leftHeld;
    private static double dragOffsetX;
    private static double dragOffsetY;

    private NanoHudRenderer() {
    }

    public static void render(GuiGraphics context) {
        try {
            renderSafely(context);
        } catch (Throwable ignored) {
            // A HUD failure must never take the frame down with it.
        }
    }

    private static void renderSafely(GuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();
        NanoHudConfig hud = NanoHudConfig.get();

        // The toggle and settings keys arrive through HudKeyListener, which
        // uses Minecraft's keyboard callback instead of polling GLFW state.
        // Mobile launchers inject keys only into that callback, so polling
        // here is why the menu never opened on a phone.

        if (!hud.enabled) return;
        if (mc.level == null || mc.player == null || mc.getWindow().isIconified()) return;
        if (mc.options.hideGui) return;

        double guiHeight = mc.getWindow().getGuiScaledHeight();
        double guiWidth = mc.getWindow().getGuiScaledWidth();
        if (guiWidth <= 0.0 || guiHeight <= 0.0) return;

        NanoHudFallback.renderLowHealthVignette(context, hud);

        boolean leftHand = hud.hudMode == NanoHudConfig.HudMode.LEFT_HAND;
        double panelX = leftHand ? 12.0 : hud.panelX();
        double panelY = leftHand
            ? guiHeight - NanoHudFallback.PANEL_HEIGHT * clampScale(hud, guiHeight) - 10.0
            : hud.panelY();
        double panelWidth = NanoHudFallback.PANEL_WIDTH * clampScale(hud, guiHeight);
        double panelHeight = NanoHudFallback.PANEL_HEIGHT * clampScale(hud, guiHeight);

        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
        boolean editor = keyDown(mc, hud.editorKey);
        boolean leftPressed = mc.mouseHandler.isLeftPressed();

        handleChips(mc, hud, editor, panelX, panelY, clampScale(hud, guiHeight), mouseX, mouseY, leftPressed);

        if (editor) {
            boolean down = keyDown(mc, GLFW.GLFW_KEY_LEFT_BRACKET);
            boolean up = keyDown(mc, GLFW.GLFW_KEY_RIGHT_BRACKET);
            if (down && !scaleDownHeld) {
                hud.scale = Math.max(0.6, hud.scale - 0.1);
                hud.save();
            }
            if (up && !scaleUpHeld) {
                hud.scale = Math.min(2.5, hud.scale + 0.1);
                hud.save();
            }
            scaleDownHeld = down;
            scaleUpHeld = up;
            if (mc.screen == null && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
        } else {
            scaleDownHeld = false;
            scaleUpHeld = false;
        }

        if (leftHand) {
            if (dragging) {
                hud.endDrag();
                dragging = false;
            }
        } else {
            handleDragging(hud, editor, leftPressed, mouseX, mouseY, panelX, panelY, panelWidth, panelHeight);
        }

        NanoHudFallback.render(context, hud);
    }

    /** Key presses routed in from Minecraft's keyboard callback. */
    public static void onKeyPressed(int key) {
        try {
            NanoHudConfig hud = NanoHudConfig.get();

            if (key == hud.toggleKey) {
                hud.toggle();
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (key == hud.settingsKey && mc.screen == null) ClientMenu.openHudScreen(mc);
        } catch (Throwable ignored) {
            // A key handler must never break input.
        }
    }

    /** Tap handling for the HUD/GUI chips, the touch route to both menus. */
    private static void handleChips(Minecraft mc, NanoHudConfig hud, boolean editor,
                                    double panelX, double panelY, double scale,
                                    double mouseX, double mouseY, boolean leftPressed) {
        boolean active = !editor && mc.screen == null && leftPressed;

        if (active && !leftHeld) {
            if (NanoHudFallback.hitHudChip(mouseX, mouseY, panelX, panelY, scale)) {
                ClientMenu.openHudScreen(mc);
            } else if (NanoHudFallback.hitGuiChip(mouseX, mouseY, panelX, panelY, scale)) {
                ClientMenu.openMeteorGui(mc);
            }
        }

        leftHeld = active;
    }

    private static double clampScale(NanoHudConfig hud, double guiHeight) {
        double scale = Math.max(0.35, Math.min(2.5, hud.scale));
        if (hud.hudMode == NanoHudConfig.HudMode.LEFT_HAND) {
            scale = Math.min(scale, (guiHeight - 24.0) / NanoHudFallback.PANEL_HEIGHT);
        }
        return scale;
    }

    private static void handleDragging(NanoHudConfig hud, boolean editor, boolean leftPressed,
                                       double mouseX, double mouseY,
                                       double panelX, double panelY,
                                       double panelWidth, double panelHeight) {
        if (!editor) {
            if (dragging) {
                hud.endDrag();
                dragging = false;
            }
            return;
        }

        if (leftPressed) {
            if (!dragging
                && mouseX >= panelX && mouseX <= panelX + panelWidth
                && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                dragging = true;
                dragOffsetX = mouseX - panelX;
                dragOffsetY = mouseY - panelY;
                hud.beginDrag(panelX, panelY);
            }

            if (dragging) hud.dragTo(mouseX - dragOffsetX, mouseY - dragOffsetY);
        } else if (dragging) {
            hud.endDrag();
            dragging = false;
        }
    }

    private static boolean keyDown(Minecraft mc, int key) {
        if (key <= 0) return false;
        return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }
}
