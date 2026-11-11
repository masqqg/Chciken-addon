# Chicken Client

Chicken Client is a Meteor Client addon for Minecraft 1.21.11.

## Included

- **Karambit** - animated first-person karambit replacement.
- **Fullbright** - client-side fullbright lightmap.
- **Freecam** - detached, no-clip camera; the player stays in place and can keep mining.
- **Free Look** - independent camera look with an optional through-walls mode.
- **Block ESP** - configurable block scanner with boxes and tracers.
- **Storage ESP** - container detection with block-entity debug and chunk data readouts.
- **Nano HUD** - a standalone NanoVG HUD rendered by its own in-game mixin, not by Meteor's HUD or 2D renderer. It has an animated cloud watermark, player card, health/armor/XP bars, ping/FPS/speed/module counters and coordinates. Hold right-shift and drag with the left mouse button to place it.
- **Red Wings** - layered animated crimson wings for yourself and nearby players, with glow, feather ribs and ember trails.
- ClusterBypass, BaseFinder, PlayerFinder, SpawnerFlag, ClearRender and Storage ESP utilities.

## Build

```bash
./gradlew build
```

The jar is written to `build/libs/chicken-client-<version>.jar`.
