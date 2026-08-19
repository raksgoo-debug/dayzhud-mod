package com.dayzhud.mod.inventory;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
// Verified against Curios 5.x for 1.20.1: ICuriosItemHandler is under api.type.capability,
// but ICurioStacksHandler is under api.type.inventory - different subpackages.
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Container behind the extraction-shooter style inventory screen.
 *
 * SLOT ALIGNMENT: the armor column's Y values are hand-tuned so each slot sits level with
 * where that piece actually appears on the paperdoll model drawn in TarkovInventoryScreen
 * (helmet by the head, boots by the feet, etc). If you move or rescale the model there,
 * these need retuning to match - they are not derived automatically.
 *
 * BACKPACK CONTENTS CAVEAT: if an equipped curio exposes an item-handler capability (most
 * backpack mods do), its contents are surfaced as extra slots under the inventory grid.
 * Because a container's slot list is fixed once the menu opens, those slots only appear/
 * update when the screen is reopened - equipping a backpack while the screen is already
 * open won't grow it until you close and reopen.
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Left armor column - Y values aligned to the model's body parts (see class note).
    private static final int EQUIP_COL_X = 16;
    private static final int[] ARMOR_ROW_Y = {54, 84, 112, 134}; // head, chest, legs, feet

    // Right column beside the model. Only two entries, deliberately paired with the armor
    // rows they sit level with: mask beside the helmet, backpack beside the chestplate.
    private static final int SIDE_COL_X = 140;
    private static final int SIDE_MASK_Y = 54;      // level with helmet
    private static final int SIDE_BACKPACK_Y = 84;  // level with chestplate

    // Everything else Curios reports goes in the GEAR grid below the paperdoll.
    private static final int GEAR_X = 16;
    private static final int GEAR_Y = 166;
    private static final int GEAR_COLS = 6;
    private static final int GEAR_SPACING = 22;

    private static final int INV_X = 186;
    private static final int INV_Y = 44;
    private static final int HOTBAR_Y = 116;

    // Backpack contents grid, under the inventory/hotbar on the right side.
    private static final int BACKPACK_X = 186;
    private static final int BACKPACK_Y = 150;
    private static final int BACKPACK_COLS = 9;
    private static final int BACKPACK_MAX_SLOTS = 27; // cap so a huge bag can't overflow the panel

    /** Identifier fragments used to pick which curio sits in each aligned side position. */
    private static final String[] MASK_KEYS = {"mask", "face"};
    private static final String[] BACKPACK_KEYS = {"back", "backpack"};

    public final Player player;
    public final List<CurioSlotInfo> curioSlotInfos = new ArrayList<>();
    /** Where the offhand slot ended up, so the screen can label it. */
    public final int offhandX, offhandY;
    /** How many backpack-content slots were added (0 if no container-style curio equipped). */
    public final int backpackSlotCount;

    private final int inventoryStartIndex;

    public record CurioSlotInfo(String identifier, int x, int y) {}

    private record PendingCurio(String identifier, ICurioStacksHandler handler, int slotIndex) {}

    public TarkovInventoryMenu(int windowId, Inventory playerInventory) {
        super(TarkovMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.player = playerInventory.player;

        // --- Vanilla armor, aligned to the model ---
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            addSlot(new ArmorRestrictedSlot(playerInventory, 39 - i, ARMOR_SLOTS[i],
                    EQUIP_COL_X, ARMOR_ROW_Y[i]));
        }

        // --- Collect every Curios slot the player has ---
        List<PendingCurio> pending = new ArrayList<>();
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isPresent()) {
            Map<String, ICurioStacksHandler> allCurios = new LinkedHashMap<>(curiosOpt.get().getCurios());
            for (Map.Entry<String, ICurioStacksHandler> entry : allCurios.entrySet()) {
                ICurioStacksHandler handler = entry.getValue();
                if (handler == null) continue;
                for (int slotIdx = 0; slotIdx < handler.getStacks().getSlots(); slotIdx++) {
                    pending.add(new PendingCurio(entry.getKey(), handler, slotIdx));
                }
            }
        }

        // --- Pull out mask + backpack so they sit level with helmet/chestplate ---
        PendingCurio mask = takeFirstMatching(pending, MASK_KEYS);
        PendingCurio backpack = takeFirstMatching(pending, BACKPACK_KEYS);

        if (mask != null) addCurio(mask, SIDE_COL_X, SIDE_MASK_Y);
        if (backpack != null) addCurio(backpack, SIDE_COL_X, SIDE_BACKPACK_Y);

        // --- Remaining curios fill the GEAR grid ---
        for (int i = 0; i < pending.size(); i++) {
            PendingCurio pc = pending.get(i);
            int gx = GEAR_X + (i % GEAR_COLS) * GEAR_SPACING;
            int gy = GEAR_Y + (i / GEAR_COLS) * GEAR_SPACING;
            addCurio(pc, gx, gy);
        }

        // --- Offhand moved down beside the weapon row, where a held item belongs ---
        this.offhandX = 136;
        this.offhandY = 224;
        addSlot(new Slot(playerInventory, 40, offhandX, offhandY));

        // --- Inventory (27) then hotbar (9) ---
        this.inventoryStartIndex = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        // --- Backpack contents, if the equipped backpack curio exposes an item handler ---
        this.backpackSlotCount = addBackpackContents(backpack);
    }

    /**
     * If the given curio holds an item that exposes Forge's item-handler capability (how
     * essentially every backpack mod stores its contents), surface those as real slots.
     * Returns how many were added.
     */
    private int addBackpackContents(PendingCurio backpackCurio) {
        if (backpackCurio == null) return 0;
        ItemStack bagStack = backpackCurio.handler().getStacks().getStackInSlot(backpackCurio.slotIndex());
        if (bagStack.isEmpty()) return 0;

        Optional<IItemHandler> bagInv = bagStack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (bagInv.isEmpty()) return 0;

        IItemHandler handler = bagInv.get();
        int count = Math.min(handler.getSlots(), BACKPACK_MAX_SLOTS);
        for (int i = 0; i < count; i++) {
            addSlot(new SlotItemHandler(handler, i,
                    BACKPACK_X + (i % BACKPACK_COLS) * 18,
                    BACKPACK_Y + (i / BACKPACK_COLS) * 18));
        }
        return count;
    }

    private PendingCurio takeFirstMatching(List<PendingCurio> pending, String[] keys) {
        for (int i = 0; i < pending.size(); i++) {
            String id = pending.get(i).identifier().toLowerCase(Locale.ROOT);
            for (String key : keys) {
                if (id.contains(key)) return pending.remove(i);
            }
        }
        return null;
    }

    private void addCurio(PendingCurio pc, int x, int y) {
        addSlot(new SlotItemHandler(pc.handler().getStacks(), pc.slotIndex(), x, y));
        curioSlotInfos.add(new CurioSlotInfo(pc.identifier(), x, y));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Shift-click behaviour.
     *
     * IMPORTANT: Minecraft calls this in a loop until it returns EMPTY. Any path that
     * moves nothing MUST return ItemStack.EMPTY, or the game locks up in an infinite loop.
     * (An earlier version returned a non-empty stack after failing to find a destination,
     * which is exactly what caused the shift-click freeze.)
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();

        int equipEnd = inventoryStartIndex;                  // armor + curios + offhand
        int invStart = inventoryStartIndex;
        int hotbarStart = invStart + 27;
        int invEnd = hotbarStart + 9;                        // end of hotbar
        int bagStart = invEnd;
        int bagEnd = invEnd + backpackSlotCount;

        boolean moved;
        if (index < equipEnd) {
            // Equipment -> inventory, then backpack
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
            if (!moved && backpackSlotCount > 0) {
                moved = moveItemStackTo(sourceStack, bagStart, bagEnd, false);
            }
        } else if (index >= bagStart && index < bagEnd) {
            // Backpack -> inventory
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
        } else {
            // Inventory/hotbar -> equipment, then backpack, then the other inventory row set
            moved = moveItemStackTo(sourceStack, 0, equipEnd, false);
            if (!moved && backpackSlotCount > 0) {
                moved = moveItemStackTo(sourceStack, bagStart, bagEnd, false);
            }
            if (!moved) {
                if (index < hotbarStart) {
                    moved = moveItemStackTo(sourceStack, hotbarStart, invEnd, false);
                } else {
                    moved = moveItemStackTo(sourceStack, invStart, hotbarStart, false);
                }
            }
        }

        if (!moved) return ItemStack.EMPTY;

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
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
