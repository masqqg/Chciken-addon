package com.example.addon.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-Minecraft settings screen for the standalone HUD.
 *
 * <p>No Meteor widget, setting or screen class is used here. The screen edits
 * {@link NanoHudConfig} directly and saves it to the HUD's own properties file.</p>
 */
public final class NanoHudScreen extends Screen {
    private static final int PANEL_WIDTH = 372;
    private static final int PANEL_HEIGHT = 266;

    private static final int YELLOW = 0xFFFFC400;
    private static final int YELLOW_SOFT = 0xFFFFE082;
    private static final int AMBER = 0xFFFF8A00;
    private static final int TEXT = 0xFFF5F7FA;
    private static final int MUTED = 0xFF9AA6B2;
    private static final int CARD = 0xF0141820;
    private static final int CARD_LIGHT = 0xFF1C222D;
    private static final int TRACK = 0xFF2A303A;

    private int slider;
    private boolean dirty;

    public NanoHudScreen() {
        super(Component.literal("Chicken Client HUD"));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        NanoHudConfig hud = NanoHudConfig.get();
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;

        context.fill(0, 0, width, height, 0xB8000000);

        // Yellow halo and card.
        context.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y + PANEL_HEIGHT + 2, 0x55FFC400);
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, CARD);
        context.fillGradient(x, y, x + PANEL_WIDTH, y + 3, YELLOW, AMBER);
        context.fill(x + 1, y + 3, x + PANEL_WIDTH - 1, y + 5, 0xFF3A2A00);

        context.drawString(font, "CHICKEN CLIENT", x + 18, y + 16, YELLOW, false);
        context.drawString(font, "HUD SETTINGS  //  STANDALONE NANO HUD", x + 18, y + 28, MUTED, false);
        context.drawString(font, "F8", x + PANEL_WIDTH - 30, y + 16, YELLOW_SOFT, false);

        int rowY = y + 52;
        toggle(context, "Watermark", hud.showWatermark, x + 18, rowY, mouseX, mouseY);
        toggle(context, "Player card", hud.showPlayer, x + 18, rowY + 25, mouseX, mouseY);
        toggle(context, "Stats & coordinates", hud.showStats, x + 18, rowY + 50, mouseX, mouseY);
        toggle(context, "Animations", hud.animations, x + 18, rowY + 75, mouseX, mouseY);

        slider(context, "Scale", hud.scale, 0.6, 2.5, x + 18, rowY + 108, 1, mouseX, mouseY, true);
        slider(context, "Background", hud.backgroundAlpha / 255.0, 0.2, 1.0, x + 18, rowY + 138, 2, mouseX, mouseY, false);

        context.drawString(font, "BRAND COLOR", x + 18, rowY + 168, MUTED, false);
        preset(context, "YELLOW", x + 18, rowY + 180, mouseX, mouseY, true);
        preset(context, "HONEY", x + 88, rowY + 180, mouseX, mouseY, false);
        preset(context, "EMBER", x + 158, rowY + 180, mouseX, mouseY, false);

        button(context, "RESET POSITION", x + 228, rowY + 180, 126, 20, mouseX, mouseY);
        button(context, "DONE", x + PANEL_WIDTH - 78, y + PANEL_HEIGHT - 28, 60, 18, mouseX, mouseY);

        context.drawString(font, "Drag the panel with RSHIFT in game.  [ ] scales it.  F7 hides it.",
            x + 18, y + PANEL_HEIGHT - 22, 0xFF7F8A96, false);
    }

    private void toggle(GuiGraphics context, String label, boolean value, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 260, 18);
        context.drawString(font, label, x, y + 3, hovered ? TEXT : 0xFFD5DCE5, false);

        int tx = x + 286;
        int ty = y + 2;
        context.fill(tx, ty, tx + 32, ty + 14, value ? 0xFFFFC400 : TRACK);
        context.fill(tx + (value ? 18 : 2), ty + 2, tx + (value ? 30 : 14), ty + 12, value ? 0xFF17130A : 0xFFD5DCE5);
    }

    private void slider(GuiGraphics context, String label, double value, double min, double max,
                        int x, int y, int id, int mouseX, int mouseY, boolean scale) {
        context.drawString(font, label, x, y, 0xFFD5DCE5, false);
        String shown = scale
            ? String.format(java.util.Locale.ROOT, "%.2fx", value)
            : Math.round(value * 100.0) + "%";
        context.drawString(font, shown, x + 286, y, YELLOW_SOFT, false);

        int tx = x + 110;
        int ty = y + 11;
        int tw = 184;
        int knob = tx + (int) Math.round((value - min) / (max - min) * tw);
        context.fill(tx, ty, tx + tw, ty + 3, TRACK);
        context.fill(tx, ty, Math.max(tx + 2, knob), ty + 3, YELLOW);
        context.fill(knob - 3, ty - 3, knob + 3, ty + 6, slider == id ? YELLOW_SOFT : YELLOW);
    }

    private void preset(GuiGraphics context, String label, int x, int y, int mouseX, int mouseY, boolean selected) {
        boolean hovered = inside(mouseX, mouseY, x, y, 64, 20);
        context.fill(x, y, x + 64, y + 20, selected ? 0xFFFFC400 : (hovered ? CARD_LIGHT : 0xFF202630));
        int color = selected ? 0xFF17130A : 0xFFD5DCE5;
        context.drawCenteredString(font, label, x + 32, y + 6, color);
    }

    private void button(GuiGraphics context, String label, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, hovered ? 0xFFFFC400 : 0xFF202630);
        context.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2 + 1, hovered ? 0xFF17130A : 0xFFD5DCE5);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        NanoHudConfig hud = NanoHudConfig.get();
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        int rowY = y + 52;

        if (inside(mouseX, mouseY, x + 286, rowY, 32, 18)) {
            hud.showWatermark = !hud.showWatermark;
            markDirty(hud);
            return true;
        }
        if (inside(mouseX, mouseY, x + 286, rowY + 25, 32, 18)) {
            hud.showPlayer = !hud.showPlayer;
            markDirty(hud);
            return true;
        }
        if (inside(mouseX, mouseY, x + 286, rowY + 50, 32, 18)) {
            hud.showStats = !hud.showStats;
            markDirty(hud);
            return true;
        }
        if (inside(mouseX, mouseY, x + 286, rowY + 75, 32, 18)) {
            hud.animations = !hud.animations;
            markDirty(hud);
            return true;
        }

        if (inside(mouseX, mouseY, x + 110, rowY + 116, 184, 18)) {
            slider = 1;
            updateScale(hud, mouseX, x + 110, 184);
            return true;
        }
        if (inside(mouseX, mouseY, x + 110, rowY + 146, 184, 18)) {
            slider = 2;
            updateAlpha(hud, mouseX, x + 110, 184);
            return true;
        }

        if (inside(mouseX, mouseY, x + 18, rowY + 180, 64, 20)) {
            setPreset(hud, 0xFFFFC400, 0xFFFF8A00, 0xFFFFE082);
            return true;
        }
        if (inside(mouseX, mouseY, x + 88, rowY + 180, 64, 20)) {
            setPreset(hud, 0xFFFFB300, 0xFFFF7A00, 0xFFFFE0A0);
            return true;
        }
        if (inside(mouseX, mouseY, x + 158, rowY + 180, 64, 20)) {
            setPreset(hud, 0xFFFF6A00, 0xFFD42A00, 0xFFFFC400);
            return true;
        }

        if (inside(mouseX, mouseY, x + 228, rowY + 180, 126, 20)) {
            hud.x = 10;
            hud.y = 10;
            markDirty(hud);
            return true;
        }

        if (inside(mouseX, mouseY, x + PANEL_WIDTH - 78, y + PANEL_HEIGHT - 28, 60, 18)) {
            onClose();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (slider == 1 || slider == 2) {
            NanoHudConfig hud = NanoHudConfig.get();
            int x = (width - PANEL_WIDTH) / 2;
            if (slider == 1) updateScale(hud, event.x(), x + 110, 184);
            else updateAlpha(hud, event.x(), x + 110, 184);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (slider != 0) {
            slider = 0;
            NanoHudConfig.get().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateScale(NanoHudConfig hud, double mouseX, int trackX, int trackWidth) {
        double t = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
        hud.scale = 0.6 + t * 1.9;
        markDirty(hud);
    }

    private void updateAlpha(NanoHudConfig hud, double mouseX, int trackX, int trackWidth) {
        double t = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
        hud.backgroundAlpha = (int) Math.round((0.2 + t * 0.8) * 255.0);
        markDirty(hud);
    }

    private void setPreset(NanoHudConfig hud, int primary, int secondary, int accent) {
        hud.primary = primary;
        hud.secondary = secondary;
        hud.accent = accent;
        markDirty(hud);
    }

    private void markDirty(NanoHudConfig hud) {
        dirty = true;
        hud.save();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        if (dirty) NanoHudConfig.get().save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
