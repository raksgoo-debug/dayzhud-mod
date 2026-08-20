package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Opens an SA Survival backpack's contents from a bare ItemStack - needed for corpses,
 * where the mod's own DynamicBackpackItemHandler can't help because it resolves the bag
 * from a live Player and a corpse isn't one.
 *
 * WHY THIS IS SAFE: it does not parse or write SA's NBT format itself. It constructs the
 * mod's OWN com.ogaba.sa_survival.item.backpack.BackpackInventory(ItemStack, int), which
 * extends ItemStackHandler, loads from the stack's tag, and auto-calls its own save() from
 * onContentsChanged. All reading and writing therefore goes through SA's serialisation
 * code, so it stays correct even if their storage format changes internally. Hand-rolling
 * NBT parsing here is what would risk destroying loot.
 *
 * Everything is resolved reflectively and fails soft: if SA isn't installed, or the class
 * or method names change, this returns null and callers fall back to showing nothing
 * rather than showing wrong data.
 */
public final class SaSurvivalBackpackAccess {

    private static final String SA_MODID = "sa_survival";
    private static final String INVENTORY_CLASS = "com.ogaba.sa_survival.item.backpack.BackpackInventory";
    private static final String ITEM_CLASS = "com.ogaba.sa_survival.item.backpack.BackpackItem";

    private static boolean resolved = false;
    private static Constructor<?> inventoryCtor;   // (ItemStack, int)
    private static Class<?> backpackItemClass;
    private static Method getSlotCountForStack;    // instance: getSlotCount(ItemStack)

    private SaSurvivalBackpackAccess() {}

    private static void resolve() {
        resolved = true;
        if (!ModList.get().isLoaded(SA_MODID)) return;
        try {
            Class<?> invClass = Class.forName(INVENTORY_CLASS);
            inventoryCtor = invClass.getConstructor(ItemStack.class, int.class);

            backpackItemClass = Class.forName(ITEM_CLASS);
            getSlotCountForStack = backpackItemClass.getMethod("getSlotCount", ItemStack.class);
        } catch (Exception e) {
            DayzHudMod.LOGGER.info("[dayzhud] SA Survival backpack internals not found in the "
                    + "expected shape; corpse SA bags will show as loot only.", e);
            inventoryCtor = null;
            backpackItemClass = null;
            getSlotCountForStack = null;
        }
    }

    /** True if this stack is an SA Survival backpack we know how to open. */
    public static boolean isSaBackpack(ItemStack stack) {
        if (!resolved) resolve();
        if (backpackItemClass == null || stack.isEmpty()) return false;
        return backpackItemClass.isInstance(stack.getItem());
    }

    /**
     * A handler over the given SA backpack's contents, or null if unavailable.
     * Writes go through the mod's own save(), so they persist on the ItemStack.
     */
    public static IItemHandlerModifiable open(ItemStack stack) {
        if (!resolved) resolve();
        if (inventoryCtor == null || !isSaBackpack(stack)) return null;

        try {
            Item item = stack.getItem();
            int slots = (int) getSlotCountForStack.invoke(item, stack);
            if (slots <= 0) return null;

            Object inventory = inventoryCtor.newInstance(stack, slots);
            return inventory instanceof IItemHandlerModifiable modifiable ? modifiable : null;
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Couldn't open SA Survival backpack contents.", e);
            return null;
        }
    }
}
