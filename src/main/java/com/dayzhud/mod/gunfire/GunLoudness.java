package com.dayzhud.mod.gunfire;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How loud a gun is and how far it carries, from what it fires (2.19.0).
 *
 * Worked out once per gun from TACZ's own data: the calibre (ammo id) first, matched against
 * the common cartridges below; then the gun's TACZ type; then its damage. A pistol cartridge
 * is a pop next to a .50 BMG, and this is what makes them sound it at a distance - and carry
 * accordingly, for players and mobs alike.
 */
public final class GunLoudness {

    /** Multipliers: {@code gain} on volume, {@code range} on how far it carries. */
    public record Profile(String calibreClass, float gain, float range) {}

    private enum Class {
        PISTOL(0.7f, 0.55f),
        SHOTGUN(1.0f, 0.85f),
        INTERMEDIATE(1.0f, 1.0f),
        FULL(1.25f, 1.35f),
        HEAVY(1.7f, 2.0f);

        final float gain, range;

        Class(float gain, float range) {
            this.gain = gain;
            this.range = range;
        }
    }

    private static final Profile NEUTRAL = new Profile("default", 1f, 1f);

    // Matched against the ammo id's path with "_", "." and "-" taken out ("7_62x39" -> "762x39").
    // Checked in this order, so .300 Blackout is intermediate before .300 Win Mag is full-power.
    private static final List<String> HEAVY = List.of("50bmg", "bmg", "127x", "338", "408", "145x", "50cal",
            "lapua", "300wm", "300win", "300mag");
    private static final List<String> INTERMEDIATE = List.of("556", "545", "762x39", "58x42", "300blk",
            "blackout", "68x", "grendel", "65x39", "939");
    private static final List<String> FULL = List.of("762x54", "762x51", "308", "3006", "303", "4570",
            "792", "8mm", "7mm", "65x", "762x63", "creedmoor");
    private static final List<String> SHOTGUN = List.of("12g", "12ga", "20g", "gauge", "410", "shell");
    private static final List<String> PISTOL = List.of("9mm", "9x", "45acp", "acp", "40sw", "10mm", "57x28",
            "46x30", "357", "38", "44", "50ae", "22", "380", "32", "762x25", "magnum");

    private static final Map<ResourceLocation, Profile> CACHE = new HashMap<>();

    private GunLoudness() {}

    public static Profile of(ItemStack gun) {
        if (!GunfireConfig.LOUDNESS_BY_CALIBRE.get()) return NEUTRAL;
        ResourceLocation id = TaczMarketCompat.gunIdOf(gun).orElse(null);
        if (id == null) return NEUTRAL;
        Profile cached = CACHE.get(id);
        if (cached != null) return cached;
        Profile p = classify(id);
        CACHE.put(id, p);
        DayzHudMod.LOGGER.info("[dayzhud] Gunshot loudness for {}: {} (volume x{}, range x{})",
                id, p.calibreClass(), p.gain(), p.range());
        return p;
    }

    private static Profile classify(ResourceLocation id) {
        TaczMarketCompat.Ballistics b = TaczMarketCompat.ballisticsOf(id);
        if (b == null) return NEUTRAL;
        Class c = byCalibre(b.ammoId());
        if (c == null) c = byType(b.type());
        if (c == null) c = byDamage(b.damage() * b.pellets());
        return new Profile(c.name().toLowerCase(Locale.ROOT), c.gain, c.range);
    }

    private static Class byCalibre(String ammoId) {
        if (ammoId == null) return null;
        String a = ammoId.substring(ammoId.indexOf(':') + 1).toLowerCase(Locale.ROOT).replaceAll("[_.\\-]", "");
        if (matches(a, HEAVY)) return Class.HEAVY;
        if (matches(a, INTERMEDIATE)) return Class.INTERMEDIATE;
        if (matches(a, FULL)) return Class.FULL;
        if (matches(a, SHOTGUN)) return Class.SHOTGUN;
        if (matches(a, PISTOL)) return Class.PISTOL;
        return null;
    }

    private static boolean matches(String ammo, List<String> keys) {
        for (String k : keys) if (ammo.contains(k)) return true;
        return false;
    }

    private static Class byType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "pistol", "smg" -> Class.PISTOL;
            case "shotgun" -> Class.SHOTGUN;
            case "rifle", "mg" -> Class.INTERMEDIATE;
            case "sniper" -> Class.FULL;
            case "rpg" -> Class.HEAVY;
            default -> null;
        };
    }

    private static Class byDamage(float damage) {
        if (damage <= 0) return Class.INTERMEDIATE;
        if (damage < 6) return Class.PISTOL;
        if (damage < 10) return Class.INTERMEDIATE;
        if (damage < 22) return Class.FULL;
        return Class.HEAVY;
    }

    /** For a datapack/gun pack reload. */
    public static void clear() {
        CACHE.clear();
    }
}
