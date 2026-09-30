package com.dayzhud.mod.weight;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Carried weight (2.15.0): config/dayzhud-weight.toml. */
public final class WeightConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue OVERWEIGHT;
    public static final ForgeConfigSpec.DoubleValue HEAVY;
    public static final ForgeConfigSpec.DoubleValue CRITICAL;
    public static final ForgeConfigSpec.DoubleValue ENDURANCE_BONUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("weight");
        ENABLED = b.comment(
                        "Everything you carry weighs something, and too much slows you down -",
                        "inventory, hotbar, armour, worn gear, your backpack's contents and the",
                        "secure container. TACZ guns and attachments use their own data weights (kg).")
                .define("enabled", true);
        OVERWEIGHT = b.comment(
                        "Above this (kg) you walk slower and sprinting drains stamina faster.")
                .defineInRange("overweightKg", 25.0, 1.0, 500.0);
        HEAVY = b.comment(
                        "Above this (kg) you can't sprint, and walk slower still.")
                .defineInRange("heavyKg", 45.0, 1.0, 500.0);
        CRITICAL = b.comment(
                        "Above this (kg) you barely move.")
                .defineInRange("criticalKg", 60.0, 1.0, 500.0);
        ENDURANCE_BONUS = b.comment(
                        "kg added to all three limits per Endurance skill level.")
                .defineInRange("enduranceBonusKg", 1.5, 0.0, 20.0);
        OVERRIDES = b.comment(
                        "Per-item weights, as \"modid:item=kg\" (per single item). Anything not",
                        "listed uses the built-in rules: TACZ data weights for guns and attachments,",
                        "armour by its protection, 1.5 kg for a backpack, 0.4 kg per grid cell for",
                        "other unstackable items, and a stack of 64 of anything weighing 1 kg.")
                .defineList("overrides", List.of(), o -> o instanceof String s && s.contains("="));
        b.pop();
        SPEC = b.build();
    }

    private WeightConfig() {}
}
