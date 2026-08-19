package com.dayzhud.mod.inventory;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.SlotItemHandler;
// Verified against Curios 5.x for 1.20.1: ICuriosItemHandler is under api.type.capability,
// but ICurioStacksHandler is under api.type.inventory - different subpackages.
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Container behind the extraction-shooter style inventory screen. Combines:
 *  - vanilla armor slots (helmet/chest/legs/feet) + offhand, positioned to line up with
 *    the corresponding body parts of the paperdoll model
 *  - every Curios slot registered for this player by any installed mod (mask, backpack,
 *    uniform, rings, etc.) - this mod no longer registers its own slot types, it just
 *    surfaces whatever Curios already knows about
 *  - the standard 27 main inventory slots + 9 hotbar slots
 *
 * Slot creation must stay deterministic between client and server, so nothing here may
 * depend on render-side state.
 *
 * LAYOUT NOTE: slot coordinates below are chosen to align with the paperdoll drawn in
 * TarkovInventoryScreen. If you move the model there, move these to match.
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Left equipment column, each row vertically aligned with a body part of the model.
    private static final int EQUIP_COL_X = 16;
    private static final int[] ARMOR_ROW_Y = {44, 72, 100, 128}; // head, chest, legs, feet

    // Right-hand column beside the model: offhand + the first few Curios slots.
    private static final int SIDE_COL_X = 140;
    private static final int SIDE_ROW_START_Y = 44;
    private static final int SIDE_ROW_SPACING = 28;
    private static final int SIDE_COL_MAX_ROWS = 4;

    // Grid below the model for the remaining Curios slots. With mods installed there can
    // easily be a dozen of these, so they get a proper grid rather than being crammed
    // into the columns beside the paperdoll.
    private static final int OVERFLOW_X = 16;
    private static final int OVERFLOW_Y = 166;
    private static final int OVERFLOW_COLS = 6;
    private static final int OVERFLOW_SPACING = 22;

    private static final int INV_X = 186;
    private static final int INV_Y = 44;
    private static final int HOTBAR_Y = 116;

    public final Player player;
    /** Screen-side info: where each Curios slot landed and what it's called, for labels/tooltips. */
    public final List<CurioSlotInfo> curioSlotInfos = new ArrayList<>();

    private final int inventoryStartIndex;

    public record CurioSlotInfo(String identifier, int x, int y) {}

    public TarkovInventoryMenu(int windowId, Inventory playerInventory) {
        super(TarkovMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.player = playerInventory.player;

        // --- Vanilla armor ---
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[i];
            int armorIndex = 39 - i; // vanilla ordering: head=39 .. feet=36
            addSlot(new ArmorRestrictedSlot(playerInventory, armorIndex, equipmentSlot,
                    EQUIP_COL_X, ARMOR_ROW_Y[i]));
        }

        // --- Offhand, top of the side column ---
        addSlot(new Slot(playerInventory, 40, SIDE_COL_X, SIDE_ROW_START_Y));

        // --- Curios: whatever any installed mod has registered for this player ---
        int sideRow = 1; // offhand took row 0
        int overflowIndex = 0;
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isPresent()) {
            Map<String, ICurioStacksHandler> allCurios = new LinkedHashMap<>(curiosOpt.get().getCurios());
            for (Map.Entry<String, ICurioStacksHandler> entry : allCurios.entrySet()) {
                ICurioStacksHandler handler = entry.getValue();
                if (handler == null) continue;
                for (int slotIdx = 0; slotIdx < handler.getStacks().getSlots(); slotIdx++) {
                    int sx, sy;
                    if (sideRow < SIDE_COL_MAX_ROWS) {
                        sx = SIDE_COL_X;
                        sy = SIDE_ROW_START_Y + sideRow * SIDE_ROW_SPACING;
                        sideRow++;
                    } else {
                        sx = OVERFLOW_X + (overflowIndex % OVERFLOW_COLS) * OVERFLOW_SPACING;
                        sy = OVERFLOW_Y + (overflowIndex / OVERFLOW_COLS) * OVERFLOW_SPACING;
                        overflowIndex++;
                    }
                    addSlot(new SlotItemHandler(handler.getStacks(), slotIdx, sx, sy));
                    curioSlotInfos.add(new CurioSlotInfo(entry.getKey(), sx, sy));
                }
            }
        }

        // --- Inventory (27) then hotbar (9). Recorded so quickMoveStack knows the range. ---
        this.inventoryStartIndex = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Shift-click behaviour.
     *
     * IMPORTANT: Minecraft calls this in a loop until it returns EMPTY. Any path that
     * moves nothing MUST return ItemStack.EMPTY, or the game locks up in an infinite
     * loop. (An earlier version of this method returned a non-empty stack after failing
     * to find a destination, which is exactly what caused the shift-click freeze.)
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();

        int invStart = inventoryStartIndex;
        int invEnd = slots.size();          // end of hotbar
        int hotbarStart = invEnd - 9;
        int equipEnd = invStart;            // armor + offhand + curios occupy [0, invStart)

        boolean moved;
        if (index < equipEnd) {
            // Equipment -> inventory
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
        } else {
            // Inventory/hotbar -> try equipment first (armor, offhand, any valid curio slot),
            // then fall back to moving between the main inventory and the hotbar.
            moved = moveItemStackTo(sourceStack, 0, equipEnd, false);
            if (!moved) {
                if (index < hotbarStart) {
                    moved = moveItemStackTo(sourceStack, hotbarStart, invEnd, false);
                } else {
                    moved = moveItemStackTo(sourceStack, invStart, hotbarStart, false);
                }
            }
        }

        if (!moved) return ItemStack.EMPTY; // nothing happened - must report EMPTY

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY; // no net change - also must report EMPTY
        }

        sourceSlot.onTake(player, sourceStack);
        return original;
    }

    /** Vanilla-style armor slot: only accepts items that actually fit that equipment slot. */
    private static class ArmorRestrictedSlot extends Slot {
        private final EquipmentSlot equipmentSlot;

        ArmorRestrictedSlot(Inventory inventory, int index, EquipmentSlot equipmentSlot, int x, int y) {
            super(inventory, index, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return equipmentSlot == LivingEntity.getEquipmentSlotForItem(stack)
                    || (equipmentSlot == EquipmentSlot.HEAD && stack.is(Items.CARVED_PUMPKIN));
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }
    }
}
