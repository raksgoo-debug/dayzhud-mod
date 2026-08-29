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

/**
 * Reads real health from First Aid (modid "firstaid") when it's installed.
 *
 * WHY THIS EXISTS: with First Aid running, {@code player.getHealth()} is NOT your health. It
 * is a lossy summary that First Aid recomputes from its own per-limb damage model, confirmed
 * by disassembling firstaid-1.20.1-1.1.jar:
 *
 *   PlayerDamageModel.calculateNewCurrentHealth(player):
 *       ... per vanillaHealthCalculation mode, e.g. AVERAGE_ALL:
 *           criticalFraction  = sum(critical currentHealth)     / sum(critical maxHealth)
 *           otherFraction     = sum(non-critical currentHealth) / sum(non-critical maxHealth)
 *           fraction          = (criticalFraction + otherFraction) / 2
 *       return fraction * player.getMaxHealth();
 *
 * So the vanilla value is an average of two group averages, re-scaled onto the max-health
 * attribute, and only refreshed when First Aid decides to push it. Anything that writes
 * vanilla health directly - natural regeneration, another mod's heal - moves that number
 * without touching a single limb, which is exactly how the HUD ended up reading 100% next to
 * a visibly wrecked body.
 *
 * So we don't read it. We read the limbs and total them, which is the same arithmetic a
 * player does looking at First Aid's own overlay.
 *
 * THE API, verified in the jar rather than assumed:
 *   ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem.INSTANCE
 *       -> Capability&lt;AbstractPlayerDamageModel&gt;
 *   AbstractPlayerDamageModel implements Iterable&lt;AbstractDamageablePart&gt;
 *   AbstractDamageablePart.currentHealth   -> public float field
 *   AbstractDamageablePart.getMaxHealth()  -> int
 *
 * No compile-time dependency: only those two class lookups are reflective, and every failure
 * path returns empty so the caller falls back to vanilla health.
 */
public final class FirstAidCompat {

    private static final String MOD_ID = "firstaid";
    private static final String CAPABILITY_CLASS = "ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem";
    private static final String PART_CLASS = "ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart";

    private static boolean resolved = false;
    private static Capability<?> damageModelCapability;
    private static Field currentHealthField;
    private static Method getMaxHealthMethod;

    private FirstAidCompat() {}

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Total body integrity as 0..1 - every limb's current health over every limb's maximum -
     * or empty when First Aid isn't installed or can't be read.
     *
     * Deliberately a plain total rather than a copy of First Aid's own AVERAGE_ALL formula:
     * a total is what "health %" means to someone reading a HUD, and it's what you get by
     * eyeballing First Aid's limb display. To mirror the vanilla bar instead, this is the
     * one method to change.
     */
    public static Optional<Float> getBodyHealth01(Player player) {
        if (player == null || !isModLoaded()) return Optional.empty();
        if (!resolved) resolve();
        if (damageModelCapability == null || currentHealthField == null || getMaxHealthMethod == null) {
            return Optional.empty();
        }

        try {
            LazyOptional<?> lazy = player.getCapability(damageModelCapability, (Direction) null);
            Object model = lazy.orElse(null);
            if (!(model instanceof Iterable<?> parts)) return Optional.empty();

            float current = 0f;
            float max = 0f;
            for (Object part : parts) {
                current += currentHealthField.getFloat(part);
                max += ((Number) getMaxHealthMethod.invoke(part)).floatValue();
            }
            if (max <= 0f) return Optional.empty();
            return Optional.of(Math.max(0f, Math.min(1f, current / max)));
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] First Aid health read failed; "
                    + "falling back to vanilla health.", e);
            return Optional.empty();
        }
    }

    private static void resolve() {
        resolved = true;
        try {
            Class<?> capClass = Class.forName(CAPABILITY_CLASS);
            damageModelCapability = (Capability<?>) capClass.getField("INSTANCE").get(null);

            Class<?> partClass = Class.forName(PART_CLASS);
            currentHealthField = partClass.getField("currentHealth");
            getMaxHealthMethod = partClass.getMethod("getMaxHealth");
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] First Aid is installed but its damage-model API "
                    + "couldn't be resolved (the mod may have changed internals) - the health "
                    + "gauge will show vanilla health, which under First Aid is only an "
                    + "approximation of your real condition.", e);
            damageModelCapability = null;
            currentHealthField = null;
            getMaxHealthMethod = null;
        }
    }
}
