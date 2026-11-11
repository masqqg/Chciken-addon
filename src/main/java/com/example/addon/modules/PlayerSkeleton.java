package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.render.SkeletonPose;
import com.example.addon.render.SkeletonRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;

/**
 * Turns the player into a walking human skeleton.
 *
 * <p>The skeleton is a real articulated model: skull with sockets, teeth and a
 * jaw, spine, rib cage, pelvis, clavicles, arms with two forearm bones and
 * finger joints, legs with tibia and fibula, patella, and toes. The walk cycle
 * drives knees, ankles, arm swing, hip roll and torso twist, so it sways like a
 * biology-class skeleton instead of gliding.</p>
 */
public class PlayerSkeleton extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
        .name("self")
        .description("Show the skeleton on yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> others = sgGeneral.add(new BoolSetting.Builder()
        .name("others")
        .description("Show skeletons on other players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideModel = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-model")
        .description("Collapse the vanilla player model so only the skeleton is visible.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away other players can be.")
        .defaultValue(32.0)
        .range(4.0, 96.0)
        .sliderRange(8.0, 64.0)
        .build()
    );

    private final Setting<Double> thickness = sgLook.add(new DoubleSetting.Builder()
        .name("bone-thickness")
        .description("Thickness of the bones. Thicker reads better from a distance.")
        .defaultValue(0.032)
        .range(0.012, 0.070)
        .sliderRange(0.018, 0.055)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall skeleton size.")
        .defaultValue(1.0)
        .range(0.6, 1.4)
        .sliderRange(0.8, 1.2)
        .build()
    );

    private final Setting<Double> sway = sgLook.add(new DoubleSetting.Builder()
        .name("sway")
        .description("How much the hips roll and the shoulders twist while walking.")
        .defaultValue(1.0)
        .range(0.0, 2.5)
        .sliderRange(0.2, 1.8)
        .build()
    );

    private final Setting<Boolean> details = sgLook.add(new BoolSetting.Builder()
        .name("extra-detail")
        .description("Fingers, toes, teeth, cheekbones and the full rib cage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("glow")
        .description("Accent edges along the bones.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> boneColor = sgLook.add(new ColorSetting.Builder()
        .name("bone-color")
        .defaultValue(new SettingColor(238, 234, 221))
        .build()
    );

    private final Setting<SettingColor> accentColor = sgLook.add(new ColorSetting.Builder()
        .name("accent-color")
        .defaultValue(new SettingColor(255, 196, 0))
        .build()
    );

    public PlayerSkeleton() {
        super(AddonTemplate.CATEGORY, "player-skeleton",
            "Replaces players with a detailed, articulated walking skeleton.");
    }

    @Override
    public void onDeactivate() {
        // Nothing to clean up: the skeleton is drawn per frame.
    }

    public boolean showsSelf() {
        return self.get();
    }

    public boolean showsOthers() {
        return others.get();
    }

    public boolean hidesVanillaModel() {
        return hideModel.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        double time = System.nanoTime() / 1_000_000_000.0;

        if (self.get()) draw(event, mc.player, time, true);

        if (others.get()) {
            double rangeSq = range.get() * range.get();

            for (Player player : mc.level.players()) {
                if (player == mc.player) continue;
                if (mc.player.distanceToSqr(player) > rangeSq) continue;
                draw(event, player, time + player.getId() * 0.41, false);
            }
        }
    }

    private void draw(Render3DEvent event, Player player, double time, boolean isSelf) {
        SkeletonPose pose = SkeletonPose.walk(player, event.tickDelta, time, sway.get());

        if (isSelf) {
            Emotes emotes = Modules.get().get(Emotes.class);
            if (emotes != null && emotes.isActive()) emotes.applyPose(pose);
        }

        SkeletonRenderer.render(event, player, pose, scale.get(), thickness.get(),
            boneColor.get(), accentColor.get(), glow.get(), details.get());
    }
}
