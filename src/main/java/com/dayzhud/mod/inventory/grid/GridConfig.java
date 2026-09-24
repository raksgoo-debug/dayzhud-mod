package com.dayzhud.mod.inventory.grid;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * How big each item is, in grid cells - within the player's own INVENTORY and worn BACKPACK,
 * an opened container, and (viewing a corpse) its inventory, hotbar and loot bag - plus how
 * a multi-cell item's bigger icon is tilted when drawn. Not the loadout slots (PRIMARY etc.):
 * those are always exactly one item regardless of footprint, but they do share the tilt
 * angle below, since the same "is it a big 3D model looking sheared" problem applies there
 * too.
 */
public final class GridConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_TYPE_FOOTPRINTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_ID_FOOTPRINTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_FOOTPRINTS;
    public static final ForgeConfigSpec.DoubleValue FLAT_ITEM_ANGLE_X;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ForgeConfigSpec.BooleanValue FLAT_GUN_RENDER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("grid");
        ENABLED = b.comment(
                        "Master switch for multi-cell items. Off makes every item exactly 1x1,",
                        "as before - nothing else in this section does anything while this is",
                        "false.",
                        "The lists below are parsed once, on first use, and cached - same trap",
                        "as maskEmptySlots elsewhere in this config: an edit to an existing",
                        "world's config needs a restart to take effect.")
                .define("enabled", true);
        GUN_TYPE_FOOTPRINTS = b.comment(
                        "Default size for a TACZ gun by its type, as \"type=WxH\" (width is the",
                        "horizontal dimension). A gunIdFootprints entry below takes priority",
                        "over this for a specific gun.")
                .defineList("gunTypeFootprints", List.of(
                                "pistol=2x1", "smg=3x2", "rifle=4x2", "shotgun=4x2",
                                "sniper=5x2", "mg=5x2", "rpg=4x2"),
                        o -> o instanceof String);
        GUN_ID_FOOTPRINTS = b.comment(
                        "Per-gun size overrides, as \"modid:gunid=WxH\" - this is the id",
                        "TaczMarketCompat.gunIdOf reports, NOT the item id (every TACZ gun is",
                        "the same item with different NBT). Empty by default; add an entry",
                        "here for any specific gun whose real size doesn't match its type's",
                        "default above.")
                .defineList("gunIdFootprints", List.of(), o -> o instanceof String);
        ITEM_FOOTPRINTS = b.comment(
                        "Plain-item size overrides, as \"modid:itemid=WxH\". For anything that",
                        "isn't a TACZ gun - backpacks, meds, resources, tools. Empty by default;",
                        "anything not listed here or above stays 1x1.")
                .defineList("itemFootprints", List.of(), o -> o instanceof String);
        FLAT_ITEM_ANGLE_X = b.comment(
                        "Extra tilt (degrees, around the X axis) applied when drawing a big",
                        "item icon - in the grid, and in the loadout boxes - on top of",
                        "whatever angle it normally renders at, tipping it toward a top-down",
                        "view.",
                        "Defaults to 0 (off). It was briefly 55 by default; reverted after a",
                        "report that big icons were only showing at their normal small size -",
                        "the likely cause is this: the icon is scaled non-uniformly (stretched",
                        "to fill a wide, short rectangle) AFTER being tilted in 3D, and that",
                        "combination can render a thin, barely-visible sliver rather than a",
                        "flattened gun, leaving only vanilla's own untouched small icon",
                        "actually visible. Left in as an opt-in rather than removed, since the",
                        "non-uniform-stretch part of that reasoning is a guess too - if you",
                        "turn this on and it looks fine, that guess was wrong. Nudge up toward",
                        "90 for more top-down; the config reloads live, no restart needed.")
                .defineInRange("flatItemAngleX", 0.0, 0.0, 90.0);
        FLAT_GUN_RENDER = b.comment(
                        "Draw TACZ guns in the grid as their real 3D model, side-on with the",
                        "barrel pointing left, sized to fit their footprint without stretching.",
                        "Off falls back to the ordinary inventory icon, fitted the same way.",
                        "Also switches itself off for the session if it ever throws, with one",
                        "warning in the log.")
                .define("flatGunRender", true);
        DEBUG_LOGGING = b.comment(
                        "Logs one line per multi-cell pickup, placement, and big-icon draw -",
                        "menu slot index, region, footprint. Off by default; it repeats every",
                        "frame once something is on screen; turn on only for a short test.")
                .define("debugLogging", false);
        b.pop();
        SPEC = b.build();
    }

    private GridConfig() {}
}
