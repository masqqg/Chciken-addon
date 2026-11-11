package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Fullbright;

/**
 * Client-side fullbright.
 *
 * <p>This used to clear the lightmap texture with raw GPU calls and cancel
 * vanilla's lightmap update. That bypassed the render system's state tracking
 * and could leave the world rendering black. It now simply drives Meteor's own
 * fullbright, which applies the effect through a supported lightmap hook.</p>
 */
public class ChickenFullbright extends Module {
    private Fullbright meteorFullbright;
    private boolean alreadyActive;

    public ChickenFullbright() {
        super(AddonTemplate.CATEGORY, "chicken-fullbright", "Lights up your world without changing the server side.");
    }

    @Override
    public void onActivate() {
        Fullbright fullbright = Modules.get().get(Fullbright.class);
        if (fullbright == null) return;

        meteorFullbright = fullbright;
        alreadyActive = fullbright.isActive();
        if (!alreadyActive) fullbright.toggle();
    }

    @Override
    public void onDeactivate() {
        if (meteorFullbright != null && !alreadyActive && meteorFullbright.isActive()) {
            meteorFullbright.toggle();
        }

        meteorFullbright = null;
        alreadyActive = false;
    }
}
