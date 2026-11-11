package com.chicken.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SettingGroup {
    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();

    public SettingGroup(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }
}
