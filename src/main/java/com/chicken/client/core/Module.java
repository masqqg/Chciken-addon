package com.chicken.client.core;

import com.chicken.client.settings.SettingGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Base class for every Chicken Client module. */
public class Module {
    private final String name;
    private final String description;
    private final Category category;

    private final SettingGroup settings = new SettingGroup("general");
    private final List<SettingGroup> groups = new ArrayList<>();

    private boolean enabled;
    private int bind = -1;

    protected Module(Category category, String name, String description) {
        this.category = category;
        this.name = name;
        this.description = description;
        groups.add(settings);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (value) onActivate();
        else onDeactivate();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public int getBind() {
        return bind;
    }

    public void setBind(int bind) {
        this.bind = bind;
    }

    public SettingGroup settings() {
        return settings;
    }

    public SettingGroup settingsGroup(String name) {
        SettingGroup group = new SettingGroup(name);
        groups.add(group);
        return group;
    }

    public List<SettingGroup> settingsGroups() {
        return Collections.unmodifiableList(groups);
    }

    // Hooks
    public void onActivate() {
    }

    public void onDeactivate() {
    }

    public void onTick() {
    }
}
