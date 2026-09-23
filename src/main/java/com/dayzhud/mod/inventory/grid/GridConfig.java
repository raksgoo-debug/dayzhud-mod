package com.dayzhud.mod.inventory.grid;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * How big each item is, in grid cells, within the player's own INVENTORY grid and an opened
 * chest's grid. Nothing else (hotbar, loadout slots, armor, worn backpack, corpse loot bag)
 * is affected by this - see the class doc on {@link ItemGrid} for the scope this was built
 * against and why.
 */
public final class GridConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_TYPE_FOOTPRINTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_ID_FOOTPRINTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_FOOTPRINTS;

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
                                "pistol=2x1", "smg=3x2", "rifle=4x2", "shotgun=4x1",
                                "sniper=5x1", "mg=5x1", "rpg=4x2"),
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
        b.pop();
        SPEC = b.build();
    }

    private GridConfig() {}
}
