package com.dayzhud.mod.compat;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reads the player's current thirst from "Thirst Was Taken" (real mod id: "thirst",
 * NOT "thirstwastaken" - the jar filename is misleading) via its actual capability API,
 * confirmed directly from the mod's own jar (version 1.20.1-1.4.0):
 *
 *   dev.ghen.thirst.foundation.common.capability.ModCapabilities.PLAYER_THIRST
 *       -> a Forge Capability<IThirst>
 *   dev.ghen.thirst.foundation.common.capability.IThirst.getThirst()
 *       -> int, 0..20 (same scale as vanilla hunger; confirmed by the 10-icon vanilla-style bar)
 *
 * This mod still has no compile-time dependency on Thirst Was Taken - Capability, Direction,
 * and LazyOptional are all normal Forge API classes we already depend on, so only the two
 * TWT-specific class lookups (ModCapabilities, IThirst) go through reflection. If a future
 * TWT version renames these, getThirst01() just falls back to vanilla saturation instead of
 * crashing - see the catch block below.
 */
public final class ThirstWasTakenCompat {

    private static final String MOD_ID = "thirst";
    private static final String CAPABILITIES_CLASS = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
    private static final String ITHIRST_CLASS = "dev.ghen.thirst.foundation.common.capability.IThirst";
    private static final int MAX_THIRST = 20;

    private static boolean resolved = false;
    private static Capability<?> playerThirstCapability;
    private static Method getThirstMethod;

    private ThirstWasTakenCompat() {}

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Returns thirst as a 0..1 fraction, or empty if the mod isn't present/resolvable. */
    public static Optional<Float> getThirst01(LocalPlayer player) {
        if (!isModLoaded()) return Optional.empty();
        if (!resolved) resolve();
        if (playerThirstCapability == null || getThirstMethod == null) return Optional.empty();

        try {
            LazyOptional<?> lazy = player.getCapability(playerThirstCapability, (Direction) null);
            Object thirstCap = lazy.orElse(null);
            if (thirstCap == null) return Optional.empty();

            int current = (int) getThirstMethod.invoke(thirstCap);
            return Optional.of(Math.max(0f, Math.min(1f, current / (float) MAX_THIRST)));
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Thirst Was Taken read failed, falling back to vanilla saturation.", e);
            return Optional.empty();
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
            getThirstMethod.setAccessible(true);
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Thirst Was Taken is installed but its API couldn't be resolved "
                    + "(mod may have changed internals) - water gauge will use vanilla saturation instead.", e);
            playerThirstCapability = null;
            getThirstMethod = null;
        }
    }
}
