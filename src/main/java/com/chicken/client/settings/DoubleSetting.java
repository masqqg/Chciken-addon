package com.chicken.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class DoubleSetting extends Setting<Double> {
    private final double min;
    private final double max;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    @Override
    public void set(Double value) {
        double clamped = value == null ? min : Math.max(min, Math.min(max, value));
        super.set(Math.round(clamped * 100.0) / 100.0);
    }

    public void setFraction(double fraction) {
        set(min + (max - min) * Math.max(0.0, Math.min(1.0, fraction)));
    }

    public double fraction() {
        return max <= min ? 0.0 : (get() - min) / (max - min);
    }

    @Override
    public String valueText() {
        return String.format(java.util.Locale.ROOT, "%.2f", get());
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(get());
    }

    @Override
    public void load(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsDouble());
    }
}
