package com.chicken.client.modules;

import com.chicken.client.core.Category;
import com.chicken.client.core.Module;
import com.chicken.client.settings.DoubleSetting;
import net.minecraft.client.renderer.state.SkyRenderState;

/**
 * Real sky replacement: the vanilla sky state is rewritten before it is rendered,
 * so the sky dome, the fog/ambient tint and the stars all come from this palette.
 */
public final class CosmicSky extends Module {
    private static final int DEEP = 0xFF0D0722;
    private static final int MID = 0xFF2B1066;
    private static final int HORIZON = 0xFFFF2E7A;
    private static final int NEBULA = 0xFF6A2BFF;

    private final DoubleSetting intensity =
        settings().add(new DoubleSetting("intensity", "How strongly the cosmic palette replaces the vanilla sky.", 0.85, 0.0, 1.0));
    private final DoubleSetting stars =
        settings().add(new DoubleSetting("stars", "Star brightness.", 0.90, 0.0, 1.0));
    private final DoubleSetting pulse =
        settings().add(new DoubleSetting("pulse", "Speed of the drifting colour pulse.", 0.35, 0.0, 2.0));

    public CosmicSky() {
        super(Category.RENDER, "cosmic-sky", "Purple nebula sky with drifting gradient and bright stars.");
    }

    /** Called from the sky render state extraction mixin. */
    public void apply(SkyRenderState state) {
        if (state == null) return;
        float strength = intensity.get().floatValue();
        if (strength <= 0.0f) return;

        float drift = (float) (Math.sin(System.currentTimeMillis() / 4200.0 * (0.4 + pulse.get().doubleValue())) * 0.5 + 0.5);
        int sky = mix(DEEP, MID, drift);
        int horizon = mix(HORIZON, NEBULA, 1.0f - drift);

        state.skyColor = mix(state.skyColor == 0 ? DEEP : state.skyColor, sky, strength);
        state.sunriseAndSunsetColor = mix(state.sunriseAndSunsetColor == 0 ? horizon : state.sunriseAndSunsetColor, horizon, strength);

        double wantedStars = stars.get().doubleValue();
        if (wantedStars > state.starBrightness) state.starBrightness = (float) Math.min(1.0, wantedStars);
    }

    private static int mix(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = (int) (((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
