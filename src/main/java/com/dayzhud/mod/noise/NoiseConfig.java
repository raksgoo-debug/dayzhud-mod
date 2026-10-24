package com.dayzhud.mod.noise;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Noise (2.19.0): config/dayzhud-noise.toml.
 *
 * COMMON, like the mod's other configs, so the client reads the same footstep radii the
 * server uses and the HUD's noise meter matches what mobs actually hear.
 */
public final class NoiseConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue MOBS_INVESTIGATE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_MARKERS;
    public static final ForgeConfigSpec.DoubleValue PLAYER_HEARING;

    public static final ForgeConfigSpec.DoubleValue GUNSHOT;
    public static final ForgeConfigSpec.DoubleValue SUPPRESSED_GUNSHOT;
    public static final ForgeConfigSpec.DoubleValue EXPLOSION;
    public static final ForgeConfigSpec.DoubleValue GLASS;
    public static final ForgeConfigSpec.DoubleValue BOW;
    public static final ForgeConfigSpec.DoubleValue SPRINT;
    public static final ForgeConfigSpec.DoubleValue WALK;
    public static final ForgeConfigSpec.DoubleValue CROUCH;

    public static final ForgeConfigSpec.IntValue INVESTIGATE_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_MOBS_PER_NOISE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SUPPRESSOR_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEAF_MOBS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("noise");
        ENABLED = b.comment(
                        "Noise, Project Zomboid style. Gunshots, explosions, breaking glass, bows and",
                        "footsteps make noise with a radius (in blocks). Hostile mobs inside it walk over",
                        "to investigate, and other players inside it see which way it came from.")
                .define("enabled", true);
        MOBS_INVESTIGATE = b.comment(
                        "Hostile mobs with no target walk to noises they hear.")
                .define("mobsInvestigate", true);
        PLAYER_MARKERS = b.comment(
                        "Players who hear an explosion or breaking glass get a direction marker",
                        "around the crosshair (direction and rough distance only - never coordinates).")
                .define("playerMarkers", true);
        PLAYER_HEARING = b.comment(
                        "How far players hear noises, as a multiple of each noise's radius.")
                .defineInRange("playerHearingMultiplier", 1.5, 0.0, 10.0);

        b.push("radius");
        GUNSHOT = b.comment("A gunshot (TACZ).").defineInRange("gunshot", 80.0, 0.0, 512.0);
        SUPPRESSED_GUNSHOT = b.comment("A gunshot with a suppressor fitted (TACZ).")
                .defineInRange("suppressedGunshot", 20.0, 0.0, 512.0);
        EXPLOSION = b.comment("Any explosion: TNT, creepers, grenades.").defineInRange("explosion", 96.0, 0.0, 512.0);
        GLASS = b.comment("A player breaking glass or a glass pane.").defineInRange("glassBreak", 24.0, 0.0, 512.0);
        BOW = b.comment("Loosing an arrow from a bow.").defineInRange("bow", 6.0, 0.0, 512.0);
        SPRINT = b.comment("Footsteps while sprinting.").defineInRange("sprint", 14.0, 0.0, 128.0);
        WALK = b.comment("Footsteps while walking.").defineInRange("walk", 6.0, 0.0, 128.0);
        CROUCH = b.comment("Footsteps while sneaking or crawling (0 = silent).").defineInRange("crouch", 0.0, 0.0, 128.0);
        b.pop();

        INVESTIGATE_SECONDS = b.comment(
                        "How long a mob keeps heading for a noise before giving up.")
                .defineInRange("investigateSeconds", 30, 1, 600);
        MAX_MOBS_PER_NOISE = b.comment(
                        "At most this many mobs (the nearest) react to one noise. Keeps a gunfight in a",
                        "crowded area from pathfinding hundreds of mobs at once.")
                .defineInRange("maxMobsPerNoise", 24, 0, 512);
        SUPPRESSOR_KEYWORDS = b.comment(
                        "A TACZ gun counts as suppressed when its muzzle attachment's id contains one of",
                        "these words.")
                .defineList("suppressorKeywords", List.of("silencer", "suppressor"),
                        o -> o instanceof String);
        DEAF_MOBS = b.comment(
                        "Mob ids that never react to noise, e.g. \"minecraft:enderman\".")
                .defineList("deafMobs", List.of("minecraft:enderman", "minecraft:warden"),
                        o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private NoiseConfig() {}
}
