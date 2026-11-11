package com.chicken.client.settings;

import com.google.gson.JsonElement;
import java.util.function.Supplier;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private Supplier<Boolean> visible = () -> true;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public boolean isVisible() {
        return visible.get();
    }

    public Setting<T> visible(Supplier<Boolean> supplier) {
        this.visible = supplier == null ? () -> true : supplier;
        return this;
    }

    public abstract String valueText();

    public abstract JsonElement save();

    public abstract void load(JsonElement element);
}
