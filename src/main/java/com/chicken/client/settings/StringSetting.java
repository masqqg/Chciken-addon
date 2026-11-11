package com.chicken.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class StringSetting extends Setting<String> {
    public StringSetting(String name, String description, String defaultValue) {
        super(name, description, defaultValue);
    }

    @Override
    public String valueText() {
        String value = get();
        return value.length() > 24 ? value.substring(0, 24) + "..." : value;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(get());
    }

    @Override
    public void load(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsString());
    }
}
