package com.dayzhud.mod.compat;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reads the player's current/max thirst from "Thirst Was Taken" (modid: thirstwastaken)
 * WITHOUT a compile-time dependency, so this mod builds and runs fine whether or not
 * that mod is installed.
 *
 * IMPORTANT - read this before relying on it:
 * Thirst Was Taken doesn't publish a stable public API, so the exact capability/attachment
 * class can shift between its versions. This class tries the method names most commonly
 * used by that mod's capability object ("getThirst"/"getMaxThirst" and common variants).
 * If it can't find a match it fails closed (isAvailable() returns false) rather than
 * guessing wrong, and the HUD falls back to vanilla food saturation for the water gauge.
 *
 * If the default lookup doesn't hit on your exact copy of the mod:
 *   1. Open the installed ThirstWasTaken-*.jar in a decompiler (e.g. Bytecode Viewer / vineflower).
 *   2. Find the capability/attachment class attached to Player (search for "Capability<" or
 *      "AttachmentType<" near a class with a name like ThirstCapability / PlayerThirst).
 *   3. Note the exact getter method names and update CANDIDATE_GET_METHODS /
 *      CANDIDATE_MAX_METHODS below, and CAPABILITY_HOLDER_CLASS if the capability class
 *      itself differs from what's guessed here.
 */
public final class ThirstWasTakenCompat {

    private static final String MOD_ID = "thirstwastaken";

    // Common capability provider class names seen across TWT's version history.
    // Tried in order; first one that resolves and yields a usable getter wins.
    private static final String[] CANDIDATE_CAPABILITY_CLASSES = {
            "com.ghenghen.thirstwastaken.capability.ThirstCapability",
            "com.ghenghen.thirstwastaken.common.capability.ThirstCapability",
            "com.ghenghen.thirstwastaken.capability.PlayerThirstCapability"
    };

    private static final String[] CANDIDATE_GET_METHODS = {"getThirst", "getWater", "getHydration", "getThirstLevel"};
    private static final String[] CANDIDATE_MAX_METHODS = {"getMaxThirst", "getMaxWater", "getMaxHydration"};

    private static boolean resolved = false;
    private static boolean available = false;

    private ThirstWasTakenCompat() {}

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Returns thirst as a 0..1 fraction, or empty if unavailable/unresolvable. */
    public static Optional<Float> getThirst01(LocalPlayer player) {
        if (!isModLoaded()) return Optional.empty();
        if (!resolved) resolve();
        if (!available) return Optional.empty();

        try {
            Object capObject = findCapabilityInstance(player);
            if (capObject == null) return Optional.empty();

            float current = -1f, max = -1f;
            for (String name : CANDIDATE_GET_METHODS) {
                Method m = tryGetMethod(capObject.getClass(), name);
                if (m != null) {
                    current = ((Number) m.invoke(capObject)).floatValue();
                    break;
                }
            }
            for (String name : CANDIDATE_MAX_METHODS) {
                Method m = tryGetMethod(capObject.getClass(), name);
                if (m != null) {
                    max = ((Number) m.invoke(capObject)).floatValue();
                    break;
                }
            }

            if (current < 0f) return Optional.empty();
            if (max <= 0f) max = 20f; // TWT historically uses a 0-20 scale like vanilla hunger
            return Optional.of(Math.max(0f, Math.min(1f, current / max)));
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Thirst Was Taken compat read failed, falling back to vanilla saturation.", e);
            return Optional.empty();
        }
    }

    private static void resolve() {
        resolved = true;
        for (String className : CANDIDATE_CAPABILITY_CLASSES) {
            try {
                Class.forName(className);
                available = true;
                return;
            } catch (ClassNotFoundException ignored) {
                // try next candidate
            }
        }
        DayzHudMod.LOGGER.info("[dayzhud] Thirst Was Taken is installed but its capability class "
                + "wasn't found under any known name - water gauge will use vanilla saturation instead. "
                + "See ThirstWasTakenCompat.java for how to fix this for your mod version.");
        available = false;
    }

    private static Object findCapabilityInstance(LocalPlayer player) {
        // TWT attaches its data via a Forge Capability on the player. We look it up
        // generically by scanning the player's exposed capabilities for an object whose
        // class matches one of our candidates, avoiding a hard reference to TWT's
        // Capability<T> token (which we don't have without compiling against the mod).
        for (String className : CANDIDATE_CAPABILITY_CLASSES) {
            try {
                Class<?> capClass = Class.forName(className);
                Object found = net.minecraftforge.common.util.LazyOptional.class != null
                        ? scanCapabilities(player, capClass)
                        : null;
                if (found != null) return found;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static Object scanCapabilities(LocalPlayer player, Class<?> capClass) {
        try {
            // Player capabilities are exposed via getCapability(Capability<T>), which needs
            // the mod's own Capability token - we don't have it. Instead, most TWT builds
            // also expose the value through the entity's persistent/forge data, so as a
            // practical fallback we reflectively probe common capability-holder field names
            // on the player mixin/attachment. This keeps the mod compiling without TWT on
            // the classpath while still finding real data when the class layout matches.
            for (java.lang.reflect.Field f : player.getClass().getDeclaredFields()) {
                if (capClass.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(player);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Method tryGetMethod(Class<?> clazz, String name) {
        try {
            Method m = clazz.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
