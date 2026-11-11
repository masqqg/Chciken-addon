package com.chicken.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    @Override
    public String valueText() {
        return get() ? "ON" : "OFF";
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(get());
    }

    @Override
    public void load(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsBoolean());
    }
}
