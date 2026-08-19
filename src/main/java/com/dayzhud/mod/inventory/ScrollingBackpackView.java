package com.dayzhud.mod.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * A fixed-size scrolling window onto a larger backpack inventory.
 *
 * The menu always creates exactly {@link #visibleSlots()} slots at fixed screen positions;
 * this view maps those onto the underlying handler with a row offset, so scrolling shifts
 * which part of a big bag those slots are showing rather than adding/removing slots. That
 * matters because a container's slot list can't change size once the menu is open.
 *
 * The offset must be identical on client and server or clicks land on the wrong item, so
 * it's only ever changed via BackpackScrollPacket, never set locally on one side alone.
 */
public class ScrollingBackpackView implements IItemHandlerModifiable {

    private final BackCurioItemHandler backing;
    private final int columns;
    private final int visibleRows;

    private int scrollRow = 0;

    public ScrollingBackpackView(BackCurioItemHandler backing, int columns, int visibleRows) {
        this.backing = backing;
        this.columns = columns;
        this.visibleRows = visibleRows;
    }

    public int visibleSlots() {
        return columns * visibleRows;
    }

    /** Total rows the worn bag actually needs. */
    public int totalRows() {
        int total = totalUsableSlots();
        return (total + columns - 1) / columns;
    }

    /** How far down we can scroll before running out of content. */
    public int maxScrollRow() {
        return Math.max(0, totalRows() - visibleRows);
    }

    public int getScrollRow() {
        return scrollRow;
    }

    public void setScrollRow(int row) {
        this.scrollRow = Math.max(0, Math.min(row, maxScrollRow()));
    }

    public boolean isScrollable() {
        return maxScrollRow() > 0;
    }

    private int totalUsableSlots() {
        int total = 0;
        // The backing handler reports its own true capacity (already corrected per mod).
        for (int i = 0; i < backing.getSlots(); i++) {
            if (backing.isSlotUsable(i)) total = i + 1;
        }
        return total;
    }

    /** Maps a visible slot index to the underlying handler index for the current scroll. */
    public int mapIndex(int visibleIndex) {
        return visibleIndex + scrollRow * columns;
    }

    public boolean isVisibleSlotUsable(int visibleIndex) {
        int real = mapIndex(visibleIndex);
        return real < backing.getSlots() && backing.isSlotUsable(real);
    }

    @Override
    public int getSlots() {
        return visibleSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return backing.getStackInSlot(mapIndex(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return backing.insertItem(mapIndex(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return backing.extractItem(mapIndex(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return backing.getSlotLimit(mapIndex(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return backing.isItemValid(mapIndex(slot), stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        backing.setStackInSlot(mapIndex(slot), stack);
    }
}
