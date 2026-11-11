package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.render.SkeletonPose;
import com.example.addon.render.SkeletonRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * Ten Fortnite-style emotes performed by the player skeleton.
 *
 * <p>While the module is active, pressing the bound key (H by default) plays the
 * selected emote; pressing it again while one is running moves on to the next
 * emote, so you can chain them. The animations drive the same articulated
 * skeleton as the player-skeleton module; if that module is off, the emote still
 * renders a skeleton here so the animation is always visible.</p>
 */
public class Emotes extends Module {
    public enum Emote {
        Floss,
        TakeTheL,
        Dab,
        OrangeJustice,
        BestMates,
        ElectroShuffle,
        Robot,
        RideThePony,
        AirGuitar,
        Hype
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<Emote> emote = sgGeneral.add(new EnumSetting.Builder<Emote>()
        .name("emote")
        .description("Emote played the next time you press the key.")
        .defaultValue(Emote.Floss)
        .build()
    );

    private final Setting<Integer> key = sgGeneral.add(new IntSetting.Builder()
        .name("key")
        .description("Key that plays and chains emotes. 80 is H.")
        .defaultValue(GLFW.GLFW_KEY_H)
        .range(0, 1000)
        .sliderRange(0, 0)
        .build()
    );

    private final Setting<Double> duration = sgGeneral.add(new DoubleSetting.Builder()
        .name("duration")
        .description("How long one emote lasts in seconds.")
        .defaultValue(4.0)
        .range(0.5, 30.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    private final Setting<Boolean> loop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .description("Keep repeating the emote until you stop it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Skeleton size when the player-skeleton module is off.")
        .defaultValue(1.0)
        .range(0.6, 1.4)
        .sliderRange(0.8, 1.2)
        .build()
    );

    private final Setting<Double> thickness = sgLook.add(new DoubleSetting.Builder()
        .name("bone-thickness")
        .defaultValue(0.032)
        .range(0.012, 0.070)
        .sliderRange(0.018, 0.055)
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

    private boolean keyHeld;
    private boolean playing;
    private Emote active;
    private long startedAt;
    private final SkeletonPose pose = new SkeletonPose();

    public Emotes() {
        super(AddonTemplate.CATEGORY, "emotes",
            "Ten Fortnite-style emotes on H: Floss, Take the L, Dab, Orange Justice and more.");
    }

    @Override
    public void onDeactivate() {
        playing = false;
        keyHeld = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            playing = false;
            return;
        }

        if (!loop.get() && playing && elapsed() > duration.get()) {
            playing = false;
        }

        boolean down = keyDown(key.get());
        if (down && !keyHeld && mc.screen == null) trigger();

        keyHeld = down;
    }

    private void trigger() {
        Emote next;
        if (playing && active != null) {
            Emote[] values = Emote.values();
            next = values[(active.ordinal() + 1) % values.length];
        } else {
            next = emote.get();
        }

        active = next;
        startedAt = System.nanoTime();
        playing = true;
        info("Emote: %s", next.name());
    }

    private double elapsed() {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }

    /** Called by the player-skeleton module so the skeleton performs the emote. */
    public void applyPose(SkeletonPose target) {
        if (!playing) return;

        double time = elapsed();
        if (!loop.get() && time > duration.get()) {
            playing = false;
            return;
        }

        apply(target, active, time);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!playing || mc.player == null || mc.level == null) return;

        PlayerSkeleton skeleton = Modules.get().get(PlayerSkeleton.class);
        if (skeleton != null && skeleton.isActive() && skeleton.showsSelf()) return;

        apply(pose, active, elapsed());

        double time = System.nanoTime() / 1_000_000_000.0;
        SkeletonRenderer.render(event, mc.player, pose, scale.get(), thickness.get(),
            boneColor.get(), accentColor.get(), false, true);
    }

    private boolean keyDown(int code) {
        if (code <= 0) return false;
        return GLFW.glfwGetKey(mc.getWindow().handle(), code) == GLFW.GLFW_PRESS;
    }

    /**
     * Fills a pose with the selected emote at time t (seconds).
     *
     * <p>Every emote drives arms, elbows, knees, hips, torso and head so the
     * silhouette matches the original dance instead of just waving an arm.</p>
     */
    private static void apply(SkeletonPose p, Emote emote, double t) {
        switch (emote) {
            case Floss -> {
                double w = Math.sin(t * 7.0);
                p.bodyRoll = w * 0.22;
                p.bodyTwist = w * 0.34;
                p.armPitchL = w * 1.15;
                p.armPitchR = -w * 1.15;
                p.armOutL = 0.22;
                p.armOutR = 0.22;
                p.elbowL = 0.60;
                p.elbowR = 0.60;
                p.rootLift = Math.abs(w) * 0.03;
                p.kneeL = 0.20 + Math.max(0.0, w) * 0.25;
                p.kneeR = 0.20 + Math.max(0.0, -w) * 0.25;
                p.headYaw = w * 0.12;
            }
            case TakeTheL -> {
                double w = Math.sin(t * 8.0);
                p.armPitchL = 1.25;
                p.armPitchR = 1.25;
                p.elbowL = 1.95;
                p.elbowR = 1.95;
                p.armOutL = 0.12;
                p.armOutR = 0.12;
                p.headYaw = w * 0.35;
                p.bodyRoll = w * 0.06;
                p.bodyTwist = w * 0.08;
                p.rootLift = Math.abs(w) * 0.012;
            }
            case Dab -> {
                double w = Math.sin(t * 2.4);
                p.armPitchR = 2.50;
                p.armOutR = 1.00;
                p.elbowR = 0.10;
                p.armPitchL = 1.55;
                p.armOutL = -0.20;
                p.elbowL = 2.20;
                p.bodyTwist = 0.35 + w * 0.04;
                p.bodyRoll = 0.15;
                p.headPitch = 0.25;
                p.rootLift = Math.abs(Math.sin(t * 4.0)) * 0.02;
            }
            case OrangeJustice -> {
                double w = Math.sin(t * 8.0);
                p.armPitchL = 1.40 + w * 0.95;
                p.armPitchR = 1.40 - w * 0.95;
                p.elbowL = 1.25;
                p.elbowR = 1.25;
                p.armOutL = 0.35;
                p.armOutR = 0.35;
                p.bodyRoll = w * 0.14;
                p.bodyTwist = -w * 0.10;
                p.kneeL = 0.25 + Math.max(0.0, w) * 0.30;
                p.kneeR = 0.25 + Math.max(0.0, -w) * 0.30;
                p.rootLift = Math.abs(w) * 0.03;
            }
            case BestMates -> {
                double w = Math.sin(t * 7.0);
                p.armPitchL = 2.35 + w * 0.75;
                p.armPitchR = 2.35 - w * 0.75;
                p.elbowL = 0.45;
                p.elbowR = 0.45;
                p.armOutL = 0.40;
                p.armOutR = 0.40;
                p.rootLift = Math.abs(w) * 0.045;
                p.kneeL = 0.15 + Math.max(0.0, w) * 0.20;
                p.kneeR = 0.15 + Math.max(0.0, -w) * 0.20;
                p.headPitch = -0.12;
            }
            case ElectroShuffle -> {
                double w = Math.sin(t * 6.0);
                p.armOutL = 1.25;
                p.armOutR = 1.25;
                p.armPitchL = 0.35 + w * 0.55;
                p.armPitchR = 0.35 - w * 0.55;
                p.elbowL = 1.00;
                p.elbowR = 1.00;
                p.legPitchL = w * 0.35;
                p.legPitchR = -w * 0.35;
                p.kneeL = 0.35 + Math.max(0.0, -w) * 0.30;
                p.kneeR = 0.35 + Math.max(0.0, w) * 0.30;
                p.bodyRoll = w * 0.16;
                p.bodyTwist = -w * 0.12;
                p.rootLift = Math.abs(w) * 0.025;
            }
            case Robot -> {
                double step = Math.floor(t * 3.0);
                boolean flip = ((int) step) % 2 == 0;
                double snap = Math.sin((t * 3.0 - step) * Math.PI) * 0.08;

                p.armPitchL = flip ? 1.57 : 0.25;
                p.elbowL = flip ? 1.57 : 0.30;
                p.armPitchR = flip ? 0.25 : 1.57;
                p.elbowR = flip ? 0.30 : 1.57;
                p.armOutL = flip ? 0.55 : 0.10;
                p.armOutR = flip ? 0.10 : 0.55;
                p.bodyTwist = flip ? 0.22 : -0.22;
                p.bodyRoll = flip ? -0.05 : 0.05;
                p.headYaw = flip ? 0.40 : -0.40;
                p.headPitch = snap;
                p.kneeL = flip ? 0.28 : 0.12;
                p.kneeR = flip ? 0.12 : 0.28;
            }
            case RideThePony -> {
                double w = t * 6.0;
                p.armPitchR = 1.50 + Math.sin(w) * 0.35;
                p.armOutR = 0.90 + Math.cos(w) * 0.50;
                p.elbowR = 1.20;
                p.armPitchL = 1.15;
                p.elbowL = 1.65;
                p.armOutL = 0.25;
                p.kneeL = 0.55 + Math.abs(Math.sin(w)) * 0.25;
                p.kneeR = 0.55 + Math.abs(Math.cos(w)) * 0.25;
                p.rootLift = Math.abs(Math.sin(w)) * 0.05;
                p.bodyRoll = Math.sin(w) * 0.10;
                p.bodyLean = 0.12;
            }
            case AirGuitar -> {
                double w = Math.sin(t * 9.0);
                p.armPitchL = 1.05;
                p.elbowL = 1.45;
                p.armOutL = 0.55;
                p.armPitchR = 0.85 + w * 0.18;
                p.elbowR = 1.15 + w * 0.45;
                p.armOutR = 0.35;
                p.headPitch = 0.28 + Math.sin(t * 4.5) * 0.32;
                p.bodyLean = 0.14;
                p.bodyTwist = Math.sin(t * 4.5) * 0.10;
                p.rootLift = Math.abs(w) * 0.012;
            }
            case Hype -> {
                double w = Math.sin(t * 5.0);
                p.rootLift = Math.abs(w) * 0.10;
                p.armPitchL = 2.60 + w * 0.35;
                p.armPitchR = 2.60 - w * 0.35;
                p.elbowL = 0.55;
                p.elbowR = 0.55;
                p.armOutL = 0.30;
                p.armOutR = 0.30;
                p.kneeL = 0.20 + Math.max(0.0, w) * 0.35;
                p.kneeR = 0.20 + Math.max(0.0, -w) * 0.35;
                p.bodyLean = 0.05;
                p.headPitch = -0.10;
            }
        }
    }

    /** Exposed so the HUD could show the current emote later. */
    public Emote activeEmote() {
        return active;
    }

    public boolean isPlaying() {
        return playing;
    }
}
