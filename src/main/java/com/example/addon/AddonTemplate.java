
package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.ChickenWatermarkHud;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.BaseFinder;
import com.example.addon.modules.ChickenStorageESP;
import com.example.addon.modules.ClusterBypass;
import com.example.addon.modules.BlockESP;
import com.example.addon.modules.ChickenFreeLook;
import com.example.addon.modules.ChickenFreecam;
import com.example.addon.modules.ChickenFullbright;
import com.example.addon.modules.ClearRender;
import com.example.addon.modules.Karambit;
import com.example.addon.modules.PlayerFinder;
import com.example.addon.modules.SpawnerFlag;
import com.example.addon.modules.SusChunkFinder;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.hud.XAnchor;
import meteordevelopment.meteorclient.systems.hud.YAnchor;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("Chicken Client");
    public static final HudGroup HUD_GROUP = new HudGroup("Chicken Client");

    private int hudBootstrapTicks;
    private boolean hudBootstrapDone;

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
        Modules.get().add(new SpawnerFlag());
        Modules.get().add(new SusChunkFinder());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud hud = Hud.get();
        if (hud != null) {
            hud.register(ChickenWatermarkHud.INFO);
            hud.register(HudExample.INFO);
        }

        // HUD config is loaded after addon init, so add the watermark after the
        // HUD's first tick instead of racing the config load.
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (hudBootstrapDone) return;
        if (++hudBootstrapTicks < 2) return;

        Hud hud = Hud.get();
        if (hud == null) return;

        hudBootstrapDone = true;

        for (HudElement element : hud) {
            if (element.info.name.equals(ChickenWatermarkHud.INFO.name)) return;
        }

        hud.add(ChickenWatermarkHud.INFO, 6, 6, XAnchor.Left, YAnchor.Top);
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
