package com.dayzhud.mod.injury;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Bleeding and pain (2.15.0): config/dayzhud-injuries.toml. */
public final class InjuryConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue BLEED_CHANCE;
    public static final ForgeConfigSpec.DoubleValue HEAVY_BLEED_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue LIGHT_BLEED_HP;
    public static final ForgeConfigSpec.DoubleValue HEAVY_BLEED_HP;
    public static final ForgeConfigSpec.IntValue LIGHT_CLOT_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PAIN_PER_DAMAGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLEED_TREATMENTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PAIN_TREATMENTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("injuries");
        ENABLED = b.comment(
                        "Bleeding and pain on top of First Aid's per-limb health. Hits from mobs,",
                        "players, projectiles and explosions can open a wound; bleeding drains health",
                        "until treated; damage causes pain, which weakens you until it fades or a",
                        "painkiller suppresses it.")
                .define("enabled", true);
        BLEED_CHANCE = b.comment(
                        "Chance to start bleeding per point of damage taken (0.08 = a 5-damage hit",
                        "bleeds 40% of the time; capped at 90%).")
                .defineInRange("bleedChancePerDamage", 0.08, 0.0, 1.0);
        HEAVY_BLEED_DAMAGE = b.comment(
                        "A single hit of at least this much damage can cause HEAVY bleeding instead.")
                .defineInRange("heavyBleedDamage", 7.0, 1.0, 100.0);
        LIGHT_BLEED_HP = b.comment(
                        "Health lost per second per light wound.")
                .defineInRange("lightBleedHpPerSecond", 0.06, 0.0, 5.0);
        HEAVY_BLEED_HP = b.comment(
                        "Health lost per second per heavy wound.")
                .defineInRange("heavyBleedHpPerSecond", 0.25, 0.0, 5.0);
        LIGHT_CLOT_SECONDS = b.comment(
                        "A light wound stops on its own after roughly this long (seconds, +/-50%).",
                        "Heavy wounds never do. 0 = light wounds never stop on their own either.")
                .defineInRange("lightClotSeconds", 180, 0, 3600);
        PAIN_PER_DAMAGE = b.comment(
                        "Pain added per point of damage taken (pain runs 0-100; 30+ and 60+ are the",
                        "two levels with effects).")
                .defineInRange("painPerDamage", 5.0, 0.0, 100.0);
        BLEED_TREATMENTS = b.comment(
                        "Items that stop bleeding when used, as \"id=light\" (stops light wounds) or",
                        "\"id=heavy\" (stops all bleeding). For items that keep their variant in NBT",
                        "use the market's key format, e.g. lrtactical:consumable/lrtactical:carfak.",
                        "Treated when the item finishes being used - its own effect still happens.")
                .defineList("bleedTreatments", List.of(
                        "tarkovdayz:bandgecivil=light",
                        "tarkovdayz:bandge=light",
                        "tarkovdayz:salewafirstaidkit=heavy",
                        "tarkovdayz:grizzly=heavy",
                        "lrtactical:consumable/lrtactical:carfak=heavy",
                        "lrtactical:consumable/lrtactical:cms=heavy",
                        "lrtactical:consumable/lrtactical:surv12=heavy"
                ), o -> o instanceof String s && s.contains("="));
        PAIN_TREATMENTS = b.comment(
                        "Items that suppress pain when used, as \"id=seconds\" of relief.")
                .defineList("painTreatments", List.of(
                        "tarkovdayz:painkillers=240",
                        "tarkovdayz:ibuprofen=480",
                        "tarkovdayz:morphine=900",
                        "lrtactical:consumable/lrtactical:ibuprofen=480",
                        "lrtactical:consumable/lrtactical:goldenstar=300",
                        "lrtactical:consumable/lrtactical:vaseline=480",
                        "fieldkit:syringe_propital=600",
                        "fieldkit:syringe_adrenaline=120",
                        "firstaid:morphine=600"
                ), o -> o instanceof String s && s.contains("="));
        b.pop();
        SPEC = b.build();
    }

    private InjuryConfig() {}
}
