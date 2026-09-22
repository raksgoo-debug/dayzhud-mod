package com.dayzhud.mod.inventory;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Which TACZ gun types (and which item tag, for melee) each of the four typed weapon slots
 * accepts.
 *
 * A list of strings rather than a fixed enum set, so a pack with a different gun mod, or a
 * different idea of what counts as a sidearm, can retune this without a recompile - the same
 * reasoning as agonyAllowlist and alwaysGibGunTypes elsewhere in this project.
 *
 * TACZ's own type strings are "pistol", "smg", "rifle", "sniper", "shotgun", "mg", "rpg"
 * (see TaczMarketCompat / CommonGunIndex.getType()).
 */
public final class WeaponSlotConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PRIMARY_GUN_TYPES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SECONDARY_GUN_TYPES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOLSTER_GUN_TYPES;
    public static final ForgeConfigSpec.ConfigValue<String> SHEATH_TAG;
    public static final ForgeConfigSpec.BooleanValue ENFORCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("weaponSlots");
        ENFORCE = b.comment(
                        "Restrict what can go in the PRIMARY/SECONDARY/HOLSTER/SHEATH slots at all.",
                        "Off makes them plain unrestricted slots again (still positioned/drawn as",
                        "the loadout row, just no type checking).")
                .define("enforce", true);
        PRIMARY_GUN_TYPES = b.comment(
                        "TACZ gun types allowed in the PRIMARY slot.")
                .defineList("primaryGunTypes",
                        List.of("rifle", "smg", "shotgun", "sniper", "mg", "rpg"),
                        o -> o instanceof String);
        SECONDARY_GUN_TYPES = b.comment(
                        "TACZ gun types allowed in the SECONDARY slot. Same set as primary by",
                        "default - it is a second general-purpose weapon slot, not a second holster.")
                .defineList("secondaryGunTypes",
                        List.of("rifle", "smg", "shotgun", "sniper", "mg", "rpg"),
                        o -> o instanceof String);
        HOLSTER_GUN_TYPES = b.comment(
                        "TACZ gun types allowed in the HOLSTER slot.")
                .defineList("holsterGunTypes", List.of("pistol"), o -> o instanceof String);
        SHEATH_TAG = b.comment(
                        "Item tag (id:path, no leading #) whose members may go in the SHEATH slot.",
                        "Never a TACZ gun regardless of this tag - sheath is melee-only. Ship your",
                        "own knife/machete items into this tag from a datapack; the default only",
                        "covers vanilla swords and the trident.")
                .define("sheathTag", "dayzhud:sheath_weapons");
        b.pop();
        SPEC = b.build();
    }

    private WeaponSlotConfig() {}
}
