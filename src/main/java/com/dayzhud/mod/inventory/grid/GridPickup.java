package com.dayzhud.mod.inventory.grid;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.WeaponSlots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Walking over a multi-cell item: put it where its footprint fits, or leave it on the ground.
 *
 * Vanilla auto-pickup drops a stack into the first empty slot it finds. Shadow cells are
 * real (invisible) items, so it never lands on another item's footprint - but it did land in
 * any single free cell, including one where the new item's own footprint doesn't fit; the
 * next reconcile then left it as a 1x1. A gun picked up into a full inventory "shrank to one
 * slot" (2.13.11 report). So a multi-cell pickup is placed here instead, in order:
 * <ol>
 *   <li>an empty loadout slot that takes it (a gun still lands in its weapon slot, as vanilla
 *       filling hotbar 0-3 first did);</li>
 *   <li>the inventory grid, wherever the whole footprint fits - as it lies, else turned;</li>
 *   <li>a free ordinary hotbar slot (one cell, like anything carried in the hotbar);</li>
 *   <li>nowhere: the pickup is refused and the item stays on the ground.</li>
 * </ol>
 * A 1x1 item, and a stack that tops up ones already carried (no new cell needed), go through
 * vanilla untouched. Runs late, so anything that cancels a pickup first (cash into the wallet)
 * still does.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class GridPickup {

    /** The player's inventory grid: container slots 9-35, 9 wide, 3 tall. */
    private static final int MAIN_START = 9, COLS = 9, ROWS = 3;
    private static final int HOTBAR = 9;

    private GridPickup() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPickup(EntityItemPickupEvent event) {
        if (!GridConfig.ENABLED.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || ItemGrid.isReservation(stack)) return;
        Footprint fp = ItemFootprints.baseFootprintOf(stack);
        if (!fp.isMultiCell()) return;
        Inventory inv = player.getInventory();
        if (mergeRoom(inv, stack) >= stack.getCount()) return;

        int slot = -1;
        boolean rotated = false;
        for (int i = 0; i < WeaponSlots.ORDER.length && slot < 0; i++) {
            if (inv.getItem(i).isEmpty() && WeaponSlots.ORDER[i].accepts(stack)) slot = i;
        }
        GridStorage grid = GridStorage.of(inv);
        if (slot < 0) {
            slot = findFit(grid, fp);
            if (slot < 0 && fp.width() != fp.height()) {
                slot = findFit(grid, fp.rotated());
                rotated = slot >= 0;
            }
        }
        for (int i = WeaponSlots.ORDER.length; i < HOTBAR && slot < 0; i++) {
            if (inv.getItem(i).isEmpty()) slot = i;
        }

        // Handled here either way: placed below, or refused (stays on the ground).
        event.setCanceled(true);
        if (slot < 0) return;

        ItemStack placed = stack.copy();
        ItemGrid.setRotated(placed, rotated);
        inv.setItem(slot, placed);
        // Claim its footprint now, not at the next reconcile - two items picked up in the same
        // tick must not both be given the same free rectangle.
        if (slot >= MAIN_START) ItemGrid.reconcile(grid, MAIN_START, COLS, ROWS);

        // What vanilla's own pickup does after a successful add.
        int count = stack.getCount();
        ForgeEventFactory.firePlayerItemPickupEvent(player, entity, placed.copy());
        player.take(entity, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), count);
        player.onItemPickup(entity);
        entity.discard();
    }

    /** Container index of the first anchor where {@code fp} fits in the grid, or -1. */
    private static int findFit(GridStorage grid, Footprint fp) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (ItemGrid.fits(grid, MAIN_START, COLS, ROWS, col, row, fp)) return MAIN_START + row * COLS + col;
            }
        }
        return -1;
    }

    /** How many of {@code stack} the stacks already carried could still take. */
    private static int mergeRoom(Inventory inv, ItemStack stack) {
        if (!stack.isStackable()) return 0;
        int room = 0;
        for (ItemStack s : inv.items) room += roomIn(inv, s, stack);
        for (ItemStack s : inv.offhand) room += roomIn(inv, s, stack);
        return room;
    }

    private static int roomIn(Inventory inv, ItemStack held, ItemStack incoming) {
        if (held.isEmpty() || !ItemStack.isSameItemSameTags(held, incoming)) return 0;
        return Math.max(0, Math.min(held.getMaxStackSize(), inv.getMaxStackSize()) - held.getCount());
    }
}
