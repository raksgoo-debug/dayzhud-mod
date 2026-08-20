package com.dayzhud.mod.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.IntSupplier;

import java.util.List;
import java.util.Locale;

/**
 * Presents a Ragdollified corpse's loot as one flat, ordered handler:
 *
 *   0..26   the corpse's main inventory   (container indices 9..35)
 *   27..35  the corpse's hotbar           (container indices 0..8)
 *   36..    contents of the backpack worn in its "back" curio slot, if any
 *
 * Two reasons this exists rather than adding slots directly:
 *  - the corpse container stores the hotbar FIRST (vanilla Inventory order), which reads
 *    backwards in a looting UI, so the order is remapped here;
 *  - the worn bag's size isn't known when the menu is built and changes if the bag is
 *    taken, so its slots are resolved live and the whole thing is scrolled as one list.
 *
 * Two ways of getting at the worn bag's contents, tried in order:
 *   1. SA Survival bags, via SaSurvivalBackpackAccess - it builds the mod's own
 *      BackpackInventory from the ItemStack, since SA's normal handler needs a live Player
 *      that a corpse can't provide.
 *   2. Everything else, via Forge's standard ITEM_HANDLER capability on the stack.
 *
 * Both go through the owning mod's own code, so nothing here parses another mod's NBT.
 */
public class CorpseLootHandler implements IItemHandlerModifiable {

    public static final int MAIN_COUNT = 27;
    public static final int HOTBAR_COUNT = 9;
    public static final int BASE_COUNT = MAIN_COUNT + HOTBAR_COUNT;

    private static final String BACK_ID = "back";

    private static final int BAG_MIRROR_SIZE = 64;

    private final Container corpse;
    private final List<String> curioIds;
    private final int curioStart;
    private final boolean clientSide;

    /**
     * Client stand-in for the bag portion. The corpse's own 36 slots sync fine (they're
     * backed by the menu's container), but bag contents held in an ItemStack capability
     * never reach the client - same Forge limitation documented in BackCurioItemHandler.
     */
    private final ItemStackHandler bagMirror = new ItemStackHandler(BAG_MIRROR_SIZE);
    private IntSupplier syncedBagSlots = () -> 0;

    public CorpseLootHandler(Container corpse, List<String> curioIds, int curioStart, boolean clientSide) {
        this.corpse = corpse;
        this.curioIds = curioIds;
        this.curioStart = curioStart;
        this.clientSide = clientSide;
    }

    public void setSyncedBagSlots(IntSupplier supplier) {
        this.syncedBagSlots = supplier;
    }

    /** Server-side true bag size, used to drive the synced count. */
    public int serverBagSlots() {
        IItemHandler h = bagHandler();
        return h == null ? 0 : Math.min(h.getSlots(), BAG_MIRROR_SIZE);
    }

    /** The bag worn on the corpse's back, or EMPTY. */
    public ItemStack getBagStack() {
        for (int i = 0; i < curioIds.size(); i++) {
            if (!curioIds.get(i).toLowerCase(Locale.ROOT).equals(BACK_ID)) continue;
            int idx = curioStart + i;
            if (idx < corpse.getContainerSize()) {
                return corpse.getItem(idx);
            }
        }
        return ItemStack.EMPTY;
    }

    private IItemHandler bagHandler() {
        ItemStack bag = getBagStack();
        if (bag.isEmpty()) return null;

        // SA Survival deliberately exposes no item-handler capability, so use its own
        // inventory class against the stack instead.
        IItemHandler sa = SaSurvivalBackpackAccess.open(bag);
        if (sa != null) return sa;

        return bag.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }

    public int bagSlots() {
        if (clientSide) return Math.min(BAG_MIRROR_SIZE, syncedBagSlots.getAsInt());
        return serverBagSlots();
    }

    /** Maps our flat index onto the corpse container's own indexing. */
    private int containerIndex(int slot) {
        if (slot < MAIN_COUNT) return 9 + slot;          // main inventory
        return slot - MAIN_COUNT;                        // hotbar (0..8)
    }

    @Override
    public int getSlots() {
        return BASE_COUNT + bagSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0) return ItemStack.EMPTY;
        if (slot < BASE_COUNT) {
            int idx = containerIndex(slot);
            return idx < corpse.getContainerSize() ? corpse.getItem(idx) : ItemStack.EMPTY;
        }
        int bagSlot = slot - BASE_COUNT;
        if (clientSide) {
            return bagSlot < BAG_MIRROR_SIZE ? bagMirror.getStackInSlot(bagSlot) : ItemStack.EMPTY;
        }
        IItemHandler bag = bagHandler();
        return (bag != null && bagSlot < bag.getSlots()) ? bag.getStackInSlot(bagSlot) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0) return;
        if (slot < BASE_COUNT) {
            int idx = containerIndex(slot);
            if (idx < corpse.getContainerSize()) {
                corpse.setItem(idx, stack);
                corpse.setChanged();
            }
            return;
        }
        int bagSlot = slot - BASE_COUNT;
        if (clientSide) {
            if (bagSlot < BAG_MIRROR_SIZE) bagMirror.setStackInSlot(bagSlot, stack);
            return;
        }
        IItemHandler bag = bagHandler();
        if (bag instanceof IItemHandlerModifiable mod && bagSlot < bag.getSlots()) {
            mod.setStackInSlot(bagSlot, stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || stack.isEmpty()) return stack;

        if (slot < BASE_COUNT) {
            ItemStack existing = getStackInSlot(slot);
            if (!existing.isEmpty()) return stack; // keep it simple: only fill empty slots
            if (!simulate) setStackInSlot(slot, stack.copy());
            return ItemStack.EMPTY;
        }
        int bagSlot = slot - BASE_COUNT;
        if (clientSide) {
            return bagSlot < BAG_MIRROR_SIZE ? bagMirror.insertItem(bagSlot, stack, simulate) : stack;
        }
        IItemHandler bag = bagHandler();
        return (bag != null && bagSlot < bag.getSlots())
                ? bag.insertItem(bagSlot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || amount <= 0) return ItemStack.EMPTY;

        if (slot < BASE_COUNT) {
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty()) return ItemStack.EMPTY;
            int taken = Math.min(amount, existing.getCount());
            ItemStack result = existing.copy();
            result.setCount(taken);
            if (!simulate) {
                ItemStack remainder = existing.copy();
                remainder.shrink(taken);
                setStackInSlot(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            }
            return result;
        }
        int bagSlot = slot - BASE_COUNT;
        if (clientSide) {
            return bagSlot < BAG_MIRROR_SIZE
                    ? bagMirror.extractItem(bagSlot, amount, simulate) : ItemStack.EMPTY;
        }
        IItemHandler bag = bagHandler();
        return (bag != null && bagSlot < bag.getSlots())
                ? bag.extractItem(bagSlot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < getSlots();
    }
}
