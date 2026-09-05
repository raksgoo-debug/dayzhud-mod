package com.dayzhud.mod.search;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings for the built-in container search.
 *
 * This is our own implementation rather than a compat layer over another searching mod. The
 * reason is concrete: the previous approach had to translate a container's own slot numbering
 * into our merged menu's numbering across a menu swap, and the two never lined up - a corpse's
 * slot 3 was painted onto the player's fourth armour slot. Here the hiding happens at the
 * CONTAINER level (see SearchedContainer), so there is only ever one numbering and nothing to
 * translate.
 */
public final class SearchConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue TICKS_PER_SLOT;
    public static final ForgeConfigSpec.IntValue INITIAL_DELAY_TICKS;
    public static final ForgeConfigSpec.BooleanValue SEARCH_CORPSES;
    public static final ForgeConfigSpec.BooleanValue SEARCH_CONTAINERS;
    public static final ForgeConfigSpec.BooleanValue MASK_EMPTY;
    public static final ForgeConfigSpec.BooleanValue SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("search");
        ENABLED = b.comment("Hide a container's contents until the player has searched it.")
                .define("enabled", true);
        INITIAL_DELAY_TICKS = b.comment(
                        "Pause before the first slot is revealed, in ticks. Without it the first",
                        "item appears the instant the screen opens, which reads as a glitch.")
                .defineInRange("initialDelayTicks", 10, 0, 200);
        TICKS_PER_SLOT = b.comment(
                        "Ticks between one revealed slot and the next. Low by default because",
                        "with empty slots covered too there are forty-odd of them on a corpse.")
                .defineInRange("ticksPerSlot", 5, 1, 200);
        MASK_EMPTY = b.comment(
                        "Cover EVERY slot until it is searched, empty ones included.",
                        "On, because covering only the occupied slots draws a map of the loot:",
                        "the hatching itself tells you which slots are worth waiting for, and",
                        "there is nothing left to find out. The cost is that a full container",
                        "takes longer to sweep, which is what ticksPerSlot is for.")
                .define("maskEmptySlots", true);
        SEARCH_CORPSES = b.comment("Search corpses before their contents are visible.")
                .define("corpses", true);
        SEARCH_CONTAINERS = b.comment("Search chests and other block containers as well.")
                .define("containers", true);
        SOUNDS = b.define("sounds", true);
        SOUND_VOLUME = b.defineInRange("soundVolume", 0.35D, 0.0D, 1.0D);
        b.pop();
        SPEC = b.build();
    }

    private SearchConfig() {}
}
