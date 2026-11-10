package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;

/** Client-side fullbright using the lightmap texture. */
public class ChickenFullbright extends Module {
    public ChickenFullbright() {
        super(AddonTemplate.CATEGORY, "chicken-fullbright", "Lights up your world without changing the server side.");
    }
}
