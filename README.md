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
- **Nano HUD** - a fully standalone HUD with its own config, mixin and font fallback. It does not depend on Meteor's HUD, settings, modules or 2D renderer, and it is drawn with vanilla `GuiGraphics` only: no NanoVG, no native libraries and no raw OpenGL, so it cannot black out world rendering. It has a layered glow frame, a travelling light sweep, animated corner ticks, a gradient player card with flapping yellow wings, a rotating reticle and radar ping around the avatar, live status chips (LOW HP, SPRINT, SWIM, AIR), health/armor/XP bars with shine, a live FPS graph, ping/FPS/speed/facing readouts, coordinates, a contacts radar strip showing nearby players by bearing, drifting dust and a pulsing low-health vignette. It draws its text with the bundled **Inter** typeface instead of the vanilla pixel font, so the interface looks like a modern overlay rather than a Minecraft screen; `modern-font=false` (or the FONT toggle in the menu) restores the vanilla font. F7 toggles it; **P** opens the Meteor-free in-game settings screen with a live yellow HUD preview, animated player card, wing speed/amplitude sliders and instant color/toggle updates. Keys are read from Minecraft's keyboard callback rather than polled GLFW state, and the HUD carries tappable **HUD** and **GUI** chips in its footer, so the HUD settings and the Chicken module GUI can be opened on phones where no right-shift key exists. Hold right-shift, drag with the left mouse button and use `[`/`]` to scale. The main brand color is yellow. HUD mode can be switched between the normal overlay and a **LEFT HAND** panel with a glove frame; left/right wings flap on separate phases. Config: `config/chicken-client/nano-hud.properties`.
- **Slow Motion** - slow-motion echoes for other players. Every step an enemy takes leaves a fading ghost body behind them, with motion streaks between the echoes, expanding ripples at their feet and an optional time-warp screen vignette, so the world reads as if it runs at a lower frame rate around them. It only reacts to other players, can skip Meteor friends so only enemies leave trails, and is purely visual. Echo count, spacing, lifetime, opacity and colours are configurable.
- **Client GUI** - the addon has its own module window and does not use Meteor's GUI: right shift (or the **GUI** chip on the HUD) opens a dark glass window with every module grouped by category, search, tap-to-toggle cards, live active counts and a full settings editor (booleans, integer and double sliders, enum cycling, colour presets, keybind capture and text). It is touch sized, scrolls with the wheel or on-screen arrows, and is drawn with vanilla primitives and the modern font.

- **Player Skeleton** - replaces players with a detailed, articulated human skeleton: skull with sockets, teeth and jaw, spine, rib cage, pelvis, clavicles, humerus/radius/ulna arms with finger joints, femur/tibia/fibula legs with patella and toes. The walk cycle bends knees and ankles, swings the arms and rolls the hips and shoulders, so it sways like a biology-class skeleton. The vanilla skin and armor collapse under the bones (`hide-model`), so what you see really is a skeleton. Bone thickness, scale, sway, extra detail, glow and two colours are configurable.
- **Emotes** - ten Fortnite-style emotes performed by the skeleton and bound to **H**: Floss, Take the L, Dab, Orange Justice, Best Mates, Electro Shuffle, Robot, Ride the Pony, Air Guitar and Hype. Pressing H again while an emote runs chains to the next one; duration, looping and the key itself are configurable. It works with or without the skeleton module.
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
