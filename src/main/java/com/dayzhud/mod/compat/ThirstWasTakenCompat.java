package com.dayzhud.mod.compat;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Reads and writes "Thirst Was Taken" (real mod id: "thirst", NOT "thirstwastaken" - the jar
 * filename is misleading) through its capability, confirmed by disassembling the mod's own
 * jar (1.20.1-1.4.0):
 *
 *   dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST
 *       -> Capability&lt;IThirst&gt;
 *   IThirst.getThirst() / setThirst(int)           -> 0..20, same scale as vanilla hunger
 *   IThirst.getExhaustion() / setExhaustion(float) -> thirst's own exhaustion accumulator
 *
 * That exhaustion pair is what lets Metabolism slow thirst PROPERLY rather than approximate
 * it: TWT drains a thirst point when its exhaustion crosses a threshold, exactly like vanilla
 * hunger, so scaling the increase slows the drain at source. Confirmed present in the jar
 * before being relied on here - guessing at another mod's API is how the corpse integration
 * broke when Ragdollified reorganised itself.
 *
 * Still no compile-time dependency: Capability, Direction and LazyOptional are ordinary Forge
 * classes, and only the two TWT class lookups go through reflection. If a future TWT release
 * renames them, every method here degrades to "unavailable" rather than throwing.
 */
public final class ThirstWasTakenCompat {

    private static final String MOD_ID = "thirst";
    private static final String CAPABILITIES_CLASS = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
    private static final String ITHIRST_CLASS = "dev.ghen.thirst.foundation.common.capability.IThirst";
    private static final int MAX_THIRST = 20;

    private static boolean resolved = false;
    private static Capability<?> playerThirstCapability;
    private static Method getThirstMethod;
    private static Method getExhaustionMethod;
    private static Method setExhaustionMethod;
    private static Method setThirstMethod;
    private static Method getQuenchedMethod;
    private static Method setQuenchedMethod;

    private ThirstWasTakenCompat() {}

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Thirst as a 0..1 fraction, or empty if the mod isn't present/resolvable. */
    public static Optional<Float> getThirst01(Player player) {
        Object cap = capabilityFor(player);
        if (cap == null || getThirstMethod == null) return Optional.empty();
        try {
            int current = (int) getThirstMethod.invoke(cap);
            return Optional.of(Math.max(0f, Math.min(1f, current / (float) MAX_THIRST)));
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Thirst read failed; falling back to vanilla saturation.", e);
            return Optional.empty();
        }
    }

    /** TWT's thirst-exhaustion accumulator, or empty when unavailable. */
    public static OptionalDouble getExhaustion(Player player) {
        Object cap = capabilityFor(player);
        if (cap == null || getExhaustionMethod == null) return OptionalDouble.empty();
        try {
            return OptionalDouble.of((float) getExhaustionMethod.invoke(cap));
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Thirst exhaustion read failed.", e);
            return OptionalDouble.empty();
        }
    }

    /** Writes TWT's thirst-exhaustion accumulator. False if it couldn't be written. */
    public static boolean setExhaustion(Player player, float value) {
        Object cap = capabilityFor(player);
        if (cap == null || setExhaustionMethod == null) return false;
        try {
            setExhaustionMethod.invoke(cap, value);
            return true;
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Thirst exhaustion write failed.", e);
            return false;
        }
    }

    /**
     * Adds thirst (and, optionally, quenched) the way a drink item would.
     *
     * Written through setThirst rather than TWT's own drink(Player,int,int) because drink()
     * also plays its own effects and assumes it is being called from TWT's item logic;
     * clamping and setting the value directly is the part this mod actually wants.
     *
     * @return false when TWT is absent or its API did not resolve, so the caller can fall
     *         back to vanilla saturation instead of silently doing nothing.
     */
    public static boolean quench(Player player, int thirst, int quenched) {
        Object cap = capabilityFor(player);
        if (cap == null || getThirstMethod == null || setThirstMethod == null) return false;
        try {
            int current = (int) getThirstMethod.invoke(cap);
            setThirstMethod.invoke(cap, Math.max(0, Math.min(MAX_THIRST, current + thirst)));
            if (quenched > 0 && getQuenchedMethod != null && setQuenchedMethod != null) {
                int q = (int) getQuenchedMethod.invoke(cap);
                setQuenchedMethod.invoke(cap, Math.max(0, Math.min(MAX_THIRST, q + quenched)));
            }
            return true;
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Thirst write failed.", e);
            return false;
        }
    }

    private static Object capabilityFor(Player player) {
        if (player == null || !isModLoaded()) return null;
        if (!resolved) resolve();
        if (playerThirstCapability == null) return null;
        try {
            LazyOptional<?> lazy = player.getCapability(playerThirstCapability, (Direction) null);
            return lazy.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void resolve() {
        resolved = true;
        try {
            Class<?> capsClass = Class.forName(CAPABILITIES_CLASS);
            Field capField = capsClass.getField("PLAYER_THIRST");
            playerThirstCapability = (Capability<?>) capField.get(null);

            Class<?> iThirstClass = Class.forName(ITHIRST_CLASS);
            getThirstMethod = iThirstClass.getMethod("getThirst");
            getExhaustionMethod = iThirstClass.getMethod("getExhaustion");
            setExhaustionMethod = iThirstClass.getMethod("setExhaustion", float.class);
            // Optional extras: a TWT release that drops these should still leave the gauge
            // and Metabolism working, so they are resolved separately and allowed to fail.
            try {
                setThirstMethod = iThirstClass.getMethod("setThirst", int.class);
                getQuenchedMethod = iThirstClass.getMethod("getQuenched");
                setQuenchedMethod = iThirstClass.getMethod("setQuenched", int.class);
            } catch (NoSuchMethodException missing) {
                DayzHudMod.LOGGER.debug("[dayzhud] Thirst setters unavailable; drinks will fall "
                        + "back to vanilla saturation.", missing);
            }
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Thirst Was Taken is installed but its API couldn't be "
                    + "resolved (the mod may have changed internals) - the water gauge falls back "
                    + "to vanilla saturation and Metabolism won't slow thirst.", e);
            playerThirstCapability = null;
            getThirstMethod = null;
            getExhaustionMethod = null;
            setExhaustionMethod = null;
            setThirstMethod = null;
            getQuenchedMethod = null;
            setQuenchedMethod = null;
        }
    }
}
