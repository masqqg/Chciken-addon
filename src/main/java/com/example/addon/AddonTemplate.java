package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.modules.BaseFinder;
import com.example.addon.modules.BlockESP;
import com.example.addon.modules.ChickenFreeLook;
import com.example.addon.modules.ChickenFullbright;
import com.example.addon.modules.ChickenHud;
import com.example.addon.modules.ChickenStorageESP;
import com.example.addon.modules.ClearRender;
import com.example.addon.modules.ClusterBypass;
import com.example.addon.modules.Karambit;
import com.example.addon.modules.PlayerFinder;
import com.example.addon.modules.RedWings;
import com.example.addon.modules.SpawnerFlag;
import com.example.addon.modules.SusChunkFinder;
import com.example.addon.modules.ChickenFreecam;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("Chicken Client");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Chicken Client");

        // Modules
        Modules.get().add(new BaseFinder());
        Modules.get().add(new BlockESP());
        Modules.get().add(new ChickenFreecam());
        Modules.get().add(new ChickenFreeLook());
        Modules.get().add(new ChickenFullbright());
        Modules.get().add(new ChickenStorageESP());
        Modules.get().add(new ClusterBypass());
        Modules.get().add(new ClearRender());
        Modules.get().add(new Karambit());
        Modules.get().add(new PlayerFinder());
        Modules.get().add(new RedWings());
        Modules.get().add(new SpawnerFlag());
        Modules.get().add(new SusChunkFinder());

        // The NanoVG HUD is intentionally not a Meteor HudElement and does not
        // subscribe to Render2DEvent. Enable it by default on first install;
        // an existing config can still turn it off.
        ChickenHud hud = new ChickenHud();
        Modules.get().add(hud);
        hud.enable();

        // Commands
        Commands.add(new CommandExample());
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
        return new GithubRepo("masqqg", "Chciken-addon");
    }
}
