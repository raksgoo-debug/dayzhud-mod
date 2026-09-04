package com.dayzhud.mod.search;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A view of a container in which unsearched slots are genuinely empty.
 *
 * This is the whole reason for writing our own mechanic instead of continuing to fight
 * Rummage's: masking happens HERE, in the Container the menu reads, so the server never
 * serialises a hidden item into the slot-sync packet at all. A client-side mask hides the
 * drawing and ships the data anyway - anyone with a modified client, or an item-search mod
 * reading the menu, sees straight through it.
 *
 * Everything is delegated except reads of unsearched slots. Writes are delegated untouched:
 * shift-clicking INTO a corpse must still work, and `setItem` from the server's own logic must
 * not be silently dropped.
 */
public class SearchedContainer implements Container {

    private final Container delegate;
    private final Player viewer;

    public SearchedContainer(Container delegate, Player viewer) {
        this.delegate = delegate;
        this.viewer = viewer;
    }

    public Container delegate() {
        return delegate;
    }

    private boolean hidden(int slot) {
        return !SearchProgress.isRevealed(delegate, viewer, slot);
    }

    @Override
    public int getContainerSize() {
        return delegate.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return hidden(slot) ? ItemStack.EMPTY : delegate.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        // Refuse to give up an item nobody has found yet. Returning EMPTY rather than throwing
        // means a stray quick-move simply does nothing instead of crashing the menu.
        if (hidden(slot)) return ItemStack.EMPTY;
        return delegate.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (hidden(slot)) return ItemStack.EMPTY;
        return delegate.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        delegate.setItem(slot, stack);
        // Putting something into a slot reveals it: it is in the player's hand, so pretending
        // they have not found it is nonsense, and leaving it hidden would let them lose an item
        // into a slot they cannot take it back out of.
        if (!stack.isEmpty()) SearchProgress.reveal(delegate, viewer, slot);
    }

    @Override
    public void setChanged() {
        delegate.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return delegate.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        delegate.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        delegate.stopOpen(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return delegate.canPlaceItem(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return delegate.getMaxStackSize();
    }

    @Override
    public void clearContent() {
        delegate.clearContent();
    }
}
