package com.chicken.client;

import com.chicken.client.config.ConfigManager;
import com.chicken.client.core.Module;
import com.chicken.client.core.ModuleManager;
import com.chicken.client.gui.ClickGuiScreen;
import com.chicken.client.modules.BlockESP;
import com.chicken.client.modules.FreeLook;
import com.chicken.client.modules.Freecam;
import com.chicken.client.modules.Fullbright;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Standalone Chicken Client entrypoint: no Meteor, no Orbit, no other client. */
public final class ChickenClient implements ClientModInitializer {
    public static final String NAME = "Chicken Client";
    public static final String VERSION = "0.1.0";
    public static final Logger LOG = LoggerFactory.getLogger("ChickenClient");

    public static final int GUI_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private static ChickenClient instance;

    private final ModuleManager modules = new ModuleManager();
    private final ConfigManager config = new ConfigManager();
    private final java.util.Map<Integer, Boolean> held = new java.util.HashMap<>();

    public static ChickenClient get() {
        return instance;
    }

    public static ModuleManager modules() {
        return instance.modules;
    }

    public static ConfigManager config() {
        return instance.config;
    }

    public static <T extends Module> T module(Class<T> type) {
        return instance == null ? null : instance.modules.get(type);
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        modules.register(new Fullbright());
        modules.register(new Freecam());
        modules.register(new FreeLook());
        modules.register(new BlockESP());

        config.load();

        LOG.info("{} v{} ready with {} modules", NAME, VERSION, modules.all().size());
    }

    /** Called once per client tick from MinecraftMixin. */
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        long window = mc.getWindow().handle();

        if (mc.screen == null) {
            if (justPressed(window, GUI_KEY)) {
                mc.execute(() -> mc.setScreen(new ClickGuiScreen()));
                return;
            }

            for (Module module : modules.all()) {
                int bind = module.getBind();
                if (bind > 0 && justPressed(window, bind)) {
                    module.toggle();
                    config.save();
                }
            }
        }

        if (mc.player != null && mc.level != null) modules.tick();
    }

    /** Called from ChickenRenderMixin at the end of the level render pass. */
    public void onRender3D() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;

        PoseStack pose = new PoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        pose.translate(-camera.x, -camera.y, -camera.z);

        for (Module module : modules.all()) {
            if (module.isEnabled()) module.onRender3D(pose);
        }
    }

    private boolean justPressed(long window, int key) {
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        Boolean was = held.put(key, down);
        return down && (was == null || !was);
    }

    public static String keyName(int key) {
        if (key <= 0) return "-";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase(java.util.Locale.ROOT);
        return "KEY" + key;
    }
}
