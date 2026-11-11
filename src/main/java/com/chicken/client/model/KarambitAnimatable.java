package com.chicken.client.model;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Animatable side of the Blockbench karambit. GeckoLib drives the bone
 * animation from karambit.animation.json; the client only has to render it.
 */
public final class KarambitAnimatable implements GeoAnimatable {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation INSPECT = RawAnimation.begin().thenPlay("inspect");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<KarambitAnimatable>("main", 4, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<KarambitAnimatable>("inspect", 4, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
