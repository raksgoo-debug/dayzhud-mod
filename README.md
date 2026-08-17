# DayZ HUD (Forge 1.20.1)

Pinned to **Forge 47.4.20** and **Thirst Was Taken 1.20.1-1.4.0**.

Replaces the vanilla hunger/health/armor/air bars with a DayZ-style horizontal status
row in the bottom-right corner: **Temperature, Stamina, Food, Water, Health**, each a
smooth icon + percentage, color-coded by severity (white/grey = fine, amber = low,
red = critical).

- **Water** reads the real thirst value from Thirst Was Taken via its actual capability
  API (confirmed directly from the mod's jar - see below), and falls back to vanilla
  food saturation if the mod isn't installed.
- **Stamina** and **Temperature** are custom additions (vanilla has neither): stamina
  drains on sprint/jump and regenerates when idle; temperature is a smoothed read of
  the biome you're standing in (colder in water, hot in lava/fire/Nether).
- Icons are smooth anti-aliased PNG textures (`src/main/resources/assets/dayzhud/textures/gui/`),
  generated as simple flat pictograms (heart, droplet, apple, bolt, thermometer) - not
  a copy of DayZ's actual art, just a clean minimalist set in the same spirit.

## About the Thirst Was Taken integration

Confirmed directly from `ThirstWasTaken-1.20.1-1.4.0.jar`:
- The mod's real id is **`thirst`** (not `thirstwastaken` - that's just the jar filename).
- Player thirst lives behind a real Forge capability:
  `dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST`
  (a `Capability<IThirst>`), with `IThirst.getThirst()` returning an int on a 0-20 scale
  (same as vanilla hunger).

`ThirstWasTakenCompat.java` still doesn't take a compile-time dependency on the mod (so
the project builds fine with or without it) - it looks up `ModCapabilities` and `IThirst`
by name via reflection, but everything else (`Capability`, `LazyOptional`, `getCapability`)
uses real Forge API classes we already depend on. If a future TWT release renames its
internal classes, the water gauge just falls back to vanilla saturation instead of
crashing - see the top-of-file comment in that class for where to look if that happens.

## Project layout

```
dayzhud/
├── build.gradle, settings.gradle, gradle.properties   - ForgeGradle build config
├── src/main/resources/
│   ├── META-INF/mods.toml                             - mod metadata + soft dep on "thirst"
│   └── assets/dayzhud/textures/gui/*.png               - the 5 status icons
└── src/main/java/com/dayzhud/mod/
    ├── DayzHudMod.java              - mod entrypoint
    ├── client/
    │   ├── ClientEvents.java        - registers the overlay
    │   ├── DayzHudOverlay.java      - all the actual HUD drawing
    │   ├── OverlayCanceller.java    - hides vanilla hunger/health/armor/air bars
    │   └── VitalsTracker.java       - stamina + temperature tracking
    └── compat/
        └── ThirstWasTakenCompat.java - thirst API hookup (see above)
```

## Building

Recommended: push this to a GitHub repo and let `.github/workflows/build.yml` build it
in the cloud (Actions tab -> download the `dayzhud-jar` artifact). No local Java/Gradle
setup needed.

To build locally instead: JDK 17 + `./gradlew build` (needs internet the first time to
pull the Forge MDK). Output lands in `build/libs/dayzhud-1.0.0.jar`.

## Tweaking the look

Position, size, spacing, and colors are plain constants near the top of
`DayzHudOverlay.java` - no config system needed, just edit and rebuild. To change an
icon's shape, replace its PNG under `src/main/resources/assets/dayzhud/textures/gui/`.
