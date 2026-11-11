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
- **Nano HUD** - a fully standalone HUD with its own config, mixin and font fallback. It does not depend on Meteor's HUD, settings, modules or 2D renderer, and it is drawn with vanilla `GuiGraphics` only: no NanoVG, no native libraries and no raw OpenGL, so it cannot black out world rendering. It has a layered glow frame, a travelling light sweep, animated corner ticks, a gradient player card with flapping yellow wings, a rotating reticle and radar ping around the avatar, live status chips (LOW HP, SPRINT, SWIM, AIR), health/armor/XP bars with shine, a live FPS graph, ping/FPS/speed/facing readouts, coordinates, a contacts radar strip showing nearby players by bearing, drifting dust and a pulsing low-health vignette. F7 toggles it; **P** opens the Meteor-free in-game settings screen with a live yellow HUD preview, animated player card, wing speed/amplitude sliders and instant color/toggle updates. Hold right-shift, drag with the left mouse button and use `[`/`]` to scale. The main brand color is yellow. HUD mode can be switched between the normal overlay and a **LEFT HAND** panel with a glove frame; left/right wings flap on separate phases. Config: `config/chicken-client/nano-hud.properties`.
- **Player Hat** - cosmetic hats above the player's head for yourself and nearby players: a gemmed gold crown, a striped party hat with a pompom, a glowing halo, a top hat or curved devil horns. Hats follow your yaw, bob gently and can glow and sparkle. Style, scale and three colours are configurable.
- **Block Wave** - ripples the blocks around you like water. A jump, a landing or every few steps lifts and tints the real block surfaces at the moving wave front, with a shock ring, glowing skirts, outlines, separate jump/land/step triggers and configurable radius, lift, band width and colors. Client side and purely visual.
- **Red Wings** - layered animated crimson wings for yourself and nearby players, with a curved wing arm, nine independently curved feathers, cross ribbons for volume, a pulsing shoulder aura, a double-beat flap, glow, feather ribs, trailing ribbons and drifting ember trails.
- **Cosmic Sky** - a cinematic 3D atmosphere with ringed planets and orbiting moons, drifting nebula clouds, aurora ribbons, twinkling stars, comets and shooting stars, using the yellow Chicken Client palette by default. Its **Hell Gate** layer adds a burning void with ember veins and a small white aperture, out of which a huge four-tailed star blazes with halo rings, a starburst core and drifting sparks (size, tail length, smoke and colours are configurable).
- ClusterBypass, BaseFinder, PlayerFinder, SpawnerFlag, ClearRender and Storage ESP utilities.

## Build

```bash
./gradlew build
```

The jar is written to `build/libs/chicken-client-<version>.jar`.
