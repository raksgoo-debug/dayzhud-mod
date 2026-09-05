package com.dayzhud.mod.inventory;

import com.dayzhud.mod.search.SearchProgress;
import com.dayzhud.mod.search.SearchedContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.IItemHandler;
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
    private static final int EQUIP_START_Y = 44;
    private static final int EQUIP_SPACING = 26;

    private static final int GEAR_X = 18;
    private static final int GEAR_Y = 170;
    private static final int GEAR_COLS = 6;
    private static final int GEAR_SPACING = 22;

    // 2x2 crafting grid + result, bottom-left under GEAR.
    private static final int CRAFT_X = 20;
    private static final int CRAFT_Y = 234;
    public static final int CRAFT_RESULT_X = 92;
    public static final int CRAFT_RESULT_Y = 243;

    // Right-hand container grid, present only when a chest/crate was opened.
    public static final int CONTAINER_X = 372;
    public static final int CONTAINER_Y = 26;
    public static final int CONTAINER_COLS = 9;

    // --- Corpse view (Ragdollified). Mirrors the player panel's shape on the right. ---
    public static final int CORPSE_ARMOR_X = 380;
    public static final int CORPSE_SIDE_X = 502;
    public static final int CORPSE_EQUIP_START_Y = 28;
    public static final int CORPSE_EQUIP_SPACING = 20;
    public static final int CORPSE_GEAR_X = 380;
    public static final int CORPSE_GEAR_Y = 122;
    public static final int CORPSE_GEAR_COLS = 6;
    public static final int CORPSE_GEAR_SPACING = 22;
    public static final int CORPSE_INV_X = 380;
    /** Corpse's own inventory: 3 main rows plus its hotbar row, always fully visible. */
    public static final int CORPSE_INV_Y = 178;
    public static final int CORPSE_LOOT_COLS = 9;
    /**
     * The corpse's loot is ONE continuous scrolling list: inventory rows, then its hotbar
     * row, then the worn backpack's contents. Slot positions can't move (Slot's x/y are
     * final), so scrolling works by remapping which item each fixed slot shows - which
     * only works if the whole region is a single uniform grid, hence one list rather than
     * separate fixed sections.
     */
    /** Corpse's own hotbar row, its own labelled section under the inventory rows. */
    public static final int CORPSE_HOTBAR_Y = 250;
    /**
     * Backpack section. Everything else on the corpse side is sized to be fully visible;
     * only the bag can genuinely overflow (bags run up to 42 slots), so it alone scrolls.
     */
    public static final int CORPSE_BAG_Y = 288;
    public static final int CORPSE_BAG_VISIBLE_ROWS = 3;
    public static final int CORPSE_BAG_SLOTS = CORPSE_LOOT_COLS * CORPSE_BAG_VISIBLE_ROWS;

    /** Corpse container index layout - verified against Ragdollified's CorpseMenu. */
    private static final int CORPSE_MAIN_START = 9;   // 9..35 main inventory
    private static final int CORPSE_HOTBAR_START = 0; // 0..8 hotbar
    private static final int CORPSE_ARMOR_FEET = 36;  // 36 feet .. 39 head
    private static final int CORPSE_OFFHAND = 40;
    private static final int CORPSE_CURIO_START = 41;

    private static final int INV_X = 186;
    private static final int INV_Y = 26;
    private static final int HOTBAR_Y = 100;

    private static final int BACKPACK_X = 186;
    private static final int BACKPACK_Y = 138;
    private static final int BACKPACK_COLS = 9;
    /** Rows shown at once; anything larger scrolls rather than spilling over the stat strip. */
    public static final int BACKPACK_VISIBLE_ROWS = 4;
    public static final int BACKPACK_MAX_SLOTS = BACKPACK_COLS * BACKPACK_VISIBLE_ROWS;

    public final Player player;
    public final List<CurioSlotInfo> curioSlotInfos = new ArrayList<>();
    public final int offhandX, offhandY;
    public final BackCurioItemHandler backpackHandler;
    /** The opened chest/crate/corpse, or null when this is just the player inventory screen. */
    public final Container openedContainer;
    public final int containerRows;
    /** The corpse's curio slot ids, in container order. EMPTY for a corpse wearing none. */
    public final List<String> corpseCurioIds;
    /**
     * Whether {@link #openedContainer} is a corpse, and so gets the gear-style layout with
     * the paperdoll instead of a flat grid.
     *
     * Passed in explicitly, NOT inferred from corpseCurioIds being non-empty. It used to be
     * inferred, which meant a corpse wearing no curios at all - a bare NPC, very common -
     * failed the test and was drawn as an ordinary chest. The curio list describes what the
     * corpse is WEARING; it can legitimately be empty, and that says nothing about whether
     * the container is a corpse.
     */
    private final boolean corpseLayout;
    public CorpseLootHandler corpseLoot;
    public ScrollingBackpackView corpseLootView;
    private final DataSlot corpseBagSlots = DataSlot.standalone();


    /** The corpse container this menu was opened over, kept for the backpack gate below. */
    private Container corpseContainer;
    public final ScrollingBackpackView backpackView;

    private final int inventoryStartIndex;
    private final int backpackStartIndex;
    private final int craftStartIndex;
    private final int containerStartIndex;

    /**
     * The loot container behind this menu, when it is one that has to be searched.
     *
     * Held as the SearchedContainer's delegate rather than the wrapper: search progress is
     * keyed on the real container so it survives the wrapper being rebuilt each time the
     * screen is opened.
     */
    private Container searchedContainer;
    private Container searchedView;

    /**
     * Backpack slot count, computed server-side and synced. The client can't work this out
     * itself because some mods' bag capabilities never reach the client (see
     * BackCurioItemHandler's class notes).
     */
    private final DataSlot backpackSlotCount = DataSlot.standalone();

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 2, 2);
    private final ResultContainer resultSlots = new ResultContainer();

    public record CurioSlotInfo(String identifier, int x, int y) {}

    private record PendingCurio(String identifier, ICurioStacksHandler handler, int slotIndex) {}

    public TarkovInventoryMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, null);
    }

    /**
     * @param container the chest/crate being viewed, or null for the plain inventory screen.
     *                  Client and server MUST build this menu with the same slot count, so
     *                  the client passes a SimpleContainer of the size sent in the open packet.
     */
    public TarkovInventoryMenu(int windowId, Inventory playerInventory, Container container) {
        this(windowId, playerInventory, container, List.of(), false);
    }

    /**
     * @param curioIds the corpse's curio slot ids, in container order. May be empty - a
     *                 corpse wearing no curios is perfectly normal.
     * @param isCorpse true to lay {@code container} out as a Ragdollified corpse
     *                 (armor/gear/inventory/hotbar plus the paperdoll) instead of a plain
     *                 grid. Both sides MUST pass the same value or the slot counts diverge
     *                 and sync breaks, so it travels in the open packet.
     */
    public TarkovInventoryMenu(int windowId, Inventory playerInventory, Container container,
                               List<String> curioIds, boolean isCorpse) {
        super(TarkovMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.player = playerInventory.player;
        this.openedContainer = container;
        this.corpseCurioIds = List.copyOf(curioIds);
        this.corpseLayout = isCorpse && container != null;
        this.containerRows = container == null ? 0 : Math.max(1, container.getContainerSize() / CONTAINER_COLS);
        if (container != null) {
            container.startOpen(player);
        }
        this.backpackHandler = new BackCurioItemHandler(player);
        this.backpackView = new ScrollingBackpackView(backpackHandler, BACKPACK_COLS, BACKPACK_VISIBLE_ROWS);
        this.backpackHandler.setSyncedSlotCount(backpackSlotCount::get);
        addDataSlot(backpackSlotCount);

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
        this.offhandY = 296;
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
            addSlot(new BackpackSlot(backpackView, i,
                    BACKPACK_X + (i % BACKPACK_COLS) * 18,
                    BACKPACK_Y + (i / BACKPACK_COLS) * 18));
        }

        // --- 2x2 crafting: result first, then the grid (mirrors vanilla's ordering) ---
        this.craftStartIndex = slots.size();
        addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, CRAFT_RESULT_X, CRAFT_RESULT_Y));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                addSlot(new Slot(craftSlots, col + row * 2, CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }

        // --- Opened container, laid out to the right of everything else ---
        this.containerStartIndex = slots.size();
        if (container instanceof com.dayzhud.mod.search.SearchedContainer searched) {
            // TWO references, deliberately. The slots are built on the WRAPPER, so that is what
            // slot.container compares equal to; progress is keyed on the DELEGATE, because the
            // wrapper is rebuilt per menu while the corpse's container is not. Storing only the
            // delegate meant maskedBodyMenuSlots matched no slots at all and the mask came out
            // empty - items hidden, nothing drawn over them.
            this.searchedContainer = searched.delegate();
            this.searchedView = searched;
        }
        if (container != null) {
            if (isCorpse()) {
                addCorpseSlots(container);
            } else {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    addSlot(new Slot(container, i,
                            CONTAINER_X + (i % CONTAINER_COLS) * 18,
                            CONTAINER_Y + (i / CONTAINER_COLS) * 18));
                }
            }
        }
    }

    /**
     * Corpse layout: armor column + offhand/curio side column beside a paperdoll, a gear
     * grid, then the corpse's own inventory and hotbar - mirroring the player's own panel
     * so both sides of the screen read the same way.
     */
    private void addCorpseSlots(Container corpse) {
        // Armor, head at the top (container stores feet-first, so count down).
        for (int i = 0; i < 4; i++) {
            addSlot(new Slot(corpse, CORPSE_ARMOR_FEET + 3 - i,
                    CORPSE_ARMOR_X, CORPSE_EQUIP_START_Y + i * CORPSE_EQUIP_SPACING));
        }
        // Offhand at the top of the side column.
        addSlot(new Slot(corpse, CORPSE_OFFHAND, CORPSE_SIDE_X, CORPSE_EQUIP_START_Y));

        // Curios: first two beside the paperdoll, the rest in the gear grid.
        int curioCount = corpseCurioIds.size();
        int sideRow = 1;
        int gearIndex = 0;
        for (int i = 0; i < curioCount; i++) {
            int slotIndex = CORPSE_CURIO_START + i;
            if (slotIndex >= corpse.getContainerSize()) break;
            if (sideRow < 3) {
                addSlot(new Slot(corpse, slotIndex, CORPSE_SIDE_X,
                        CORPSE_EQUIP_START_Y + sideRow * CORPSE_EQUIP_SPACING));
                sideRow++;
            } else {
                addSlot(new Slot(corpse, slotIndex,
                        CORPSE_GEAR_X + (gearIndex % CORPSE_GEAR_COLS) * CORPSE_GEAR_SPACING,
                        CORPSE_GEAR_Y + (gearIndex / CORPSE_GEAR_COLS) * CORPSE_GEAR_SPACING));
                gearIndex++;
            }
        }

        // The corpse's own inventory and hotbar are fixed, labelled sections - they always
        // fit, so there's nothing to gain from scrolling them.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(corpse, CORPSE_MAIN_START + col + row * 9,
                        CORPSE_INV_X + col * 18, CORPSE_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(corpse, CORPSE_HOTBAR_START + col,
                    CORPSE_INV_X + col * 18, CORPSE_HOTBAR_Y));
        }

        // The worn backpack is the one part that can overflow, so it gets its own
        // scrollable section underneath.
        this.corpseContainer = corpse;
        this.corpseLoot = new CorpseLootHandler(corpse, corpseCurioIds, CORPSE_CURIO_START,
                player.level().isClientSide);
        this.corpseLoot.setSyncedBagSlots(corpseBagSlots::get);
        addDataSlot(corpseBagSlots);

        this.corpseLootView = new ScrollingBackpackView(corpseLoot, CORPSE_LOOT_COLS,
                CORPSE_BAG_VISIBLE_ROWS);
        // Window onto the bag portion only - the inventory rows above are real slots.
        this.corpseLootView.setRange(CorpseLootHandler.BASE_COUNT, () -> corpseLoot.bagSlots());

        for (int i = 0; i < CORPSE_BAG_SLOTS; i++) {
            addSlot(new CorpseLootSlot(corpseLootView, i,
                    CORPSE_INV_X + (i % CORPSE_LOOT_COLS) * 18,
                    CORPSE_BAG_Y + (i / CORPSE_LOOT_COLS) * 18));
        }

    }

    /** True when the corpse is wearing a bag we can look inside. */
    /** The container being searched, or null when this menu has nothing to search. */
    public Container searchedContainer() {
        return searchedContainer;
    }

    /** Menu index of the first slot belonging to the searched container. */
    /**
     * The order the search works through a corpse: equipment, then inventory, then hotbar,
     * then the bag.
     *
     * The container's own numbering is hotbar first (0-8), then main (9-35), then armour
     * (36-39) - so walking it in index order searched the belt before the body armour, which
     * is backwards from how anyone loots. This returns container indices in the order a player
     * would actually go through them; a non-corpse container has no such expectation and is
     * left in its natural order.
     */
    public int[] searchOrder() {
        Container searched = searchedContainer;
        if (searched == null) return new int[0];
        if (!corpseLayout) {
            int[] plain = new int[searched.getContainerSize()];
            for (int i = 0; i < plain.length; i++) plain[i] = i;
            return plain;
        }
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 3; i >= 0; i--) order.add(CORPSE_ARMOR_FEET + i);   // head down to feet
        order.add(CORPSE_OFFHAND);
        for (int i = CORPSE_CURIO_START; i < searched.getContainerSize(); i++) order.add(i);
        for (int i = CORPSE_MAIN_START; i < CORPSE_MAIN_START + 27; i++) order.add(i);
        for (int i = CORPSE_HOTBAR_START; i < CORPSE_HOTBAR_START + 9; i++) order.add(i);

        int[] out = new int[order.size()];
        for (int i = 0; i < out.length; i++) out[i] = order.get(i);
        return out;
    }


    public boolean corpseHasBackpack() {
        return corpseLoot != null && corpseLoot.bagSlots() > 0;
    }

    /** Loot slot that switches off past the end of the corpse's contents. */
    private class CorpseLootSlot extends SlotItemHandler {
        private final ScrollingBackpackView view;
        private final int visibleIndex;

        CorpseLootSlot(ScrollingBackpackView view, int index, int x, int y) {
            super(view, index, x, y);
            this.view = view;
            this.visibleIndex = index;
        }

        int visibleIndex() {
            return visibleIndex;
        }

        @Override
        public boolean isActive() {
            return view.isVisibleSlotUsable(visibleIndex);
        }

        /**
         * The bag is searched slot by slot, like the body.
         *
         * Masked HERE rather than in a wrapper the way the corpse's own container is, because
         * the bag is an IItemHandler and there is nothing to wrap that the menu reads through.
         * Overriding the slot works just as well: broadcastChanges reads slot.getItem(), so an
         * unsearched bag slot is never sent to the client at all - the client cannot render
         * what it was never told about.
         */
        @Override
        public ItemStack getItem() {
            if (isBagSlotHidden(visibleIndex)) return ItemStack.EMPTY;
            return super.getItem();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isBagSlotHidden(visibleIndex) && super.mayPickup(player);
        }
    }

    /**
     * True while this bag slot has not been searched yet.
     *
     * Bag progress lives in the corpse's own BitSet at an offset past the container size, so
     * one corpse is one record and the pockets-then-pack ordering comes from the numbering
     * rather than from a separate gate.
     */
    /** How many bag slots the corpse's pack has, for the search to walk through. */
    public int corpseBagSlotCount() {
        return corpseLoot == null ? 0 : corpseLoot.serverBagSlots();
    }

    /** Whether a bag slot holds anything, so the search can skip empty ones. */
    public boolean corpseBagSlotOccupied(int bagSlot) {
        if (corpseLoot == null) return false;
        return !corpseLoot.getStackInSlot(CorpseLootHandler.BASE_COUNT + bagSlot).isEmpty();
    }

    /**
     * Menu indices of bag slots still to be searched, so the screen can hatch them.
     *
     * Computed from the visible window rather than the bag's real size, because the bag list
     * scrolls - a slot's menu index only means anything for the rows currently on screen.
     */
    /**
     * Menu indices of the CORPSE's own slots still to be searched.
     *
     * Derived by walking the menu's slots, not by adding an offset to the container index.
     * The corpse column is laid out head-down for armour, then curios, then inventory, then
     * hotbar - nothing like the container's own numbering, where the hotbar is 0-8 and the
     * armour is 36-39. Assuming menuIndex = offset + containerIndex therefore hatched the
     * hotbar row while the items it was hiding were the backpack's, which is exactly the
     * symptom: covers on empty hotbar slots, and an item appearing in the bag when one of
     * them "opened".
     */
    public int[] maskedBodyMenuSlots(java.util.function.IntPredicate revealed) {
        if (searchedContainer == null) return new int[0];
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container != searchedView) continue;
            int containerSlot = slot.getContainerSlot();
            if (revealed.test(containerSlot)) continue;
            if (searchedContainer.getItem(containerSlot).isEmpty()) continue;
            out.add(i);
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    public int[] maskedBagMenuSlots() {
        if (corpseLoot == null || corpseLootView == null) return new int[0];
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (!(slots.get(i) instanceof CorpseLootSlot bagSlot)) continue;
            if (bagSlot.isActive() && isBagSlotHidden(bagSlot.visibleIndex())) out.add(i);
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    boolean isBagSlotHidden(int visibleIndex) {
        if (!(corpseContainer instanceof SearchedContainer searched)) return false;
        if (corpseLoot == null || corpseLootView == null) return false;
        int bagSlot = corpseLootView.mapIndex(visibleIndex) - CorpseLootHandler.BASE_COUNT;
        if (bagSlot < 0) return false;
        // Past the end of the real bag, or empty: never hidden. The visible window can show
        // more cells than the bag actually has, and the search walk only ever visits the real
        // ones - so a cover on a cell outside it had nothing that would ever lift it. That is
        // the pair of permanently masked slots at the bottom of the scrolled list.
        if (bagSlot >= corpseLoot.serverBagSlots()) return false;
        if (corpseLoot.getStackInSlot(CorpseLootHandler.BASE_COUNT + bagSlot).isEmpty()) return false;
        return !SearchProgress.isBagRevealed(searched.delegate(), player, bagSlot);
    }

    @Override
    public void broadcastChanges() {
        // Refresh the synced count before the data slots go out, so the client's view of
        // how many backpack slots exist is always up to date - including the moment a bag
        // is equipped or removed.
        if (!player.level().isClientSide) {
            int count = 0;
            for (int i = 0; i < BACKPACK_MAX_SLOTS; i++) {
                if (backpackHandler.isSlotUsable(i)) count = i + 1;
            }
            backpackSlotCount.set(count);

            if (corpseLoot != null) {
                // The bag section is always sized now; its slots hide themselves individually
                // until searched (see CorpseLootSlot.getItem). The previous all-or-nothing
                // gate made a searched body pop its whole pack open at once, which skipped the
                // half of the search that has the loot worth finding.
                corpseBagSlots.set(corpseLoot.serverBagSlots());
            }
        }
        super.broadcastChanges();
    }

    public boolean hasContainer() {
        return openedContainer != null;
    }

    public boolean isCorpse() {
        return corpseLayout;
    }

    /** Recompute the crafting result whenever the 2x2 grid changes. */
    @Override
    public void slotsChanged(Container container) {
        if (container == craftSlots) {
            updateCraftingResult();
        } else {
            super.slotsChanged(container);
        }
    }

    /**
     * Resolves the current 2x2 recipe and pushes the result to the client.
     *
     * This duplicates what CraftingMenu.slotChangedCraftingGrid does, because that method
     * is protected and so can't be called from a menu outside its package. Runs
     * server-side only; the explicit slot packet is what makes the result appear on the
     * client, since the result slot isn't backed by normal container sync.
     */
    private void updateCraftingResult() {
        Level level = player.level();
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack result = ItemStack.EMPTY;
        var recipeOpt = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
        if (recipeOpt.isPresent()) {
            CraftingRecipe recipe = recipeOpt.get();
            if (resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
                ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }

        resultSlots.setItem(0, result);
        setRemoteSlot(craftStartIndex, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, incrementStateId(), craftStartIndex, result));
    }

    /** Don't let items vanish if the screen closes with something still on the grid. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (openedContainer != null) {
            openedContainer.stopOpen(player);
        }
        resultSlots.clearContent();
        if (!player.level().isClientSide) {
            clearContainer(player, craftSlots);
        }
    }

    /** How many backpack slots are visible right now (0 when nothing suitable is worn). */
    public int getActiveBackpackSlots() {
        int usable = 0;
        for (int i = 0; i < BACKPACK_MAX_SLOTS; i++) {
            if (backpackView.isVisibleSlotUsable(i)) usable++;
        }
        return usable;
    }

    /** Applied on both sides via BackpackScrollPacket - never set on one side alone. */
    public void setBackpackScroll(int row) {
        backpackView.setScrollRow(row);
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
        int craftResult = craftStartIndex;
        int craftGridStart = craftStartIndex + 1;
        int craftGridEnd = craftGridStart + 4;
        int containerStart = containerStartIndex;
        int containerEnd = slots.size();
        boolean hasContainer = containerEnd > containerStart;

        boolean moved;
        if (index == craftResult) {
            // Crafting output: push to inventory, then let the recipe re-run.
            moved = moveItemStackTo(sourceStack, invStart, invEnd, true);
            if (!moved) return ItemStack.EMPTY;
            sourceSlot.onQuickCraft(sourceStack, original);
        } else if (index >= craftGridStart && index < craftGridEnd) {
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
        } else if (index < equipEnd) {
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
            if (!moved) moved = moveItemStackTo(sourceStack, bagStart, bagEnd, false);
        } else if (hasContainer && index >= containerStart && index < containerEnd) {
            // Container -> inventory, then backpack.
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
            if (!moved) moved = moveItemStackTo(sourceStack, bagStart, bagEnd, false);
        } else if (index >= bagStart && index < bagEnd) {
            moved = moveItemStackTo(sourceStack, invStart, invEnd, false);
            if (!moved && hasContainer) {
                moved = moveItemStackTo(sourceStack, containerStart, containerEnd, false);
            }
        } else {
            // With a container open, shift-click should send loot there first - that's the
            // action players expect while looting, ahead of auto-equipping.
            moved = hasContainer && moveItemStackTo(sourceStack, containerStart, containerEnd, false);
            if (!moved) moved = moveItemStackTo(sourceStack, 0, equipEnd, false);
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

    /** Backpack slot that switches itself off when the scrolled-to position has no real slot. */
    private static class BackpackSlot extends SlotItemHandler {
        private final ScrollingBackpackView view;
        private final int visibleIndex;

        BackpackSlot(ScrollingBackpackView view, int index, int x, int y) {
            super(view, index, x, y);
            this.view = view;
            this.visibleIndex = index;
        }

        @Override
        public boolean isActive() {
            // Accounts for both the worn bag's real capacity (each mod reports it
            // differently) and the current scroll position.
            return view.isVisibleSlotUsable(visibleIndex);
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
