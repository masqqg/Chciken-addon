package com.example.addon;

import com.example.addon.modules.ChickenStorageESP;
import com.example.addon.modules.ClusterBypass;
import com.example.addon.modules.ClearRender;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("Chicken");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Chicken Addon");

        // Modules
        Modules.get().add(new ChickenStorageESP());
        Modules.get().add(new ClusterBypass());
        Modules.get().add(new ClearRender());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
