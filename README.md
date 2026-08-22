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
│   ├── META-INF/mods.toml                             - mod metadata + deps (Curios mandatory, "thirst" soft)
│   └── assets/dayzhud/textures/gui/*.png               - HUD icons
└── src/main/java/com/dayzhud/mod/
    ├── DayzHudMod.java              - mod entrypoint
    ├── client/
    │   ├── ClientEvents.java        - registers the overlay
    │   ├── DayzHudOverlay.java      - all the actual HUD drawing
    │   ├── OverlayCanceller.java    - hides vanilla hunger/health/armor/air bars
    │   └── VitalsTracker.java       - stamina + temperature tracking
    ├── compat/
    │   └── ThirstWasTakenCompat.java - thirst API hookup (see above)
    └── inventory/                   - the Tarkov-style inventory screen (see below)
```

## The Tarkov-style inventory screen

Pressing E (in Survival/Adventure - Creative keeps the normal screen) opens a custom dark
paperdoll-style inventory instead of vanilla's: armor slots, three new Curios slots this
mod defines (**Face Cover**, **Headset**, **Chest Rig** - the chest rig slot only shows/
works once a chestplate is equipped), a live 3D player preview, read-only previews of
hotbar slots 0-3 as **Primary/Secondary/Holster/Sheath** (these mirror the hotbar rather
than being separate storage - dragging items happens via the hotbar as normal), the
standard inventory as a **Pockets** grid, and a bottom stat strip reusing this mod's own
health/food/water tracking.

It also automatically lays out **any other Curios slot type** other installed mods
register (e.g. rings, belts, backpacks from content packs) in an overflow row, so nothing
new another mod adds is silently hidden - it just shows up.

**This required adding Curios as a mandatory dependency** (`curios_version` in
gradle.properties, currently `5.12.1+1.20.1`) - the mod won't load without it. If your
instance is on a different Curios version, this number probably needs updating to match.

### Honest risk areas for this feature specifically

This is a much bigger, more architecturally involved feature than the HUD (custom
Menu/Screen, real client-server networking, a third-party API). A few spots are genuinely
uncertain until it's actually compiled and I've marked them in-code with `// Risk area`
style comments explaining exactly what to check if the build fails there:

- **`TarkovInventoryMenu.java`** - the two Curios interface imports
  (`ICuriosItemHandler`, `ICurioStacksHandler`) - package path may have shifted between
  Curios versions.
- **`TarkovInventoryScreen.java`** - the `InventoryScreen.renderEntityInInventoryFollowsMouse(...)`
  call for the player paperdoll - this vanilla helper's exact parameter list has changed
  across Minecraft versions before.
- **`TarkovInventoryClientEvents.java`** - `ScreenEvent.Opening`'s accessor method names
  for the incoming screen.

If the build fails, paste me the error the same way you have been - these are contained,
fixable spots, not a sign the overall approach is wrong.

## Corpse looting (Ragdollified)

When a Ragdollified player corpse is opened, `CorpseOpenRedirect` swaps its looting screen
for the merged view - your own loadout panel on the left, the corpse's armor, curios, gear,
inventory, hotbar and backpack laid out down the right-hand column.

**Ragdollified 1.0.0-RELEASE split the corpse feature into its own addon.** The looting
code is no longer part of `ragdollified`; it now lives in **Ragdollified Player Corpse**
(`ragdollifiedpc`), which depends on the core mod. That moved the menu class:

| build | modid | menu class |
|---|---|---|
| 0.9.x-BETA and earlier | `ragdollified` | `com.raiiiden.ragdollified.menu.CorpseMenu` |
| 1.0.0-RELEASE and later | `ragdollifiedpc` | `com.raiiiden.ragdollifiedpc.menu.CorpseMenu` |

The redirect matches **both** class names, so it works on either. Both mods stay optional -
nothing here compiles against them; the menu is matched by name and its `corpse` /
`curioIds` / `corpseSlots` fields are read reflectively.

The corpse container's slot layout was re-verified against the new addon's jar and is
**unchanged** by the split: `41 + curioCount` slots, laid out `0-8` hotbar, `9-35` main
inventory, `36-39` armor (feet-first), `40` offhand, `41+` one per curio slot. The redirect
compares the container's real size against that layout before touching anything and stands
down if they disagree, so a future layout change degrades to Ragdollified's own screen
rather than misplacing loot.

The loot column's header now shows the corpse's own name ("STEVE'S CORPSE") - recovered by
matching the open container against the nearby corpse entity, since the open event hands us
the menu rather than the provider that named it. It falls back to "CORPSE" if the entity
can't be read.

**Not carried over:** the addon's own screen has `TAKE ALL` and `SWAP` buttons that the
merged view doesn't reproduce - replacing the menu replaces its buttons too. Everything is
still reachable by hand (and by shift-click).

## Building

Recommended: push this to a GitHub repo and let `.github/workflows/build.yml` build it
in the cloud (Actions tab -> download the `dayzhud-jar` artifact). No local Java/Gradle
setup needed.

To build locally instead: JDK 17 + `./gradlew build` (needs internet the first time to
pull the Forge MDK). Output lands in `build/libs/dayzhud-1.0.0.jar`.

## Tweaking the look

HUD position, size, spacing, and colors are plain constants near the top of
`DayzHudOverlay.java` - no config system needed, just edit and rebuild. To change an
icon's shape, replace its PNG under `src/main/resources/assets/dayzhud/textures/gui/`.

Inventory screen slot positions, panel colors, and which hotbar slots map to
Primary/Secondary/Holster/Sheath are all plain constants near the top of
`TarkovInventoryScreen.java` and in `TarkovInventoryMenu.java`'s constructor.
