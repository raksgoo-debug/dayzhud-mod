# DayZ HUD (Forge 1.20.1)

Pinned to **Forge 47.4.20** and built against **Thirst Was Taken 1.20.1-1.4.0** (the
latest 1.20.1 release as of writing) - if you're on a different TWT version, the
compat lookup should still work since it's reflection-based, but the class-name fix
described below is more likely to be needed on older/newer releases.

Replaces the vanilla hunger/health/armor/air bars with a DayZ-style vertical status
stack in the bottom-left corner: **Health, Water, Food, Stamina, Temperature**, each
shown as a small icon + percentage, color-coded by severity (white/grey = fine,
amber = low, red = critical).

- **Water** reads from *Thirst Was Taken* if it's installed, and falls back to vanilla
  food saturation if it isn't - so the mod works fine standalone too.
- **Stamina** and **Temperature** are custom additions (vanilla has neither): stamina
  drains on sprint/jump and regenerates when idle; temperature is a smoothed read of
  the biome you're standing in (colder in water, hot in lava/fire/Nether).
- All icons are drawn procedurally (small pixel grids in code) - no texture assets to
  manage, and nothing copied from DayZ's actual art.

## Project layout

```
dayzhud/
├── build.gradle, settings.gradle, gradle.properties   - ForgeGradle build config
├── src/main/resources/META-INF/mods.toml              - mod metadata + soft dep on TWT
└── src/main/java/com/dayzhud/mod/
    ├── DayzHudMod.java              - mod entrypoint
    ├── client/
    │   ├── ClientEvents.java        - registers the overlay
    │   ├── DayzHudOverlay.java      - all the actual HUD drawing
    │   ├── OverlayCanceller.java    - hides vanilla hunger/health/armor/air bars
    │   └── VitalsTracker.java       - stamina + temperature tracking
    └── compat/
        └── ThirstWasTakenCompat.java - reflection-based soft dep on Thirst Was Taken
```

## Building

You'll need a JDK 17 and an internet connection (Gradle needs to pull the Forge MDK
and dependencies the first time).

1. Unzip this project.
2. Open a terminal in the project root.
3. Run `./gradlew build` (or `gradlew.bat build` on Windows) - **note:** this project
   doesn't include the Gradle wrapper jar itself (binary file), so first run
   `gradle wrapper --gradle-version 8.1.1` if you have Gradle installed globally, or
   open the folder in IntelliJ IDEA with the Forge/Gradle plugins and let it generate
   the wrapper for you. Either way works.
4. The built mod jar lands in `build/libs/dayzhud-1.0.0.jar`.
5. To test without building a jar: `./gradlew runClient` launches a dev client with the
   mod loaded - install Thirst Was Taken's jar into `run/mods/` first if you want to
   test that integration.

## About the Thirst Was Taken integration

Thirst Was Taken doesn't ship a stable public API, so `ThirstWasTakenCompat` finds its
data via reflection instead of a compile-time dependency - this means the project
builds identically whether or not you have that mod's jar around, and won't break if
the other mod updates its internal class names (it just falls back to vanilla
saturation instead of crashing).

If the water gauge shows vanilla saturation instead of real thirst on your setup, the
guessed class/method names in `ThirstWasTakenCompat.java` didn't match your installed
version. The file has step-by-step comments on how to open the installed jar in a
decompiler and correct the three constants near the top (`CANDIDATE_CAPABILITY_CLASSES`,
`CANDIDATE_GET_METHODS`, `CANDIDATE_MAX_METHODS`) to match what you find.

## Tweaking the look

Everything about the HUD's position, size, colors, and thresholds is in
`DayzHudOverlay.java` as plain constants/if-statements near the top and in
`severityColor()`/`tempColor()` - no config system needed, just edit and rebuild.
