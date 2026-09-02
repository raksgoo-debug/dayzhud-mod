# dayzhud 1.1.0 - trader market + bottled water

**This archive contains ONLY new and changed files.** Unzip it over the repo root; nothing
else in the tree needs to move. No files are deleted or renamed by this update.

## New (32 files)

    src/main/java/com/dayzhud/mod/market/          21 files - the whole market system
    src/main/java/com/dayzhud/mod/item/WaterBottleItem.java
    src/main/java/com/dayzhud/mod/registry/ModItems.java
    src/main/java/com/dayzhud/mod/registry/ModCreativeTabs.java
    src/main/resources/data/dayzhud/market/prices.json
    src/main/resources/assets/dayzhud/models/item/water_bottle.json
    src/main/resources/assets/dayzhud/textures/item/water_bottle.png

## Changed (8 files) - what changed in each

    DayzHudMod.java          registers ModItems, ModCreativeTabs and the COMMON config
    inventory/NetworkHandler.java   six market packets APPENDED to the list (ids are
                                    positional - nothing before them is renumbered)
    inventory/TarkovMenuTypes.java  adds the MARKET menu type
    client/ClientEvents.java        FMLClientSetupEvent binds MarketScreen to MARKET
    client/DayzHudOverlay.java      draws the rouble balance above the status row
    compat/ThirstWasTakenCompat.java  adds quench(); the new setters resolve separately and
                                      are allowed to fail, so the gauge still works without them
    src/main/resources/META-INF/mods.toml   optional tarkovdayz + tacz dependencies
    src/main/resources/assets/dayzhud/lang/en_us.json   19 new keys (existing ones untouched)
    gradle.properties        mod_version 1.0.0 -> 1.1.0

## After updating

`config/dayzhud-common.toml` is created on first run. Forge only writes defaults into a
config file that does not exist yet, so if you already have one, delete it or hand-edit it to
pick up the market section.

## Verification

Syntax and cross-class references checked with a JDK parse pass over the merged tree
(74 source files). Minecraft and Forge are not on the classpath here, so nothing that touches
their APIs is verified - a clean local pass proves the mod's own calls and the syntax and
nothing more. Build through the repo's GitHub Actions workflow; that is the real check.
