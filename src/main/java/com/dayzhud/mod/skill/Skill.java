package com.dayzhud.mod.skill;

import java.util.Locale;

/**
 * The five upgradeable skills, and everything that defines them: cost curve, cap, and how
 * much one level is worth.
 *
 * WHY THESE FIVE: each one moves a number this mod already owns and already shows on the
 * HUD - health, stamina, food/water, damage taken, temperature. Nothing here reaches into
 * another mod's internals, so none of it can break when a dependency updates. (That's a
 * deliberate choice: the weapon-handling skill people usually want first would mean
 * reflecting into a gun mod, which is exactly the coupling that broke this mod's corpse
 * support when Ragdollified reorganised its packages.)
 *
 * BALANCE: {@link #costFor} is the whole economy. Cost rises linearly with the level being
 * bought, so early levels are cheap and the cap is a real commitment - taking one skill from
 * 0 to 10 costs 130 XP levels. Tune baseCost/costStep here; nothing else needs touching.
 */
public enum Skill {

    VITALITY("Vitality",
            "Raises your maximum health.",
            2.0f, "max health", 10, 2, 2),

    ENDURANCE("Endurance",
            "Deepens your stamina pool and slows how fast sprinting burns it.",
            10.0f, "max stamina", 10, 2, 2),

    METABOLISM("Metabolism",
            "Slows how quickly you get hungry and thirsty.",
            6.0f, "% slower drain", 10, 2, 2),

    TOUGHNESS("Toughness",
            "Reduces all incoming damage.",
            2.0f, "% damage taken", 10, 3, 3),

    ACCLIMATION("Acclimation",
            "Widens the temperature band you can survive comfortably.",
            2.0f, "% wider comfort band", 10, 2, 2);

    private final String displayName;
    private final String description;
    /** How much ONE level is worth, in this skill's own units. */
    private final float perLevel;
    private final String unit;
    private final int maxLevel;
    private final int baseCost;
    private final int costStep;

    Skill(String displayName, String description, float perLevel, String unit,
          int maxLevel, int baseCost, int costStep) {
        this.displayName = displayName;
        this.description = description;
        this.perLevel = perLevel;
        this.unit = unit;
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.costStep = costStep;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /**
     * XP levels needed to buy {@code nextLevel} (1-based - the cost of going from 0 to 1 is
     * {@code costFor(1)}). Returns 0 for an out-of-range level so callers can treat "can't
     * buy" and "free" as the same guarded case; check {@link #maxLevel()} first.
     */
    public int costFor(int nextLevel) {
        if (nextLevel < 1 || nextLevel > maxLevel) return 0;
        return baseCost + nextLevel * costStep;
    }

    /** The skill's total effect at this level, in its own units. */
    public float magnitudeAt(int level) {
        return perLevel * Math.max(0, Math.min(level, maxLevel));
    }

    /**
     * Effect as a 0..1 fraction, for the skills whose unit is a percentage. Meaningless for
     * VITALITY and ENDURANCE, which are flat amounts - use {@link #magnitudeAt} there.
     */
    public float fractionAt(int level) {
        return magnitudeAt(level) / 100f;
    }

    /** Human-readable current effect, e.g. "+6 max health" or "12% damage taken". */
    public String describe(int level) {
        float value = magnitudeAt(level);
        String number = value == Math.floor(value)
                ? String.valueOf((int) value)
                : String.format(Locale.ROOT, "%.1f", value);
        return switch (this) {
            case VITALITY, ENDURANCE -> "+" + number + " " + unit;
            case TOUGHNESS -> "-" + number + unit;
            default -> "+" + number + unit;
        };
    }

    public static Skill byId(String id) {
        for (Skill skill : values()) {
            if (skill.id().equals(id)) return skill;
        }
        return null;
    }
}
