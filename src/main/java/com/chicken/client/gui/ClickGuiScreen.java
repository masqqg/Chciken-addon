package com.chicken.client.gui;

import com.chicken.client.ChickenClient;
import com.chicken.client.core.Category;
import com.chicken.client.core.Module;
import com.chicken.client.settings.BoolSetting;
import com.chicken.client.settings.DoubleSetting;
import com.chicken.client.settings.Setting;
import com.chicken.client.settings.SettingGroup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Hand drawn ClickGUI: no Meteor widgets, no vanilla buttons. Everything is
 * painted with GuiGraphics so the look matches the NanoVG HUD palette.
 */
public final class ClickGuiScreen extends Screen {
    private static final int WIDTH = 452;
    private static final int HEIGHT = 312;

    private static final int CARD = 0xF0141820;
    private static final int CARD_DARK = 0xFF0B0F16;
    private static final int ROW = 0xFF1C222D;
    private static final int ROW_HOVER = 0xFF232A34;
    private static final int ROW_SELECTED = 0xFF2A303A;
    private static final int YELLOW = 0xFFFFC400;
    private static final int TEXT = 0xFFF5F7FA;
    private static final int MUTED = 0xFF9AA6B2;
    private static final int GREEN = 0xFF6BE07A;

    private static final int LIST_X = 12;
    private static final int LIST_W = 196;
    private static final int PANEL_X = 220;
    private static final int PANEL_W = 220;
    private static final int BAR_OFFSET = 112;
    private static final int BAR_W = 96;

    private Category category = Category.RENDER;
    private Module selected;
    private Setting<?> dragging;
    private Module binding;

    public ClickGuiScreen() {
        super(Component.literal("Chicken Client"));
    }

    private int px() {
        return (width - WIDTH) / 2;
    }

    private int py() {
        return (height - HEIGHT) / 2;
    }

    private List<Setting<?>> visibleSettings(Module module) {
        List<Setting<?>> list = new ArrayList<>();
        if (module == null) return list;
        for (SettingGroup group : module.settingsGroups()) {
            for (Setting<?> setting : group.settings()) if (setting.isVisible()) list.add(setting);
        }
        return list;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = px();
        int y = py();

        g.fill(0, 0, width, height, 0x88000000);
        g.fill(x - 2, y - 2, x + WIDTH + 2, y + HEIGHT + 2, 0x55FFC400);
        g.fill(x, y, x + WIDTH, y + HEIGHT, CARD);
        g.fillGradient(x, y, x + WIDTH, y + 3, YELLOW, 0xFFFF8A00);

        g.drawString(font, "CHICKEN CLIENT", x + 16, y + 12, YELLOW, false);
        g.drawString(font, "STANDALONE  //  v" + ChickenClient.VERSION, x + 16, y + 25, MUTED, false);

        int tx = x + PANEL_X;
        for (Category c : Category.values()) {
            int tw = font.width(c.title()) + 14;
            boolean active = c == category;
            boolean hover = inside(mouseX, mouseY, tx, y + 9, tw, 18);
            g.fill(tx, y + 9, tx + tw, y + 27, active ? YELLOW : (hover ? ROW_HOVER : ROW));
            g.drawString(font, c.title(), tx + 7, y + 14, active ? 0xFF17130A : TEXT, false);
            tx += tw + 6;
        }

        int ry = y + 48;
        for (Module module : ChickenClient.modules().byCategory(category)) {
            boolean hover = inside(mouseX, mouseY, x + LIST_X, ry, LIST_W, 20);
            boolean sel = module == selected;
            g.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + 20, sel ? ROW_SELECTED : (hover ? ROW_HOVER : CARD_DARK));
            g.fill(x + LIST_X + 8, ry + 8, x + LIST_X + 12, ry + 12, module.isEnabled() ? GREEN : 0xFF5A6572);
            g.drawString(font, module.name(), x + LIST_X + 18, ry + 6, module.isEnabled() ? TEXT : 0xFFAEB8C2, false);
            String bind = ChickenClient.keyName(module.getBind());
            g.drawString(font, bind, x + LIST_X + LIST_W - font.width(bind) - 8, ry + 6, MUTED, false);
            ry += 22;
        }

        if (selected != null) renderSettings(g, x, y, mouseX, mouseY);

        g.drawString(font, "R-SHIFT / ESC close   -   click: toggle   -   right click: settings",
            x + 16, y + HEIGHT - 16, MUTED, false);
    }

    private void renderSettings(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int sx = x + PANEL_X;
        int sy = y + 48;

        g.fill(sx, sy, sx + PANEL_W, sy + HEIGHT - 78, CARD_DARK);
        g.fill(sx, sy, sx + PANEL_W, sy + 2, 0xFF3A2A00);

        g.drawString(font, selected.name(), sx + 10, sy + 10, YELLOW, false);
        String desc = selected.description();
        if (font.width(desc) > PANEL_W - 20) desc = font.plainSubstrByWidth(desc, PANEL_W - 20) + "...";
        g.drawString(font, desc, sx + 10, sy + 24, MUTED, false);

        int rowY = sy + 42;
        boolean bindHover = inside(mouseX, mouseY, sx + 6, rowY, PANEL_W - 12, 20);
        g.fill(sx + 6, rowY, sx + PANEL_W - 6, rowY + 20, bindHover ? ROW_HOVER : ROW);
        g.drawString(font, "keybind", sx + 14, rowY + 6, TEXT, false);
        String bindText = binding == selected ? "PRESS A KEY" : ChickenClient.keyName(selected.getBind());
        g.drawString(font, bindText, sx + PANEL_W - 14 - font.width(bindText), rowY + 6, binding == selected ? YELLOW : MUTED, false);

        rowY += 24;
        for (Setting<?> setting : visibleSettings(selected)) {
            boolean hover = inside(mouseX, mouseY, sx + 6, rowY, PANEL_W - 12, 20);
            g.fill(sx + 6, rowY, sx + PANEL_W - 6, rowY + 20, hover ? ROW_HOVER : CARD);
            g.drawString(font, setting.name(), sx + 14, rowY + 6, TEXT, false);

            if (setting instanceof BoolSetting bool) {
                g.drawString(font, bool.get() ? "ON" : "OFF", sx + PANEL_W - 14 - font.width(bool.get() ? "ON" : "OFF"),
                    rowY + 6, bool.get() ? GREEN : MUTED, false);
            } else if (setting instanceof DoubleSetting slider) {
                int bx = sx + BAR_OFFSET;
                int by = rowY + 7;
                g.fill(bx, by, bx + BAR_W, by + 6, 0xFF2A303A);
                g.fill(bx, by, bx + (int) (BAR_W * slider.fraction()), by + 6, YELLOW);
                String value = slider.valueText();
                g.drawString(font, value, sx + PANEL_W - 14 - font.width(value), rowY + 6, TEXT, false);
            } else {
                String value = setting.valueText();
                g.drawString(font, value, sx + PANEL_W - 14 - font.width(value), rowY + 6, MUTED, false);
            }

            rowY += 22;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = px();
        int y = py();
        double mx = event.x();
        double my = event.y();

        int tx = x + PANEL_X;
        for (Category c : Category.values()) {
            int tw = font.width(c.title()) + 14;
            if (inside(mx, my, tx, y + 9, tw, 18)) {
                category = c;
                selected = null;
                return true;
            }
            tx += tw + 6;
        }

        int ry = y + 48;
        for (Module module : ChickenClient.modules().byCategory(category)) {
            if (inside(mx, my, x + LIST_X, ry, LIST_W, 20)) {
                if (event.button() == 0) {
                    module.toggle();
                    ChickenClient.config().save();
                }
                selected = module;
                return true;
            }
            ry += 22;
        }

        if (selected == null) return super.mouseClicked(event, doubleClick);

        int sx = x + PANEL_X;
        int sy = y + 48;

        if (inside(mx, my, sx + 6, sy + 42, PANEL_W - 12, 20)) {
            binding = selected;
            return true;
        }

        int rowY = sy + 66;
        for (Setting<?> setting : visibleSettings(selected)) {
            if (inside(mx, my, sx + 6, rowY, PANEL_W - 12, 20)) {
                if (setting instanceof BoolSetting bool) {
                    bool.set(!bool.get());
                    ChickenClient.config().save();
                } else if (setting instanceof DoubleSetting slider) {
                    dragging = slider;
                    applyDrag(slider, mx, sx);
                }
                return true;
            }
            rowY += 22;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging instanceof DoubleSetting slider) {
            applyDrag(slider, event.x(), px());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ChickenClient.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void applyDrag(DoubleSetting slider, double mouseX, int x) {
        int barX = x + PANEL_X + BAR_OFFSET;
        slider.setFraction((mouseX - barX) / (double) BAR_W);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (binding != null) {
            selected.setBind(event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
            binding = null;
            ChickenClient.config().save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ChickenClient.config().save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
