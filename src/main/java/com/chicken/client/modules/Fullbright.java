package com.chicken.client.modules;

import com.chicken.client.core.Category;
import com.chicken.client.core.Module;

/** Client-side fullbright; the actual lightmap swap lives in ChickenFullbrightMixin. */
public final class Fullbright extends Module {
    public Fullbright() {
        super(Category.RENDER, "fullbright", "Lights up the world without changing anything server side.");
    }
}
