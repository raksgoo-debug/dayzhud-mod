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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Container behind the extraction-shooter style inventory screen.
 *
 * LAYOUT: the armor column uses even {@link #EQUIP_SPACING} spacing, and the paperdoll in
 * TarkovInventoryScreen is positioned/scaled to line its body parts up with those rows.
 * Move one and the other needs retuning - the alignment is hand-matched, not derived.
 *
 * BACKPACK: contents of the bag worn in the "back" Curios slot appear as a grid on the
 * right. These slots always exist (a container's slot list is fixed at open time) but
 * switch themselves on/off via {@link BackpackSlot#isActive()} against a live view of the
 * worn bag - so swapping bags updates the grid immediately, no reopen required.
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * Curios slot types deliberately not shown. These duplicate functionality already
     * covered by other slots (mask items go in "mask", bags go in "back"), so surfacing
     * them just adds dead squares. Matched on the exact identifier.
     *
     * NOTE: hiding a slot here does not delete it from Curios - anything already stored in
     * one stays there and simply isn't reachable through this screen. Clear such slots
     * before adding an identifier to this list.
     */
    private static final Set<String> HIDDEN_CURIO_IDS = Set.of("backpack", "chest_rig", "face_cover");

    /** Matched exactly, so "back" doesn't accidentally also grab "backpack". */
    private static final String MASK_ID = "mask";
    private static final String BACK_ID = "back";

    // Equipment columns - even spacing, aligned to the paperdoll's body parts.
    private static final int EQUIP_COL_X = 18;
    private static final int SIDE_COL_X = 138;
    private static final int EQUIP_START_Y = 48;
    private static final int EQUIP_SPACING = 26;

    private static final int GEAR_X = 18;
    private static final int GEAR_Y = 168;
    private static final int GEAR_COLS = 6;
    private static final int GEAR_SPACING = 22;

    private static final int INV_X = 186;
    private static final int INV_Y = 44;
    private static final int HOTBAR_Y = 116;

    private static final int BACKPACK_X = 186;
    private static final int BACKPACK_Y = 152;
    private static final int BACKPACK_COLS = 9;
    /** Fixed allocation; bigger bags are shown up to this many slots. */
    public static final int BACKPACK_MAX_SLOTS = 27;

    public final Player player;
    public final List<CurioSlotInfo> curioSlotInfos = new ArrayList<>();
    public final int offhandX, offhandY;
    public final BackCurioItemHandler backpackHandler;

    private final int inventoryStartIndex;
    private final int backpackStartIndex;

    public record CurioSlotInfo(String identifier, int x, int y) {}

    private record PendingCurio(String identifier, ICurioStacksHandler handler, int slotIndex) {}

    public TarkovInventoryMenu(int windowId, Inventory playerInventory) {
        super(TarkovMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.player = playerInventory.player;
        this.backpackHandler = new BackCurioItemHandler(player);

        // --- Vanilla armor: evenly spaced left column ---
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            addSlot(new ArmorRestrictedSlot(playerInventory, 39 - i, ARMOR_SLOTS[i],
                    EQUIP_COL_X, EQUIP_START_Y + i * EQUIP_SPACING));
        }

        // --- Gather Curios slots, dropping the redundant ones ---
        List<PendingCurio> pending = new ArrayList<>();
        Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOpt.isPresent()) {
            Map<String, ICurioStacksHandler> allCurios = new LinkedHashMap<>(curiosOpt.get().getCurios());
            for (Map.Entry<String, ICurioStacksHandler> entry : allCurios.entrySet()) {
                String id = entry.getKey().toLowerCase(Locale.ROOT);
                if (HIDDEN_CURIO_IDS.contains(id)) continue;
                ICurioStacksHandler handler = entry.getValue();
                if (handler == null) continue;
                for (int slotIdx = 0; slotIdx < handler.getStacks().getSlots(); slotIdx++) {
                    pending.add(new PendingCurio(entry.getKey(), handler, slotIdx));
                }
            }
        }

        // Mask sits level with the helmet, back (bag) level with the chestplate.
        PendingCurio mask = takeExact(pending, MASK_ID);
        PendingCurio back = takeExact(pending, BACK_ID);
        if (mask != null) addCurio(mask, SIDE_COL_X, EQUIP_START_Y);
        if (back != null) addCurio(back, SIDE_COL_X, EQUIP_START_Y + EQUIP_SPACING);

        // Everything else drops into the GEAR grid.
        for (int i = 0; i < pending.size(); i++) {
            addCurio(pending.get(i),
                    GEAR_X + (i % GEAR_COLS) * GEAR_SPACING,
                    GEAR_Y + (i / GEAR_COLS) * GEAR_SPACING);
        }

        // --- Offhand, level with the weapon mirror row ---
        this.offhandX = 136;
        this.offhandY = 226;
        addSlot(new Slot(playerInventory, 40, offhandX, offhandY));

        // --- Inventory (27) + hotbar (9) ---
        this.inventoryStartIndex = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        // --- Backpack contents: always allocated, self-disabling when no bag is worn ---
        this.backpackStartIndex = slots.size();
        for (int i = 0; i < BACKPACK_MAX_SLOTS; i++) {
            addSlot(new BackpackSlot(backpackHandler, i,
                    BACKPACK_X + (i % BACKPACK_COLS) * 18,
                    BACKPACK_Y + (i / BACKPACK_COLS) * 18));
        }
    }

    /** How many backpack slots are live right now (0 when nothing suitable is worn). */
    public int getActiveBackpackSlots() {
        return Math.min(backpackHandler.getSlots(), BACKPACK_MAX_SLOTS);
    }

    private PendingCurio takeExact(List<PendingCurio> pending, String identifier) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).identifier().toLowerCase(Locale.ROOT).equals(identifier)) {
                return pending.remove(i);
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
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();

        int equipEnd = inventoryStartIndex;
        int invStart = inventoryStartIndex;
        int hotbarStart = invStart + 27;
        int invEnd = hotbarStart + 9;
        int bagStart = backpackStartIndex;
        int bagEnd = bagStart + BACKPACK_MAX_SLOTS;

        boolean moved;
        if (index < equipEnd) {
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
            if (!moved) moved = moveItemStackTo(sourceStack, bagStart, bagEnd, false);
        } else if (index >= bagStart && index < bagEnd) {
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
        } else {
            moved = moveItemStackTo(sourceStack, 0, equipEnd, false);
            if (!moved) moved = moveItemStackTo(sourceStack, bagStart, bagEnd, false);
            if (!moved) {
                moved = index < hotbarStart
                        ? moveItemStackTo(sourceStack, hotbarStart, invEnd, false)
                        : moveItemStackTo(sourceStack, invStart, hotbarStart, false);
            }
        }

        if (!moved) return ItemStack.EMPTY;

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == original.getCount()) return ItemStack.EMPTY;

        sourceSlot.onTake(player, sourceStack);
        return original;
    }

    /** Backpack slot that switches itself off when the worn bag is smaller or absent. */
    private static class BackpackSlot extends SlotItemHandler {
        private final BackCurioItemHandler bag;
        private final int slotIndex;

        BackpackSlot(BackCurioItemHandler bag, int index, int x, int y) {
            super(bag, index, x, y);
            this.bag = bag;
            this.slotIndex = index;
        }

        @Override
        public boolean isActive() {
            return slotIndex < bag.getSlots();
        }
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
