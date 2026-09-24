package com.dayzhud.mod.inventory.grid;

import java.util.Map;

/**
 * Built-in footprints for non-gun items, beside DefaultGunFootprints. Like it, these are data
 * both sides share (the server decides what fits, and never loads models).
 *
 * <b>LR_VARIANTS</b> - LR Tactical (and content packs for it, e.g. Apocalyptic Arsenal) uses a
 * few generic items whose variant lives in NBT ("MeleeWeaponId" / "ConsumableId" /
 * "ThrowableId"), so these are keyed by that variant id. Sized offline, from each variant's
 * own model in lrtactical-1.20.1-0.4.3 + apocalypticarsenal-1.0.1, with the same rule and
 * scale as the guns - 9 model units per cell, length x the second-longest side, rounded up
 * with 0.25 slack - and melee weapons at least 2 wide so a knife isn't a single cell.
 *
 * <b>ITEMS</b> - plain items keyed by registry id. TaCZ: Magazines has two items; the regular
 * magazine is 2x1, the small (pistol) one stays 1x1.
 *
 * Config entries (gunIdFootprints / itemFootprints) override both tables.
 */
public final class DefaultItemFootprints {

    public static final Map<String, Footprint> LR_VARIANTS = Map.ofEntries(
            Map.entry("lrtactical:ai2", new Footprint(1, 1)),
            Map.entry("lrtactical:amoxycillin", new Footprint(1, 1)),
            Map.entry("lrtactical:baseball_bat", new Footprint(4, 1)),
            Map.entry("lrtactical:blood_pack", new Footprint(2, 1)),
            Map.entry("lrtactical:c4", new Footprint(1, 1)),
            Map.entry("lrtactical:carfak", new Footprint(1, 1)),
            Map.entry("lrtactical:cms", new Footprint(1, 1)),
            Map.entry("lrtactical:condensed_milk", new Footprint(1, 1)),
            Map.entry("lrtactical:dagger", new Footprint(2, 1)),
            Map.entry("lrtactical:fire_axe", new Footprint(4, 2)),
            Map.entry("lrtactical:flash_grenade", new Footprint(1, 1)),
            Map.entry("lrtactical:goldenstar", new Footprint(1, 1)),
            Map.entry("lrtactical:hardened_katana", new Footprint(5, 2)),
            Map.entry("lrtactical:ibuprofen", new Footprint(1, 1)),
            Map.entry("lrtactical:karambit", new Footprint(2, 1)),
            Map.entry("lrtactical:m67", new Footprint(1, 1)),
            Map.entry("lrtactical:molotov", new Footprint(2, 1)),
            Map.entry("lrtactical:rgn", new Footprint(1, 1)),
            Map.entry("lrtactical:smoke_grenade", new Footprint(1, 1)),
            Map.entry("lrtactical:surv12", new Footprint(2, 1)),
            Map.entry("lrtactical:vaseline", new Footprint(1, 1)),
            Map.entry("lrtactical:wooden_baseball_bat", new Footprint(4, 1))
    );

    public static final Map<String, Footprint> ITEMS = Map.ofEntries(
            Map.entry("taczmagazines:magazine", new Footprint(2, 1)),
            Map.entry("taczmagazines:magazine_small", new Footprint(1, 1))
    );

    /** Each LR variant's length in model units - the shared-scale reference, as for guns. */
    public static final Map<String, Float> LR_LENGTH = Map.ofEntries(
            Map.entry("lrtactical:ai2", 3.3f),
            Map.entry("lrtactical:amoxycillin", 4.6f),
            Map.entry("lrtactical:baseball_bat", 31.2f),
            Map.entry("lrtactical:blood_pack", 11.5f),
            Map.entry("lrtactical:c4", 6.8f),
            Map.entry("lrtactical:carfak", 7.8f),
            Map.entry("lrtactical:cms", 9.3f),
            Map.entry("lrtactical:condensed_milk", 6.0f),
            Map.entry("lrtactical:dagger", 9.7f),
            Map.entry("lrtactical:fire_axe", 32.6f),
            Map.entry("lrtactical:flash_grenade", 7.3f),
            Map.entry("lrtactical:goldenstar", 2.0f),
            Map.entry("lrtactical:hardened_katana", 42.8f),
            Map.entry("lrtactical:ibuprofen", 3.8f),
            Map.entry("lrtactical:karambit", 7.8f),
            Map.entry("lrtactical:m67", 6.3f),
            Map.entry("lrtactical:molotov", 12.7f),
            Map.entry("lrtactical:rgn", 6.9f),
            Map.entry("lrtactical:smoke_grenade", 7.6f),
            Map.entry("lrtactical:surv12", 12.8f),
            Map.entry("lrtactical:vaseline", 3.0f),
            Map.entry("lrtactical:wooden_baseball_bat", 31.2f)
    );

    private DefaultItemFootprints() {}
}
