package com.dayzhud.mod.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A live view of whatever container item is currently worn in the player's "back" Curios
 * slot. Every call re-resolves the worn bag, so the moment a different backpack is
 * equipped (or removed) this handler's size and contents change with it - no menu reopen
 * needed. That's what lets the backpack grid in the inventory screen update on the fly.
 *
 * All accessors are bounds-checked and fail soft (empty stack / no-op) rather than
 * throwing, because the backing size legitimately changes underneath callers mid-session.
 */
public class BackCurioItemHandler implements IItemHandlerModifiable {

    /** Curios identifier we treat as "the backpack slot". */
    private static final String BACK_SLOT_ID = "back";

    private final Player player;

    public BackCurioItemHandler(Player player) {
        this.player = player;
    }

    /** The bag ItemStack currently worn on the back, or EMPTY. */
    public ItemStack getBagStack() {
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isEmpty()) return ItemStack.EMPTY;

        for (Map.Entry<String, ICurioStacksHandler> entry : curiosOpt.get().getCurios().entrySet()) {
            if (!entry.getKey().toLowerCase(Locale.ROOT).equals(BACK_SLOT_ID)) continue;
            ICurioStacksHandler handler = entry.getValue();
            if (handler == null) continue;
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                ItemStack stack = handler.getStacks().getStackInSlot(i);
                if (!stack.isEmpty()) return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The worn bag's own inventory, or null if nothing suitable is equipped. */
    private IItemHandler delegate() {
        ItemStack bag = getBagStack();
        if (bag.isEmpty()) return null;
        return bag.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }

    private boolean valid(IItemHandler h, int slot) {
        return h != null && slot >= 0 && slot < h.getSlots();
    }

    @Override
    public int getSlots() {
        IItemHandler h = delegate();
        return h == null ? 0 : h.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        IItemHandler h = delegate();
        return valid(h, slot) ? h.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        IItemHandler h = delegate();
        return valid(h, slot) ? h.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        IItemHandler h = delegate();
        return valid(h, slot) ? h.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        IItemHandler h = delegate();
        return valid(h, slot) ? h.getSlotLimit(slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        IItemHandler h = delegate();
        return valid(h, slot) && h.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        IItemHandler h = delegate();
        if (valid(h, slot) && h instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        }
    }
}
