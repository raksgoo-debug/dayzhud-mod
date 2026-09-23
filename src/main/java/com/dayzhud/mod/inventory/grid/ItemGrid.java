package com.dayzhud.mod.inventory.grid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Multi-cell items in a flat, 1-slot-per-index {@link GridStorage} - a {@code Container}
 * (the player's own inventory, an opened chest) or an {@code IItemHandlerModifiable} (a worn
 * backpack, a corpse's loot bag), whichever the region is backed by.
 *
 * Minecraft's container model has no concept of "reserved but empty" - a slot either holds a
 * stack or it doesn't, and vanilla auto-pickup, hoppers, and other mods all write into the
 * first empty slot they find with no idea that this mod thinks of some empty-looking cells as
 * spoken for. Blocking placement at the Slot/mayPlace layer (as the loadout slots in
 * WeaponSlots do) only stops clicks made through THIS screen; it does nothing about any of
 * those other writers.
 *
 * So a "shadow" cell is never actually left empty. It holds a real (if invisible) marker
 * stack - see {@link ModGridItems} - tagged with which cell is its anchor. Every writer that
 * checks {@code ItemStack.isEmpty()} before inserting, which is the standard contract, sees
 * the cell as occupied and leaves it alone.
 *
 * Reservations are never hand-maintained. {@link #reconcile} recomputes every shadow in a
 * region from scratch, every time it's called (see {@link com.dayzhud.mod.inventory.TarkovInventoryMenu#broadcastChanges},
 * once a tick), from nothing but the real items currently in the region and their declared
 * footprints. That makes the whole system self-healing: a pickup, a drop, a hopper insert, a
 * stray write from anywhere - whatever changed, the next reconcile pass derives the correct
 * shadows from the result, rather than every possible change site needing to remember to
 * clean up after itself.
 */
public final class ItemGrid {

    private static final String ANCHOR_KEY = "dayzhud:GridAnchor";
    private static final String ROTATED_KEY = "dayzhud:GridRotated";

    private ItemGrid() {}

    public static boolean isReservation(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModGridItems.GRID_RESERVED.get();
    }

    public static boolean isRotated(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(ROTATED_KEY);
    }

    public static void setRotated(ItemStack stack, boolean rotated) {
        if (rotated) {
            stack.getOrCreateTag().putBoolean(ROTATED_KEY, true);
        } else if (stack.hasTag()) {
            stack.getTag().remove(ROTATED_KEY);
        }
    }

    /** The footprint this stack actually occupies right now, rotation included. Never call
     *  this on a reservation stack - it has no footprint of its own. */
    public static Footprint footprintOf(ItemStack stack) {
        Footprint base = ItemFootprints.baseFootprintOf(stack);
        return isRotated(stack) ? base.rotated() : base;
    }

    public static boolean isMultiCell(ItemStack stack) {
        return !stack.isEmpty() && ItemFootprints.baseFootprintOf(stack).isMultiCell();
    }

    /** Container-relative index of the anchor a reservation stack belongs to, or -1 if
     *  {@code stack} isn't a reservation or is missing its tag. */
    public static int anchorOf(ItemStack stack) {
        if (!isReservation(stack) || !stack.hasTag()) return -1;
        CompoundTag tag = stack.getTag();
        return tag.contains(ANCHOR_KEY) ? tag.getInt(ANCHOR_KEY) : -1;
    }

    private static ItemStack reservationFor(int anchorContainerIndex) {
        ItemStack stack = new ItemStack(ModGridItems.GRID_RESERVED.get());
        stack.getOrCreateTag().putInt(ANCHOR_KEY, anchorContainerIndex);
        return stack;
    }

    /**
     * Whether the multi-cell item anchored at ({@code col}, {@code row}) currently holds
     * every one of its shadow cells in its own name - i.e., whether the last {@link
     * #reconcile} actually granted it its footprint, as opposed to a losing contender that
     * still declares a multi-cell footprint but never got to claim the cells for it (its
     * neighbour won the contest, or simply already existed there from before either of them
     * went through this system - which is exactly what happens to two guns of the same type
     * sitting one cell apart from before a footprint config change made them wider than the
     * gap between them).
     *
     * Render code calls this before drawing something big: drawing big for a stack that
     * lost its footprint fight would visually overlap whatever those cells actually belong
     * to. A losing stack still renders - just at its own single cell, exactly like an
     * ordinary 1x1 item - which is the graceful degradation {@link #reconcile}'s own doc
     * already promised on the data side; this is that same promise kept on the render side.
     */
    public static boolean hasReservedFootprint(GridStorage storage, int start, int cols, int rows,
                                                int col, int row, Footprint footprint) {
        if (!footprint.isMultiCell()) return true; // nothing to contest for a 1x1 item
        if (col + footprint.width() > cols || row + footprint.height() > rows) return false;
        int anchorIdx = start + row * cols + col;
        for (int dr = 0; dr < footprint.height(); dr++) {
            for (int dc = 0; dc < footprint.width(); dc++) {
                if (dc == 0 && dr == 0) continue;
                ItemStack there = storage.get(start + (row + dr) * cols + (col + dc));
                if (!isReservation(there) || anchorOf(there) != anchorIdx) return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code footprint}'s rectangle, top-left at ({@code col}, {@code row}), is
     * entirely empty in this region - including the origin cell itself. A cell already
     * claimed as a shadow of some OTHER item counts as occupied here, the same as a real
     * item would: this is what stops one item's footprint quietly eating into a neighbour's,
     * which reconcile()'s scan-order tie-break would otherwise paper over invisibly.
     */
    public static boolean fits(GridStorage storage, int start, int cols, int rows,
                                int col, int row, Footprint footprint) {
        if (col < 0 || row < 0) return false;
        if (col + footprint.width() > cols || row + footprint.height() > rows) return false;
        for (int dr = 0; dr < footprint.height(); dr++) {
            for (int dc = 0; dc < footprint.width(); dc++) {
                int idx = start + (row + dr) * cols + (col + dc);
                if (!storage.get(idx).isEmpty()) return false;
            }
        }
        return true;
    }

    /**
     * Recomputes every shadow cell in {@code container}'s [{@code start}, start + cols*rows)
     * region from scratch.
     *
     * Pass 1 clears every existing reservation unconditionally - old shadows are never
     * trusted, only ever recomputed. Pass 2 walks the region in row-major order; each real
     * multi-cell item claims its footprint's remaining cells if they're all genuinely free,
     * or is quietly left at its own single cell if they're not (edge of the grid, or another
     * item already there). Scan order is what decides a contested cell when two items'
     * footprints would overlap - whichever comes first in reading order keeps it, and the
     * loser just renders as 1x1 until something changes. That degrade is deliberate: it is
     * strictly better than corrupting either item's stack, and it can only happen from a
     * state this method didn't itself create (fits() already refuses a placement that would
     * cause it).
     */
    public static void reconcile(GridStorage storage, int start, int cols, int rows) {
        int size = cols * rows;

        for (int i = 0; i < size; i++) {
            int idx = start + i;
            if (isReservation(storage.get(idx))) {
                storage.set(idx, ItemStack.EMPTY);
            }
        }

        for (int i = 0; i < size; i++) {
            int idx = start + i;
            ItemStack stack = storage.get(idx);
            if (stack.isEmpty()) continue;

            Footprint fp = footprintOf(stack);
            if (!fp.isMultiCell()) continue;

            int col = i % cols;
            int row = i / cols;
            if (col + fp.width() > cols || row + fp.height() > rows) continue;

            boolean clear = true;
            outer:
            for (int dr = 0; dr < fp.height(); dr++) {
                for (int dc = 0; dc < fp.width(); dc++) {
                    if (dc == 0 && dr == 0) continue;
                    if (!storage.get(start + (row + dr) * cols + (col + dc)).isEmpty()) {
                        clear = false;
                        break outer;
                    }
                }
            }
            if (!clear) continue;

            for (int dr = 0; dr < fp.height(); dr++) {
                for (int dc = 0; dc < fp.width(); dc++) {
                    if (dc == 0 && dr == 0) continue;
                    storage.set(start + (row + dr) * cols + (col + dc), reservationFor(idx));
                }
            }
        }
    }
}
