# Chicken Client

![Animated HUD preview](artifacts/chicken-hud-preview.gif)

Animated design preview of the yellow HUD and the in-game settings card.

The HUD is drawn entirely with Minecraft's own GUI pipeline, so it needs no native libraries and never touches GPU state.

Chicken Client is a Meteor Client addon for Minecraft 1.21.11.

## Included

- **Karambit** - animated first-person karambit replacement with a curved hooked blade, bevel and energy edge, wrapped grip, gloved hand, continuous finger spin, motion-blur ghosts, a fading swing trail and an impact spark burst.
- **Fullbright** - client-side fullbright that drives Meteor's own lightmap hook, so it never touches GPU textures directly.
- **Freecam** - detached, no-clip camera; the player stays in place and can keep mining.
- **Free Look** - independent camera look with an optional through-walls mode.
- **Block ESP** - configurable block scanner with boxes and tracers.
- **Storage ESP** - container detection with block-entity debug and chunk data readouts.
- **Nano HUD** - a fully standalone HUD with its own config, mixin and font fallback. It does not depend on Meteor's HUD, settings, modules or 2D renderer, and it is drawn with vanilla `GuiGraphics` only: no NanoVG, no native libraries and no raw OpenGL, so it cannot black out world rendering. It has an animated cloud watermark, a gradient player card with flapping yellow wings, orbiting avatar aura, radar ping, health/armor/XP bars, a live FPS graph, ping/FPS/speed counters, coordinates, drifting dust, a moving scanline and a pulsing low-health vignette. F7 toggles it; F8 opens the Meteor-free in-game settings screen with a live yellow HUD preview, animated player card, wing speed/amplitude sliders and instant color/toggle updates. Hold right-shift, drag with the left mouse button and use `[`/`]` to scale. The main brand color is yellow. HUD mode can be switched between the normal overlay and a **LEFT HAND** panel with a glove frame; left/right wings flap on separate phases. Config: `config/chicken-client/nano-hud.properties`.
- **Block Wave** - ripples the blocks around you like water. A jump, a landing or every few steps lifts and tints the real block surfaces at the moving wave front, with a shock ring, glowing skirts, outlines, separate jump/land/step triggers and configurable radius, lift, band width and colors. Client side and purely visual.
- **Red Wings** - layered animated crimson wings for yourself and nearby players, with a curved wing arm, nine independently curved feathers, cross ribbons for volume, a pulsing shoulder aura, a double-beat flap, glow, feather ribs, trailing ribbons and drifting ember trails.
- **Cosmic Sky** - a cinematic 3D atmosphere with ringed planets and orbiting moons, drifting nebula clouds, aurora ribbons, twinkling stars, comets and shooting stars, using the yellow Chicken Client palette by default.
- ClusterBypass, BaseFinder, PlayerFinder, SpawnerFlag, ClearRender and Storage ESP utilities.

## Build

```bash
./gradlew build
```

The jar is written to `build/libs/chicken-client-<version>.jar`.
