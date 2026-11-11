package com.chicken.client.model;

import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** GeckoLib model pointing at the Blockbench exported karambit geometry. */
public final class KarambitModel extends GeoModel<KarambitAnimatable> {
    public static final Identifier MODEL =
        Identifier.fromNamespaceAndPath("template", "geckolib/models/item/karambit.geo.json");
    public static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("template", "textures/item/karambit.png");
    public static final Identifier ANIMATION =
        Identifier.fromNamespaceAndPath("template", "geckolib/animations/item/karambit.animation.json");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(KarambitAnimatable animatable) {
        return ANIMATION;
    }
}
