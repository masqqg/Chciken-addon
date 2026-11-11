package com.chicken.client.gui;

import com.chicken.client.ChickenClient;
import com.chicken.client.core.Category;
import com.chicken.client.core.Module;
import com.chicken.client.settings.BoolSetting;
import com.chicken.client.settings.DoubleSetting;
import com.chicken.client.settings.Setting;
import com.chicken.client.settings.SettingGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Modern NanoVG menu. Everything is drawn with the bundled smooth font:
 * rounded glass panels, gradient accents, animated open, animated hover and
 * animated sliders. No Minecraft bitmap font is used.
 */
public final class ClickGuiScreen extends Screen {
    private static final int W = 620;
    private static final int H = 396;

    private static final int BG = 0xFF0B0E14;
    private static final int PANEL = 0xFF12161F;
    private static final int PANEL_2 = 0xFF171C27;
    private static final int ROW = 0xFF1B212D;
    private static final int ROW_HOVER = 0xFF232B3A;
    private static final int ROW_SELECTED = 0xFF283142;
    private static final int LINE = 0xFF2A3342;
    private static final int TEXT = 0xFFEFF3F8;
    private static final int MUTED = 0xFF8B96A8;
    private static final int YELLOW = 0xFFFFC400;
    private static final int AMBER = 0xFFFF8A00;
    private static final int GREEN = 0xFF63E07C;
    private static final int CYAN = 0xFF35E0FF;

    private static final int HEADER = 78;
    private static final int PAD = 18;
    private static final int LIST_W = 320;
    private static final int ROW_H = 32;
    private static final int BAR_W = 90;

    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.nanoTime();
    private final Map<String, Float> hover = new HashMap<>();

    private Category category = Category.RENDER;
    private Module selected;
    private Setting<?> dragging;
    private Module binding;
    private int scroll;

    public ClickGuiScreen() {
        super(Component.literal("Chicken Client"));
    }

    private int px() {
        return (width - W) / 2;
    }

    private int py() {
        return (height - H) / 2;
    }

    private List<Setting<?>> visibleSettings(Module module) {
        List<Setting<?>> list = new ArrayList<>();
        if (module == null) return list;
        for (SettingGroup group : module.settingsGroups()) {
            for (Setting<?> setting : group.settings()) if (setting.isVisible()) list.add(setting);
        }
        return list;
    }

    private float hoverOf(String key, boolean active, float dt) {
        float current = hover.getOrDefault(key, 0.0f);
        float target = active ? 1.0f : 0.0f;
        current += (target - current) * Math.min(1.0f, dt * 14.0f);
        hover.put(key, current);
        return current;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000.0f);
        lastFrame = now;

        if (!NanoVg.begin(width, height)) {
            fallback(g);
            return;
        }

        float open = NanoVg.easeOut((System.currentTimeMillis() - openedAt) / 240.0f);
        float alpha = 0.15f + 0.85f * open;
        int x = px();
        int y = py();

        NanoVg.rect(0, 0, width, height, NanoVg.alpha(0xFF05070B, (int) (210 * open)));

        NanoVg.roundRect(x - 1, y - 1, W + 2, H + 2, 20, NanoVg.alpha(AMBER, (int) (40 * alpha)));
        NanoVg.roundRect(x, y, W, H, 18, NanoVg.alpha(BG, (int) (245 * alpha)));
        NanoVg.strokeRoundGradient(x, y, W, H, 18, 1.2f,
            NanoVg.alpha(YELLOW, (int) (200 * alpha)), NanoVg.alpha(0xFF7A3CFF, (int) (120 * alpha)));

        drawHeader(x, y, mouseX, mouseY, alpha, dt, open);
        drawModules(x, y, mouseX, mouseY, alpha, dt);
        drawSettings(x, y, mouseX, mouseY, alpha, dt);

        NanoVg.text("R-SHIFT / ESC close     left click toggles     right click settings",
            x + PAD, y + H - 22, 11, NanoVg.alpha(MUTED, (int) (200 * alpha)));

        NanoVg.end();
    }

    private void drawHeader(int x, int y, int mouseX, int mouseY, float alpha, float dt, float open) {
        long time = System.currentTimeMillis();
        float pulse = 0.55f + 0.45f * (float) Math.sin(time / 620.0);

        NanoVg.roundRect(x + 1, y + 1, W - 2, HEADER, 18, NanoVg.alpha(PANEL, (int) (250 * alpha)));
        NanoVg.rect(x + 1, y + HEADER - 1, W - 2, 1, NanoVg.alpha(LINE, (int) (200 * alpha)));

        NanoVg.logo(x + PAD + 20, y + HEADER / 2f - 2, 34 * (0.9f + 0.1f * open), alpha);
        NanoVg.text("CHICKEN CLIENT", x + PAD + 50, y + 20, 22, NanoVg.alpha(TEXT, (int) (255 * alpha)));
        NanoVg.text("v" + ChickenClient.VERSION + "   //   standalone build", x + PAD + 50, y + 48, 12,
            NanoVg.alpha(MUTED, (int) (235 * alpha)));

        float tx = x + PAD + 50 + NanoVg.textWidth("CHICKEN CLIENT", 22) + 26;
        for (Category c : Category.values()) {
            String label = c.title().toUpperCase(java.util.Locale.ROOT);
            float tw = NanoVg.textWidth(label, 11) + 26;
            boolean active = c == category;
            float hoverT = hoverOf("tab:" + c.name(), inside(mouseX, mouseY, (int) tx, y + 26, (int) tw, 26), dt);

            int fill = active ? NanoVg.alpha(YELLOW, (int) (235 * alpha))
                : NanoVg.alpha(NanoVg.mix(ROW, ROW_HOVER, hoverT), (int) (220 * alpha));
            NanoVg.roundRect(tx, y + 26, tw, 26, 13, fill);
            int textColor = active ? 0xFF191104 : NanoVg.alpha(TEXT, (int) (235 * alpha));
            NanoVg.text(label, tx + 13, y + 33, 11, textColor);
            tx += tw + 8;
        }
    }

    private void drawModules(int x, int y, int mouseX, int mouseY, float alpha, float dt) {
        int listX = x + PAD;
        int listY = y + HEADER + PAD - 4;
        int listH = H - HEADER - PAD * 2;

        NanoVg.roundRect(listX, listY, LIST_W, listH, 14, NanoVg.alpha(PANEL_2, (int) (245 * alpha)));
        NanoVg.text(category.title().toUpperCase(java.util.Locale.ROOT) + " MODULES", listX + 14, listY + 12, 10,
            NanoVg.alpha(MUTED, (int) (200 * alpha)));
        NanoVg.line(listX + 14, listY + 28, listX + LIST_W - 14, listY + 28, 1, NanoVg.alpha(LINE, (int) (150 * alpha)));

        List<Module> modules = ChickenClient.modules().byCategory(category);
        int rowY = listY + 36 - scroll;

        for (Module module : modules) {
            boolean hovered = inside(mouseX, mouseY, listX + 8, rowY, LIST_W - 16, ROW_H);
            float hoverT = hoverOf("row:" + module.name(), hovered, dt);
            boolean selectedRow = module == selected;

            int base = selectedRow ? ROW_SELECTED : NanoVg.mix(ROW, ROW_HOVER, hoverT);
            NanoVg.roundRect(listX + 8, rowY, LIST_W - 16, ROW_H, 10, NanoVg.alpha(base, (int) (230 * alpha)));

            if (module.isEnabled()) {
                NanoVg.roundRect(listX + 8, rowY + 7, 3, ROW_H - 14, 2, NanoVg.alpha(YELLOW, (int) (240 * alpha)));
            }

            NanoVg.circle(listX + 26, rowY + ROW_H / 2f, 4.5f,
                module.isEnabled() ? NanoVg.alpha(GREEN, (int) (240 * alpha)) : NanoVg.alpha(0xFF556072, (int) (180 * alpha)));
            NanoVg.text(module.name(), listX + 40, rowY + 10, 14, NanoVg.alpha(module.isEnabled() ? TEXT : MUTED, (int) (245 * alpha)));

            String bind = ChickenClient.keyName(module.getBind());
            NanoVg.text(bind, listX + LIST_W - 20 - NanoVg.textWidth(bind, 10), rowY + 12, 10,
                NanoVg.alpha(module.getBind() > 0 ? YELLOW : MUTED, (int) (200 * alpha)));

            rowY += ROW_H + 4;
        }
    }

    private void drawSettings(int x, int y, int mouseX, int mouseY, float alpha, float dt) {
        int sx = x + PAD + LIST_W + 14;
        int sw = W - (LIST_W + PAD * 3 + 14);
        int sy = y + HEADER + PAD - 4;

        NanoVg.roundRect(sx, sy, sw, H - HEADER - PAD * 2, 14, NanoVg.alpha(PANEL_2, (int) (245 * alpha)));

        if (selected == null) {
            NanoVg.text("select a module", sx + sw / 2f - NanoVg.textWidth("select a module", 12) / 2f,
                sy + (H - HEADER) / 2f - 20, 12, NanoVg.alpha(MUTED, (int) (190 * alpha)));
            return;
        }

        NanoVg.text(selected.name(), sx + 14, sy + 12, 16, NanoVg.alpha(YELLOW, (int) (245 * alpha)));
        String desc = selected.description();
        while (NanoVg.textWidth(desc, 10) > sw - 28 && desc.length() > 4) desc = desc.substring(0, desc.length() - 2);
        NanoVg.text(desc, sx + 14, sy + 34, 10, NanoVg.alpha(MUTED, (int) (215 * alpha)));

        int rowY = sy + 56;
        boolean bindHover = inside(mouseX, mouseY, sx + 10, rowY, sw - 20, 26);
        float bindT = hoverOf("bind", bindHover, dt);
        NanoVg.roundRect(sx + 10, rowY, sw - 20, 26, 8, NanoVg.alpha(NanoVg.mix(ROW, ROW_HOVER, bindT), (int) (230 * alpha)));
        NanoVg.text("keybind", sx + 22, rowY + 8, 12, NanoVg.alpha(TEXT, (int) (235 * alpha)));
        String bindText = binding == selected ? "press a key" : ChickenClient.keyName(selected.getBind());
        NanoVg.text(bindText.toUpperCase(java.util.Locale.ROOT), sx + sw - 24 - NanoVg.textWidth(bindText.toUpperCase(java.util.Locale.ROOT), 11),
            rowY + 9, 11, NanoVg.alpha(binding == selected ? YELLOW : MUTED, (int) (235 * alpha)));

        rowY += 32;
        for (Setting<?> setting : visibleSettings(selected)) {
            boolean hovered = inside(mouseX, mouseY, sx + 10, rowY, sw - 20, 26);
            float t = hoverOf("set:" + setting.name(), hovered, dt);
            NanoVg.roundRect(sx + 10, rowY, sw - 20, 26, 8, NanoVg.alpha(NanoVg.mix(PANEL, ROW, t), (int) (225 * alpha)));
            NanoVg.text(setting.name(), sx + 22, rowY + 8, 12, NanoVg.alpha(TEXT, (int) (235 * alpha)));

            if (setting instanceof BoolSetting bool) {
                int pillW = 34;
                int pillX = sx + sw - 24 - pillW;
                NanoVg.roundRect(pillX, rowY + 6, pillW, 14, 7,
                    NanoVg.alpha(bool.get() ? GREEN : 0xFF39424F, (int) (235 * alpha)));
                NanoVg.circle(bool.get() ? pillX + pillW - 7 : pillX + 7, rowY + 13, 5,
                    NanoVg.alpha(0xFF0B0E14, (int) (245 * alpha)));
            } else if (setting instanceof DoubleSetting slider) {
                int bx = sx + sw - 24 - BAR_W;
                int by = rowY + 11;
                NanoVg.roundRect(bx, by, BAR_W, 5, 2.5f, NanoVg.alpha(0xFF2A3342, (int) (230 * alpha)));
                float fraction = (float) slider.fraction();
                NanoVg.roundRect(bx, by, BAR_W * fraction, 5, 2.5f, NanoVg.alpha(YELLOW, (int) (240 * alpha)));
                NanoVg.circle(bx + BAR_W * fraction, by + 2.5f, 4.5f, NanoVg.alpha(0xFFFFFFFF, (int) (245 * alpha)));
                String value = slider.valueText();
                NanoVg.text(value, bx - 8 - NanoVg.textWidth(value, 10), rowY + 8, 10, NanoVg.alpha(MUTED, (int) (220 * alpha)));
            } else {
                String value = setting.valueText();
                NanoVg.text(value, sx + sw - 24 - NanoVg.textWidth(value, 10), rowY + 8, 10, NanoVg.alpha(MUTED, (int) (220 * alpha)));
            }

            rowY += 32;
            if (rowY > sy + H - HEADER - PAD * 2 - 34) break;
        }
    }

    /** Plain fallback so the client is never unusable if NanoVG cannot start. */
    private void fallback(GuiGraphics g) {
        int x = px();
        int y = py();
        g.fill(x, y, x + W, y + H, 0xF00B0E14);
        g.drawString(font, "CHICKEN CLIENT", x + 18, y + 18, 0xFFFFC400, false);
        int rowY = y + 48;
        for (Module module : ChickenClient.modules().byCategory(category)) {
            g.drawString(font, (module.isEnabled() ? "[x] " : "[ ] ") + module.name(), x + 20, rowY, 0xFFFFFFFF, false);
            rowY += 18;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = px();
        int y = py();
        double mx = event.x();
        double my = event.y();

        float tx = x + PAD + 50 + NanoVg.textWidth("CHICKEN CLIENT", 22) + 26;
        for (Category c : Category.values()) {
            String label = c.title().toUpperCase(java.util.Locale.ROOT);
            float tw = NanoVg.textWidth(label, 11) + 26;
            if (inside(mx, my, (int) tx, y + 26, (int) tw, 26)) {
                category = c;
                selected = null;
                scroll = 0;
                return true;
            }
            tx += tw + 8;
        }

        int listX = x + PAD;
        int listY = y + HEADER + PAD - 4;
        int rowY = listY + 36 - scroll;
        for (Module module : ChickenClient.modules().byCategory(category)) {
            if (inside(mx, my, listX + 8, rowY, LIST_W - 16, ROW_H)) {
                if (event.button() == 0) {
                    module.toggle();
                    ChickenClient.config().save();
                }
                selected = module;
                return true;
            }
            rowY += ROW_H + 4;
        }

        if (selected != null) {
            int sx = x + PAD + LIST_W + 14;
            int sw = W - (LIST_W + PAD * 3 + 14);
            int sy = y + HEADER + PAD - 4;

            if (inside(mx, my, sx + 10, sy + 56, sw - 20, 26)) {
                binding = selected;
                return true;
            }

            int settingY = sy + 88;
            for (Setting<?> setting : visibleSettings(selected)) {
                if (inside(mx, my, sx + 10, settingY, sw - 20, 26)) {
                    if (setting instanceof BoolSetting bool) {
                        bool.set(!bool.get());
                        ChickenClient.config().save();
                    } else if (setting instanceof DoubleSetting slider) {
                        dragging = slider;
                        applyDrag(slider, mx, sx, sw);
                    }
                    return true;
                }
                settingY += 32;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging instanceof DoubleSetting slider) {
            applyDrag(slider, event.x(), px() + PAD + LIST_W + 14, W - (LIST_W + PAD * 3 + 14));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = ChickenClient.modules().byCategory(category).size();
        int max = Math.max(0, rows * (ROW_H + 4) - 240);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * 24));
        return true;
    }

    private void applyDrag(DoubleSetting slider, double mouseX, int sx, int sw) {
        int barX = sx + sw - 24 - BAR_W;
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
