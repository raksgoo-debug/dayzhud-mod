package com.dayzhud.mod.gunfire;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Distant gunfire (2.19.0): config/dayzhud-gunfire.toml.
 *
 * COMMON so a server and its clients read one file: the server decides who is in range,
 * each client decides how loud it is and how it echoes.
 */
public final class GunfireConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue DISTANT_FROM;
    public static final ForgeConfigSpec.DoubleValue RANGE;
    public static final ForgeConfigSpec.DoubleValue SUPPRESSED_RANGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_RANGES;
    public static final ForgeConfigSpec.DoubleValue VOLUME;
    public static final ForgeConfigSpec.DoubleValue SPEED_OF_SOUND;
    public static final ForgeConfigSpec.BooleanValue ECHOES;
    public static final ForgeConfigSpec.BooleanValue LOUDNESS_BY_CALIBRE;
    public static final ForgeConfigSpec.BooleanValue OCCLUSION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("gunfire");
        ENABLED = b.comment(
                        "TACZ gunshots heard from far away, from where they were fired: each gun's own",
                        "third-person sound (so a resource pack's custom sounds are what you hear), delayed",
                        "by the speed of sound, quieter and darker with distance, with echoes off the",
                        "terrain and buildings around you.")
                .define("enabled", true);
        DISTANT_FROM = b.comment(
                        "TACZ's own gunshot covers nearby players; the distant sound takes over from about",
                        "this many blocks (it fades in from 60% of it, so the two overlap a little).")
                .defineInRange("distantFrom", 48.0, 8.0, 512.0);
        RANGE = b.comment(
                        "How far an unsuppressed gunshot carries (blocks), for an intermediate rifle",
                        "cartridge. With loudnessByCalibre, pistols carry x0.55 of this, shotguns x0.85,",
                        "full-power rifles x1.35 and heavy calibres (.50 BMG, .338) x2.")
                .defineInRange("range", 640.0, 16.0, 4096.0);
        SUPPRESSED_RANGE = b.comment(
                        "How far a suppressed gunshot carries (blocks); scaled by calibre too, up to x1.5.")
                .defineInRange("suppressedRange", 128.0, 0.0, 4096.0);
        GUN_RANGES = b.comment(
                        "Per-gun range overrides as \"gunId=blocks\", e.g. \"tacz:m107=1200\" for a .50 or",
                        "\"tacz:glock_17=320\" for a pistol. Suppressed shots still use suppressedRange.")
                .defineList("gunRanges", List.of(), o -> o instanceof String s && s.contains("="));
        VOLUME = b.comment(
                        "Loudness of a distant shot at distantFrom; it falls by half (-6 dB) every time",
                        "the distance doubles. Client side.")
                .defineInRange("volume", 0.7, 0.0, 1.0);
        SPEED_OF_SOUND = b.comment(
                        "Blocks per second. A shot 343 blocks away is heard a second after it's fired.",
                        "0 = no delay. Client side.")
                .defineInRange("speedOfSound", 343.0, 0.0, 10000.0);
        ECHOES = b.comment(
                        "Echoes off walls, cliffs and buildings around you (or a long rolling echo in open",
                        "country). Client side.")
                .define("echoes", true);
        LOUDNESS_BY_CALIBRE = b.comment(
                        "Louder and farther-carrying the bigger the cartridge, from each gun's TACZ ammo id",
                        "(then its type, then its damage): pistol x0.7 volume, intermediate rifle x1,",
                        "full-power rifle x1.25, heavy x1.7. Also scales how far mobs hear it. The log",
                        "names the class each gun got.")
                .define("loudnessByCalibre", true);
        OCCLUSION = b.comment(
                        "Shots with solid terrain or walls between you and the shooter are quieter and",
                        "duller, the more so the more is in the way. Echoes still come through. Client side.")
                .define("occlusion", true);
        b.pop();
        SPEC = b.build();
    }

    private GunfireConfig() {}
}
