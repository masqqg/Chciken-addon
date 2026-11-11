package com.example.addon.hud;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Chicken Client's own module GUI.
 *
 * <p>This is not Meteor's GUI: no Meteor theme, widgets or screens are used.
 * The window is drawn from scratch with vanilla {@link GuiGraphics} fills and
 * the bundled modern font, so it looks and behaves like part of this client.</p>
 *
 * <p>It lists every module grouped by category, toggles modules on tap, offers a
 * search field and edits the full module settings: booleans, integer and double
 * sliders, enums, colours, keybinds and text. Everything is touch sized, and a
 * pair of arrow buttons scrolls the lists where a mouse wheel is not available
 * (phones).</p>
 */
public final class ChickenGuiScreen extends Screen {
    private static final int HEADER = 44;
    private static final int ROW_HEIGHT = 38;
    private static final int ROW_GAP = 4;
    private static final int SETTING_ROW = 22;

    private static final int BACKDROP = 0xF2070A0F;
    private static final int CARD = 0xFF141A23;
    private static final int CARD_HOVER = 0xFF1B2431;
    private static final int CARD_ACTIVE = 0xFF1D2A1F;
    private static final int TRACK = 0xFF2A323D;
    private static final int TEXT = 0xFFF2F5F9;
    private static final int MUTED = 0xFF8B97A6;
    private static final int YELLOW = 0xFFFFC400;
    private static final int AMBER = 0xFFFF8A00;
    private static final int GREEN = 0xFF4ADE80;

    private static final int[][] COLOR_PRESETS = {
        {255, 255, 255}, {255, 196, 0}, {255, 138, 0}, {96, 214, 255},
        {255, 80, 96}, {74, 222, 128}, {176, 132, 255}, {255, 120, 200}, {20, 22, 28}
    };

    private final List<Category> categories = new ArrayList<>();

    private Category category;
    private String query = "";
    private boolean searchFocused;

    private int scroll;
    private int settingsScroll;

    private Module expanded;

    private Setting<?> dragSetting;
    private int dragTrackX;
    private int dragTrackWidth;

    private Setting<?> bindTarget;
    private boolean bindIsModuleKeybind;

    private Setting<String> textTarget;
    private final StringBuilder textBuffer = new StringBuilder();

    public ChickenGuiScreen() {
        super(Component.literal("Chicken Client"));
    }

    @Override
    protected void init() {
        categories.clear();
        for (Category loop : Modules.loopCategories()) categories.add(loop);
        categories.sort(Comparator.comparing(loop -> loop.name.toLowerCase(Locale.ROOT)));

        // The addon's own category always leads the sidebar.
        categories.removeIf(loop -> loop.name.equalsIgnoreCase("Chicken Client"));
        categories.add(0, com.example.addon.AddonTemplate.CATEGORY);

        scroll = 0;
        settingsScroll = 0;
    }

    // ---------------------------------------------------------------- layout

    private int sideX() {
        return 12;
    }

    private int sideW() {
        return Math.max(96, Math.min(132, width / 7));
    }

    private int listX() {
        return sideX() + sideW() + 8;
    }

    private int listW() {
        return Math.max(160, width - listX() - 14);
    }

    private int bodyTop() {
        return HEADER + 8;
    }

    private int bodyBottom() {
        return height - 16;
    }

    private int columns() {
        return listW() >= 520 ? 2 : 1;
    }

    private int columnWidth() {
        int gaps = (columns() - 1) * 8;
        return (listW() - gaps) / columns();
    }

    private int settingsX() {
        int panelWidth = settingsW();
        return Math.max(listX(), width - panelWidth - 14);
    }

    private int settingsW() {
        return Math.min(320, Math.max(220, width / 2));
    }

    private int settingsMaxScroll() {
        int content = 40;
        for (Object row : settingsRows()) content += row instanceof String ? 20 : SETTING_ROW;
        int visible = bodyBottom() - bodyTop() - 12;
        return Math.max(0, content - visible);
    }

    private int listMaxScroll() {
        List<Module> modules = modules();
        int step = ROW_HEIGHT + ROW_GAP;
        int rows = (modules.size() + columns() - 1) / Math.max(1, columns());
        int content = rows * step;
        int visible = bodyBottom() - bodyTop();
        return Math.max(0, content - visible);
    }

    private List<Module> modules() {
        List<Module> list = new ArrayList<>();
        if (category == null) list.addAll(Modules.get().getAll());
        else list.addAll(Modules.get().getGroup(category));

        if (!query.isEmpty()) {
            String needle = query.toLowerCase(Locale.ROOT);
            list.removeIf(module -> !module.title.toLowerCase(Locale.ROOT).contains(needle)
                && !module.name.toLowerCase(Locale.ROOT).contains(needle)
                && !module.description.toLowerCase(Locale.ROOT).contains(needle));
        }

        list.sort(Comparator.comparing(module -> module.title.toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Group headers are strings, settings are {@link Setting} objects. */
    private List<Object> settingsRows() {
        List<Object> rows = new ArrayList<>();
        if (expanded == null) return rows;

        for (SettingGroup group : expanded.settings.groups) {
            List<Setting<?>> visible = new ArrayList<>();
            for (Setting<?> setting : group) {
                if (setting.isVisible()) visible.add(setting);
            }
            if (visible.isEmpty()) continue;

            rows.add(group.name);
            rows.addAll(visible);
        }

        return rows;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKDROP);
        context.fillGradient(0, 0, width, 3, YELLOW, AMBER);

        drawHeader(context, mouseX, mouseY);
        drawSidebar(context, mouseX, mouseY);
        drawModules(context, mouseX, mouseY);
        if (expanded != null) drawSettings(context, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics context, int mouseX, int mouseY) {
        UiText.draw(context, "CHICKEN", 16, 12, YELLOW, true);
        int chicken = UiText.width("CHICKEN ");
        UiText.draw(context, "CLIENT", 16 + chicken, 12, TEXT, true);

        UiText.draw(context, "MODULE GUI", 16, 24, MUTED, false);

        int searchX = Math.max(180, width - 430);
        int searchW = Math.max(120, width - searchX - 56);
        boolean hovered = inside(mouseX, mouseY, searchX, 10, searchW, 20);
        context.fill(searchX, 10, searchX + searchW, 30, searchFocused ? 0xFF1C2430 : 0xFF151C26);
        context.fill(searchX, 30, searchX + searchW, 31, searchFocused ? YELLOW : 0xFF2A323D);
        UiText.draw(context, query.isEmpty() ? "search modules..." : query,
            searchX + 6, 16, query.isEmpty() ? 0xFF6F7B8A : TEXT, false);

        // Caret.
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = searchX + 8 + UiText.width(query);
            context.fill(caret, 15, caret + 1, 25, YELLOW);
        }

        boolean closeHovered = inside(mouseX, mouseY, width - 34, 10, 20, 20);
        context.fill(width - 34, 10, width - 14, 30, closeHovered ? 0xFFFF4350 : 0xFF20262F);
        UiText.drawCentered(context, "X", width - 24, 16, closeHovered ? 0xFF14070A : TEXT);

        UiText.draw(context, Modules.get().getActive().size() + " / " + Modules.get().getCount() + " ACTIVE",
            width - 150, 16, MUTED, false);

        context.fill(12, HEADER + 2, width - 12, HEADER + 3, 0xFF232B36);
    }

    private void drawSidebar(GuiGraphics context, int mouseX, int mouseY) {
        int x = sideX();
        int w = sideW();
        int y = bodyTop();

        rowButton(context, "ALL MODULES", x, y, w, 22, category == null, mouseX, mouseY);
        y += 26;

        for (Category loop : categories) {
            if (y + 22 > bodyBottom()) break;
            rowButton(context, loop.name, x, y, w, 20, loop.equals(category), mouseX, mouseY);
            y += 24;
        }
    }

    private void rowButton(GuiGraphics context, String label, int x, int y, int w, int h,
                           boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, selected ? 0xFF2B2410 : (hovered ? CARD_HOVER : 0xFF121821));
        if (selected) context.fill(x, y, x + 2, y + h, YELLOW);
        UiText.draw(context, trim(label, w - 14), x + 8, y + (h - 8) / 2 + 1,
            selected ? YELLOW : (hovered ? TEXT : MUTED), false);
    }

    private void drawModules(GuiGraphics context, int mouseX, int mouseY) {
        List<Module> modules = modules();
        int x = listX();
        int w = listW();
        int columns = columns();
        int columnWidth = columnWidth();
        int step = ROW_HEIGHT + ROW_GAP;

        if (modules.isEmpty()) {
            UiText.draw(context, "no modules match that search", x + 6, bodyTop() + 10, MUTED, false);
        }

        for (int i = 0; i < modules.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int rx = x + column * (columnWidth + 8);
            int ry = bodyTop() + row * step - scroll;
            if (ry + ROW_HEIGHT < bodyTop() || ry > bodyBottom()) continue;

            Module module = modules.get(i);
            drawModuleCard(context, module, rx, ry, columnWidth, mouseX, mouseY);
        }

        drawScrollArrows(context, x, w, listMaxScroll(), scroll, mouseX, mouseY);
    }

    private void drawModuleCard(GuiGraphics context, Module module, int x, int y, int w,
                                int mouseX, int mouseY) {
        boolean active = module.isActive();
        boolean hovered = inside(mouseX, mouseY, x, y, w, ROW_HEIGHT);

        context.fill(x, y, x + w, y + ROW_HEIGHT, active ? CARD_ACTIVE : (hovered ? CARD_HOVER : CARD));
        context.fill(x, y, x + 2, y + ROW_HEIGHT, active ? GREEN : (hovered ? YELLOW : 0xFF2A323D));

        UiText.draw(context, trim(module.title, w - 110), x + 10, y + 6, active ? TEXT : 0xFFD6DEE8, false);
        UiText.draw(context, trim(module.description, w - 110), x + 10, y + 19, MUTED, false);

        // Keybind hint.
        Keybind keybind = module.keybind;
        if (keybind != null && keybind.isSet()) {
            UiText.draw(context, keybind.toString(), x + w - 96, y + 6, 0xFF6F7B8A, false);
        }

        // On/off pill on the right.
        int pillW = 46;
        int pillX = x + w - pillW - 8;
        int pillY = y + 12;
        context.fill(pillX, pillY, pillX + pillW, pillY + 14, active ? 0xFF17301F : TRACK);
        context.fill(pillX + (active ? 34 : 2), pillY + 2, pillX + (active ? 44 : 12), pillY + 12,
            active ? GREEN : 0xFFD5DCE5);
        UiText.drawCentered(context, active ? "ON" : "OFF", pillX + (active ? 15 : 32), pillY + 3,
            active ? GREEN : MUTED);
    }

    private void drawSettings(GuiGraphics context, int mouseX, int mouseY) {
        int x = settingsX();
        int w = settingsW();
        int y = bodyTop();
        int bottom = bodyBottom();

        context.fill(x - 2, y - 2, x + w + 2, bottom + 2, 0x88000000);
        context.fill(x, y, x + w, bottom, 0xFF101620);
        context.fill(x, y, x + w, y + 2, YELLOW);

        UiText.draw(context, trim(expanded.title, w - 90), x + 10, y + 8, TEXT, false);
        UiText.draw(context, "SETTINGS", x + 10, y + 20, MUTED, false);

        int backW = 54;
        boolean backHovered = inside(mouseX, mouseY, x + w - backW - 8, y + 8, backW, 18);
        context.fill(x + w - backW - 8, y + 8, x + w - 8, y + 26, backHovered ? YELLOW : 0xFF20262F);
        UiText.drawCentered(context, "BACK", x + w - backW / 2 - 8, y + 13,
            backHovered ? 0xFF14070A : TEXT);

        // Bind the module keybind itself as the first row.
        int bindY = y + 32;
        context.fill(x, bindY, x + w, bindY + SETTING_ROW, 0xFF141A23);
        UiText.draw(context, "toggle key", x + 10, bindY + 7, 0xFFD6DEE8, false);
        boolean bindingModule = bindIsModuleKeybind;
        String keyLabel = bindingModule ? "press..." : (expanded.keybind.isSet() ? expanded.keybind.toString() : "none");
        int keyX = x + w - 96;
        context.fill(keyX, bindY + 4, x + w - 8, bindY + 18, bindingModule ? 0xFF3A2A00 : 0xFF20262F);
        UiText.drawCentered(context, trim(keyLabel, 82), keyX + 44, bindY + 7, bindingModule ? YELLOW : TEXT);

        int rowIndex = 0;
        int rowY = bindY + SETTING_ROW + 2 - settingsScroll;
        for (Object row : settingsRows()) {
            rowIndex++;
            if (row instanceof String header) {
                if (rowY >= y + 30 && rowY + 20 <= bottom) {
                    UiText.draw(context, header.toUpperCase(Locale.ROOT), x + 10, rowY + 5, YELLOW, false);
                    context.fill(x + 10, rowY + 16, x + w - 10, rowY + 17, 0xFF232B36);
                }
                rowY += 20;
                continue;
            }

            Setting<?> setting = (Setting<?>) row;
            if (rowY >= y + 30 && rowY + SETTING_ROW <= bottom) {
                drawSettingRow(context, setting, x, rowY, w, mouseX, mouseY);
            }
            rowY += SETTING_ROW;
        }

        drawScrollArrows(context, x, w, settingsMaxScroll(), settingsScroll, mouseX, mouseY);
        context.fill(x, bottom - 1, x + w, bottom, 0xFF232B36);
    }

    private void drawSettingRow(GuiGraphics context, Setting<?> setting, int x, int y, int w,
                                int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, SETTING_ROW);
        context.fill(x, y, x + w, y + SETTING_ROW, hovered ? 0xFF1A2230 : 0x00141A23);
        UiText.draw(context, trim(setting.title, 130), x + 10, y + 7, 0xFFD6DEE8, false);

        int right = x + w - 10;

        if (setting instanceof BoolSetting bool) {
            boolean value = bool.get();
            context.fill(right - 34, y + 5, right, y + 17, value ? 0xFF17301F : TRACK);
            context.fill(right - (value ? 16 : 30), y + 7, right - (value ? 26 : 4), y + 15,
                value ? GREEN : 0xFFD5DCE5);
            return;
        }

        if (setting instanceof DoubleSetting number) {
            double value = number.get();
            double min = Math.max(number.min, number.sliderMin);
            double max = Math.min(number.max, number.sliderMax);
            if (max <= min) max = min + 1.0;
            drawSlider(context, right - 150, y + 9, 100, min, max, value);
            UiText.draw(context, String.format(Locale.ROOT, "%." + number.decimalPlaces + "f", value),
                right - 44, y + 7, YELLOW, false);
            return;
        }

        if (setting instanceof IntSetting number) {
            int value = number.get();
            int min = Math.max(number.min, number.sliderMin);
            int max = Math.min(number.max, number.sliderMax);
            if (max <= min) max = min + 1;
            drawSlider(context, right - 150, y + 9, 100, min, max, value);
            UiText.draw(context, Integer.toString(value), right - 40, y + 7, YELLOW, false);
            return;
        }

        if (setting instanceof EnumSetting<?> enumSetting) {
            String label = String.valueOf(enumSetting.get());
            context.fill(right - 110, y + 4, right, y + 18, 0xFF20262F);
            UiText.drawCentered(context, trim(label, 104), right - 55, y + 7, TEXT);
            return;
        }

        if (setting instanceof ColorSetting color) {
            SettingColor value = color.get();
            context.fill(right - 30, y + 5, right, y + 17, argb(value.a, value.r, value.g, value.b));
            context.fill(right - 30, y + 5, right, y + 6, 0x66FFFFFF);
            return;
        }

        if (setting instanceof KeybindSetting keybindSetting) {
            boolean binding = bindTarget == setting;
            context.fill(right - 96, y + 4, right, y + 18, binding ? 0xFF3A2A00 : 0xFF20262F);
            String label = binding ? "press..." : keybindSetting.get().toString();
            UiText.drawCentered(context, trim(label, 88), right - 48, y + 7, binding ? YELLOW : TEXT);
            return;
        }

        if (setting instanceof StringSetting stringSetting) {
            boolean focused = textTarget == setting;
            context.fill(right - 150, y + 4, right, y + 18, focused ? 0xFF1C2430 : 0xFF20262F);
            UiText.draw(context, trim(stringSetting.get(), 142), right - 145, y + 7,
                focused ? YELLOW : TEXT, false);
            return;
        }

        UiText.draw(context, trim(String.valueOf(setting.get()), 110), right - 110, y + 7, MUTED, false);
    }

    private void drawSlider(GuiGraphics context, int trackX, int y, int trackWidth, double min, double max, double value) {
        context.fill(trackX, y, trackX + trackWidth, y + 3, TRACK);
        double t = Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
        int filled = trackX + (int) Math.round(t * trackWidth);
        context.fill(trackX, y, Math.max(trackX + 1, filled), y + 3, YELLOW);
        context.fill(filled - 2, y - 3, filled + 2, y + 6, 0xFFFFE082);
    }

    private void drawScrollArrows(GuiGraphics context, int x, int w, int maxScroll, int current,
                                  int mouseX, int mouseY) {
        if (maxScroll <= 0) return;

        int bx = x + w - 18;
        boolean upHovered = inside(mouseX, mouseY, bx, bodyBottom() - 40, 16, 16);
        boolean downHovered = inside(mouseX, mouseY, bx, bodyBottom() - 20, 16, 16);

        context.fill(bx, bodyBottom() - 40, bx + 16, bodyBottom() - 24, upHovered ? YELLOW : 0xCC101620);
        UiText.drawCentered(context, "^", bx + 8, bodyBottom() - 36, upHovered ? 0xFF14070A : TEXT);
        context.fill(bx, bodyBottom() - 20, bx + 16, bodyBottom() - 4, downHovered ? YELLOW : 0xCC101620);
        UiText.drawCentered(context, "v", bx + 8, bodyBottom() - 16, downHovered ? 0xFF14070A : TEXT);
    }

    // ----------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            // Close button.
            if (inside(mouseX, mouseY, width - 34, 10, 20, 20)) {
                onClose();
                return true;
            }

            // Search field.
            int searchX = Math.max(180, width - 430);
            int searchW = Math.max(120, width - searchX - 56);
            if (inside(mouseX, mouseY, searchX, 10, searchW, 20)) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;

            if (expanded != null) {
                if (handleSettingsClick(mouseX, mouseY, button)) return true;
            }

            if (handleSidebarClick(mouseX, mouseY)) return true;

            if (handleModuleClick(mouseX, mouseY)) return true;

            if (handleScrollClick(mouseX, mouseY)) return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleSidebarClick(double mouseX, double mouseY) {
        int x = sideX();
        int w = sideW();
        int y = bodyTop();

        if (inside(mouseX, mouseY, x, y, w, 22)) {
            category = null;
            scroll = 0;
            return true;
        }
        y += 26;

        for (Category loop : categories) {
            if (y + 20 > bodyBottom()) break;
            if (inside(mouseX, mouseY, x, y, w, 20)) {
                category = loop;
                scroll = 0;
                return true;
            }
            y += 24;
        }

        return false;
    }

    private boolean handleModuleClick(double mouseX, double mouseY) {
        List<Module> modules = modules();
        int x = listX();
        int columns = columns();
        int columnWidth = columnWidth();
        int step = ROW_HEIGHT + ROW_GAP;

        for (int i = 0; i < modules.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int rx = x + column * (columnWidth + 8);
            int ry = bodyTop() + row * step - scroll;
            if (!inside(mouseX, mouseY, rx, ry, columnWidth, ROW_HEIGHT)) continue;

            Module module = modules.get(i);
            // Left side toggles, the right side of the card opens its settings.
            boolean settingsSide = mouseX > rx + columnWidth - 62;
            if (settingsSide) {
                expanded = expanded == module ? null : module;
                settingsScroll = 0;
                textTarget = null;
            } else {
                module.toggle();
            }
            return true;
        }

        return false;
    }

    private boolean handleScrollClick(double mouseX, double mouseY) {
        if (expanded != null) {
            int x = settingsX();
            int w = settingsW();
            int max = settingsMaxScroll();
            if (max > 0) {
                if (inside(mouseX, mouseY, x + w - 18, bodyBottom() - 40, 16, 16)) {
                    settingsScroll = Math.max(0, settingsScroll - 60);
                    return true;
                }
                if (inside(mouseX, mouseY, x + w - 18, bodyBottom() - 20, 16, 16)) {
                    settingsScroll = Math.min(max, settingsScroll + 60);
                    return true;
                }
            }
            return false;
        }

        int x = listX();
        int w = listW();
        int max = listMaxScroll();
        if (max > 0) {
            if (inside(mouseX, mouseY, x + w - 18, bodyBottom() - 40, 16, 16)) {
                scroll = Math.max(0, scroll - 80);
                return true;
            }
            if (inside(mouseX, mouseY, x + w - 18, bodyBottom() - 20, 16, 16)) {
                scroll = Math.min(max, scroll + 80);
                return true;
            }
        }

        return false;
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        int x = settingsX();
        int w = settingsW();
        int y = bodyTop();

        int backW = 54;
        if (inside(mouseX, mouseY, x + w - backW - 8, y + 8, backW, 18)) {
            expanded = null;
            textTarget = null;
            bindTarget = null;
            bindIsModuleKeybind = false;
            return true;
        }

        int bindY = y + 32;
        if (inside(mouseX, mouseY, x, bindY, w, SETTING_ROW)) {
            bindTarget = null;
            bindIsModuleKeybind = true;
            return true;
        }

        int rowY = bindY + SETTING_ROW + 2 - settingsScroll;
        for (Object row : settingsRows()) {
            if (row instanceof String) {
                rowY += 20;
                continue;
            }

            Setting<?> setting = (Setting<?>) row;
            if (inside(mouseX, mouseY, x, rowY, w, SETTING_ROW)) {
                interact(setting, mouseX, mouseY, x, w, rowY);
                return true;
            }
            rowY += SETTING_ROW;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private void interact(Setting<?> setting, double mouseX, double mouseY, int x, int w, int y) {
        int right = x + w - 10;

        if (setting instanceof BoolSetting bool) {
            bool.set(!bool.get());
            return;
        }

        if (setting instanceof DoubleSetting number) {
            dragSetting = setting;
            dragTrackX = right - 150;
            dragTrackWidth = 100;
            updateSlider(number, mouseX, y);
            return;
        }

        if (setting instanceof IntSetting number) {
            dragSetting = setting;
            dragTrackX = right - 150;
            dragTrackWidth = 100;
            updateSlider(number, mouseX, y);
            return;
        }

        if (setting instanceof EnumSetting<?> enumSetting) {
            cycleEnum(enumSetting);
            return;
        }

        if (setting instanceof ColorSetting color) {
            cycleColor(color);
            return;
        }

        if (setting instanceof KeybindSetting keybindSetting) {
            bindTarget = setting;
            bindIsModuleKeybind = false;
            return;
        }

        if (setting instanceof StringSetting stringSetting) {
            textTarget = (Setting<String>) setting;
            textBuffer.setLength(0);
            textBuffer.append(stringSetting.get());
            return;
        }
    }

    private void updateSlider(DoubleSetting number, double mouseX, int y) {
        double min = Math.max(number.min, number.sliderMin);
        double max = Math.min(number.max, number.sliderMax);
        if (max <= min) return;

        double t = Math.max(0.0, Math.min(1.0, (mouseX - dragTrackX) / dragTrackWidth));
        double value = min + t * (max - min);
        number.set(Math.round(value * Math.pow(10, number.decimalPlaces)) / Math.pow(10, number.decimalPlaces));
    }

    private void updateSlider(IntSetting number, double mouseX, int y) {
        int min = Math.max(number.min, number.sliderMin);
        int max = Math.min(number.max, number.sliderMax);
        if (max <= min) return;

        double t = Math.max(0.0, Math.min(1.0, (mouseX - dragTrackX) / dragTrackWidth));
        number.set((int) Math.round(min + t * (max - min)));
    }

    @SuppressWarnings("unchecked")
    private void cycleEnum(EnumSetting<?> setting) {
        Object current = setting.get();
        Object[] values = current.getClass().getEnumConstants();
        if (values == null || values.length == 0) return;

        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }

        ((Setting) setting).set(values[(index + 1) % values.length]);
    }

    private void cycleColor(ColorSetting setting) {
        SettingColor current = setting.get();
        int index = 0;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (current.r == COLOR_PRESETS[i][0] && current.g == COLOR_PRESETS[i][1]
                && current.b == COLOR_PRESETS[i][2]) {
                index = i;
                break;
            }
        }

        int[] next = COLOR_PRESETS[(index + 1) % COLOR_PRESETS.length];
        setting.set(new SettingColor(next[0], next[1], next[2], current.a));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragSetting instanceof DoubleSetting number) {
            updateSlider(number, event.x(), 0);
            return true;
        }
        if (dragSetting instanceof IntSetting number) {
            updateSlider(number, event.x(), 0);
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragSetting != null) {
            dragSetting = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (expanded != null && mouseX >= settingsX()) {
            settingsScroll = clamp(settingsScroll - (int) (verticalAmount * 24), 0, settingsMaxScroll());
            return true;
        }

        scroll = clamp(scroll - (int) (verticalAmount * 24), 0, listMaxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();

        if (bindTarget != null || bindIsModuleKeybind) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                bindTarget = null;
                bindIsModuleKeybind = false;
                return true;
            }

            if (bindIsModuleKeybind) {
                if (expanded != null) expanded.keybind.set(true, keyCode, modifiers);
                bindIsModuleKeybind = false;
                return true;
            }

            Object value = bindTarget.get();
            if (value instanceof Keybind keybind) keybind.set(true, keyCode, modifiers);
            bindTarget = null;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (expanded != null) {
                expanded = null;
                textTarget = null;
                bindTarget = null;
                bindIsModuleKeybind = false;
                return true;
            }
            onClose();
            return true;
        }

        if (textTarget != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && textBuffer.length() > 0) {
                textBuffer.setLength(textBuffer.length() - 1);
                textTarget.set(textBuffer.toString());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                textTarget = null;
                return true;
            }
            return true;
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scroll = clamp(scroll + 24, 0, listMaxScroll());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scroll = clamp(scroll - 24, 0, listMaxScroll());
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String typed = event.codepointAsString();
        if (typed.isEmpty()) return false;

        if (textTarget != null) {
            textBuffer.append(typed);
            textTarget.set(textBuffer.toString());
            return true;
        }

        if (searchFocused) {
            query += typed;
            scroll = 0;
            return true;
        }

        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    private static String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (UiText.width(text) <= maxWidth) return text;

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (UiText.width(builder.toString()) > maxWidth - 8) {
                builder.setLength(Math.max(0, builder.length() - 1));
                return builder + "..";
            }
        }

        return builder.toString();
    }
}
