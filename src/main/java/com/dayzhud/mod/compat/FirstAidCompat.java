package com.dayzhud.mod.compat;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    // ---- Per-limb view and treatment (2.16.0, the inventory's HEALTH tab) --------------------
    //
    // Also verified in firstaid-1.20.1-1.1.jar:
    //   AbstractDamageablePart.part          -> public final EnumPlayerPart
    //   AbstractDamageablePart.activeHealer  -> public AbstractPartHealer (null when none)
    //   AbstractPartHealer.maxHeal           -> public final IntSupplier; getHealsDone() -> int
    //   AbstractPlayerDamageModel.getFromEnum(EnumPlayerPart), getMorphineTicks(), scheduleResync()
    //   ItemHealing (extends Item): createNewHealer(ItemStack), getApplyTime(ItemStack) (ms)
    //
    // First Aid's own MessageApplyHealingItem handler does exactly this with the stack in the
    // player's hand: createNewHealer, shrink(1), part.activeHealer = healer. applyHealing
    // repeats it for the stack on the inventory cursor.

    private static final String MODEL_CLASS = "ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel";
    private static final String HEALER_CLASS = "ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer";
    private static final String ITEM_HEALING_CLASS = "ichttt.mods.firstaid.api.healing.ItemHealing";
    private static final String ENUM_PART_CLASS = "ichttt.mods.firstaid.api.enums.EnumPlayerPart";

    private static Field partField, activeHealerField, maxHealField;
    private static Method healsDoneMethod, getFromEnumMethod, morphineTicksMethod, scheduleResyncMethod;
    private static Method createNewHealerMethod, applyTimeMethod;
    private static Class<?> itemHealingClass;
    @SuppressWarnings("rawtypes")
    private static Class enumPartClass;
    private static boolean limbsResolved = false;

    /**
     * One limb. {@code id} is First Aid's EnumPlayerPart name (HEAD, LEFT_ARM, ...);
     * {@code healProgress} is 0..1 through the active healer's heals, or -1 with none.
     */
    public record Limb(String id, float current, int max, float healProgress) {
        public float health01() {
            return max <= 0 ? 0f : Math.max(0f, Math.min(1f, current / max));
        }
    }

    /** Every limb in First Aid's own order, or empty when First Aid isn't there or can't be read. */
    public static Optional<java.util.List<Limb>> limbs(Player player) {
        Object model = model(player);
        if (!(model instanceof Iterable<?> parts) || !limbsResolved) return Optional.empty();
        try {
            java.util.List<Limb> out = new java.util.ArrayList<>();
            for (Object part : parts) {
                Object healer = activeHealerField.get(part);
                float progress = -1f;
                if (healer != null) {
                    int max = ((java.util.function.IntSupplier) maxHealField.get(healer)).getAsInt();
                    int done = ((Number) healsDoneMethod.invoke(healer)).intValue();
                    progress = max <= 0 ? 0f : Math.min(1f, done / (float) max);
                }
                out.add(new Limb(((Enum<?>) partField.get(part)).name(), currentHealthField.getFloat(part),
                        ((Number) getMaxHealthMethod.invoke(part)).intValue(), progress));
            }
            return Optional.of(out);
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] First Aid limb read failed", e);
            return Optional.empty();
        }
    }

    /** Morphine left, in ticks; 0 without First Aid. */
    public static int morphineTicks(Player player) {
        Object model = model(player);
        if (model == null || morphineTicksMethod == null) return 0;
        try {
            return ((Number) morphineTicksMethod.invoke(model)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Whether this is one of First Aid's limb-treatment items (bandage, plaster, ...). */
    public static boolean isHealingItem(ItemStack stack) {
        return !stack.isEmpty() && limbsResolved() && itemHealingClass.isInstance(stack.getItem());
    }

    /** How long First Aid makes you hold the item on a limb, in ms (its own GUI's hold button). */
    public static int applyTimeMs(ItemStack stack) {
        if (!isHealingItem(stack)) return 0;
        try {
            return ((Number) applyTimeMethod.invoke(stack.getItem(), stack)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Server side: puts a healer made from {@code stack} on the named limb and uses one item,
     * as First Aid's own packet handler does. Returns false (nothing changed) if the stack
     * isn't a healing item, the limb name is unknown, or First Aid can't be read.
     */
    @SuppressWarnings("unchecked")
    public static boolean applyHealing(Player player, ItemStack stack, String limbId) {
        if (!isHealingItem(stack)) return false;
        Object model = model(player);
        if (model == null) return false;
        try {
            Object part = getFromEnumMethod.invoke(model, Enum.valueOf(enumPartClass, limbId));
            Object healer = createNewHealerMethod.invoke(stack.getItem(), stack);
            if (part == null || healer == null) return false;
            stack.shrink(1);
            activeHealerField.set(part, healer);
            scheduleResyncMethod.invoke(model);
            return true;
        } catch (IllegalArgumentException e) {
            return false; // not a limb name
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Couldn't apply a First Aid healing item", e);
            return false;
        }
    }

    private static Object model(Player player) {
        if (player == null || !isModLoaded()) return null;
        if (!resolved) resolve();
        if (damageModelCapability == null || !limbsResolved()) return null;
        return player.getCapability(damageModelCapability, (Direction) null).orElse(null);
    }

    private static boolean limbsResolved() {
        if (!isModLoaded()) return false;
        if (!resolved) resolve();
        return limbsResolved;
    }

    private static void resolveLimbs() {
        try {
            Class<?> partClass = Class.forName(PART_CLASS);
            partField = partClass.getField("part");
            activeHealerField = partClass.getField("activeHealer");
            Class<?> healerClass = Class.forName(HEALER_CLASS);
            maxHealField = healerClass.getField("maxHeal");
            healsDoneMethod = healerClass.getMethod("getHealsDone");
            enumPartClass = Class.forName(ENUM_PART_CLASS);
            Class<?> modelClass = Class.forName(MODEL_CLASS);
            getFromEnumMethod = modelClass.getMethod("getFromEnum", enumPartClass);
            morphineTicksMethod = modelClass.getMethod("getMorphineTicks");
            scheduleResyncMethod = modelClass.getMethod("scheduleResync");
            itemHealingClass = Class.forName(ITEM_HEALING_CLASS);
            createNewHealerMethod = itemHealingClass.getMethod("createNewHealer", ItemStack.class);
            applyTimeMethod = itemHealingClass.getMethod("getApplyTime", ItemStack.class);
            limbsResolved = currentHealthField != null && getMaxHealthMethod != null;
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] First Aid's limb API couldn't be resolved - the "
                    + "inventory's HEALTH tab stays hidden.", e);
            limbsResolved = false;
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
            resolveLimbs();
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
