package com.chicken.client.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> byCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module module : modules) if (module.category() == category) list.add(module);
        return list;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) if (type.isInstance(module)) return (T) module;
        return null;
    }

    public Module get(String name) {
        for (Module module : modules) if (module.name().equalsIgnoreCase(name)) return module;
        return null;
    }

    public void tick() {
        for (Module module : modules) if (module.isEnabled()) module.onTick();
    }
}
