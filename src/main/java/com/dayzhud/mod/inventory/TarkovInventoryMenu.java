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
// but ICurioStacksHandler is under api.type.inventory - they're in different subpackages.
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The server/client-synced container behind the Tarkov-style inventory screen. Combines:
 *  - vanilla armor slots (helmet/chestplate/leggings/boots) + offhand
 *  - the three DayZ Curios slots this mod defines (face cover, headset, chest rig)
 *  - any OTHER Curios slot types other installed mods have registered for the player,
 *    laid out automatically so nothing is silently missing (this is the "shows all the
 *    slots available around the character" part)
 *  - the standard 27 main inventory slots + 9 hotbar slots ("Pockets")
 *
 * Both client and server construct this identically from the same player Inventory, so
 * slot order/count must stay deterministic between the two - don't make slot creation
 * depend on anything that could differ client vs server (e.g. render state).
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final Player player;
    public final Slot chestArmorSlot;
    public final Slot chestRigSlot;
    public final List<String> extraCurioIdentifiers = new ArrayList<>();

    public TarkovInventoryMenu(int windowId, Inventory playerInventory) {
        super(TarkovMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.player = playerInventory.player;

        // --- Vanilla armor slots ---
        Slot chestSlotRef = null;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[i];
            int armorIndex = 39 - i; // matches vanilla Inventory armor slot ordering (head=39..feet=36)
            Slot slot = new ArmorRestrictedSlot(playerInventory, armorIndex, equipmentSlot, 20, 20 + i * 24);
            addSlot(slot);
            if (equipmentSlot == EquipmentSlot.CHEST) chestSlotRef = slot;
        }
        this.chestArmorSlot = chestSlotRef;

        // Offhand
        addSlot(new Slot(playerInventory, 40, 68, 92));

        // --- Curios slots (face cover, headset, chest rig + anything else registered) ---
        Slot chestRig = null;
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isPresent()) {
            ICuriosItemHandler curios = curiosOpt.get();
            Map<String, ICurioStacksHandler> allCurios = new LinkedHashMap<>(curios.getCurios());

            addCurioSlot(allCurios, ModCurios.FACE_COVER, 44, 20);
            addCurioSlot(allCurios, ModCurios.HEADSET, 68, 44);
            chestRig = addCurioSlot(allCurios, ModCurios.CHEST_RIG, 44, 44);

            // Anything left over came from another mod's slot type - lay it out automatically
            // in an overflow row so it's still reachable, rather than silently hidden.
            int overflowX = 20, overflowY = 120, col = 0;
            for (String identifier : allCurios.keySet()) {
                extraCurioIdentifiers.add(identifier);
                ICurioStacksHandler handler = curios.getCurios().get(identifier);
                if (handler == null) continue;
                for (int slotIdx = 0; slotIdx < handler.getStacks().getSlots(); slotIdx++) {
                    addSlot(new SlotItemHandler(handler.getStacks(), slotIdx, overflowX + (col % 6) * 24, overflowY + (col / 6) * 24));
                    col++;
                }
            }
        }
        this.chestRigSlot = chestRig;

        // --- Pockets: standard player inventory (27 main + 9 hotbar) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 140 + col * 18, 20 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 140 + col * 18, 132));
        }
    }

    /** Adds a Curios slot if that identifier is present for this player, removing it from the map so it isn't double-placed in the overflow pass. */
    private Slot addCurioSlot(Map<String, ICurioStacksHandler> allCurios, String identifier, int x, int y) {
        ICurioStacksHandler handler = allCurios.remove(identifier);
        if (handler == null || handler.getStacks().getSlots() == 0) return null;
        Slot slot = new SlotItemHandler(handler.getStacks(), 0, x, y);
        addSlot(slot);
        return slot;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Basic shift-click support between Pockets (main inv + hotbar) only for now -
        // shift-clicking into armor/curios slots isn't wired up yet, matching this being
        // a visual-first pass. Regular click-drag into any slot still works normally.
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack result = sourceStack.copy();

        int pocketsStart = slots.size() - 36;
        int pocketsEnd = slots.size();

        if (index >= pocketsStart && index < pocketsEnd) {
            // From Pockets - try armor slots if it's wearable, otherwise just leave it (no
            // separate "storage" container in this pass to move it into).
            EquipmentSlot fittingSlot = LivingEntity.getEquipmentSlotForItem(sourceStack);
            if (fittingSlot != null && fittingSlot.getType() == EquipmentSlot.Type.ARMOR) {
                for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                    if (ARMOR_SLOTS[i] == fittingSlot && !moveItemStackTo(sourceStack, i, i + 1, false)) {
                        break;
                    }
                }
            }
        } else {
            if (!moveItemStackTo(sourceStack, pocketsStart, pocketsEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return result;
    }

    /** Vanilla-style armor slot: only accepts items that actually fit that equipment slot. */
    private static class ArmorRestrictedSlot extends Slot {
        private final EquipmentSlot equipmentSlot;

        ArmorRestrictedSlot(Inventory inventory, int index, EquipmentSlot equipmentSlot, int x, int y) {
            super(inventory, index, x, y);
            this.equipmentSlot = equipmentSlot;
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
