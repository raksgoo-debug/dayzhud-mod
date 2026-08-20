package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.IntSupplier;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A live view of whatever bag is currently worn in the player's "back" Curios slot, so the
 * inventory screen's backpack grid resizes the instant a bag is equipped or swapped.
 *
 * Backpack mods don't agree on how to expose their contents, so this tries two routes,
 * both verified by decompiling the actual mod jars:
 *
 * 1. SA Survival (modid "sa_survival") does NOT put an item-handler capability on the bag
 *    ItemStack - it has its own GUI. It does ship
 *    com.ogaba.sa_survival.item.backpack.DynamicBackpackItemHandler, a public
 *    IItemHandlerModifiable taking a Player, which resolves the equipped backpack itself
 *    and exposes isSlotEnabled(int). We construct that reflectively and delegate to it.
 *
 * 2. Everything else: read Forge's standard ITEM_HANDLER capability off the worn stack.
 *    Fracture Point / Warborn works this way, but over-allocates its handler to the
 *    maximum tier size (COLUMNS x TOTAL_ROWS) while real capacity comes from
 *    BackpackItem.getSlotsForTier(getTier(stack)) - which is why a 9-slot bag previously
 *    showed 27 empty squares. We call those two static methods reflectively to trim it.
 *
 * All accessors are bounds-checked and fail soft, because the backing size legitimately
 * changes underneath callers mid-session.
 *
 * CLIENT/SERVER SPLIT - the important part. Forge does NOT sync ItemStack capability data
 * to the client unless the item overrides getShareTag/readShareTag, and several backpack
 * mods (Fracture Point/Warborn among them) don't. Resolving the bag on the client
 * therefore finds an empty handler and the whole section silently disappears.
 *
 * So the SERVER resolves the real bag, and the CLIENT uses a plain mirror buffer that the
 * menu's normal slot sync fills in - exactly how vanilla container screens work. The
 * client also takes its slot count from a synced DataSlot rather than trying to work it
 * out itself.
 */
public class BackCurioItemHandler implements IItemHandlerModifiable {

    private static final String BACK_SLOT_ID = "back";

    // --- SA Survival integration ---
    private static final String SA_MODID = "sa_survival";
    private static final String SA_HANDLER_CLASS =
            "com.ogaba.sa_survival.item.backpack.DynamicBackpackItemHandler";

    // --- Warborn / Fracture Point capacity correction ---
    private static final String WARBORN_ITEM_CLASS = "com.raiiiden.warborn.common.item.BackpackItem";

    private static boolean saResolved = false;
    private static Method saIsSlotEnabled;

    private static boolean warbornResolved = false;
    private static Method warbornGetTier;
    private static Method warbornGetSlotsForTier;
    private static Method warbornGetVisibleRowsForTier;
    private static Class<?> warbornBackpackItemClass;

    /** Upper bound on displayable slots; must match the menu's allocation. */
    private static final int MIRROR_SIZE = 64;

    private final Player player;
    private final IItemHandlerModifiable saHandler; // null unless SA Survival is installed

    /** Client-side stand-in, populated by the menu's slot sync. */
    private final ItemStackHandler clientMirror = new ItemStackHandler(MIRROR_SIZE);
    /** Slot count as told by the server, used client-side. */
    private IntSupplier syncedSlotCount = () -> 0;

    public void setSyncedSlotCount(IntSupplier supplier) {
        this.syncedSlotCount = supplier;
    }

    private boolean isClient() {
        return player.level().isClientSide;
    }

    public BackCurioItemHandler(Player player) {
        this.player = player;
        this.saHandler = createSaHandler(player);
    }

    private static IItemHandlerModifiable createSaHandler(Player player) {
        if (!ModList.get().isLoaded(SA_MODID)) return null;
        try {
            Class<?> cls = Class.forName(SA_HANDLER_CLASS);
            Object instance = cls.getConstructor(Player.class).newInstance(player);
            if (!saResolved) {
                saResolved = true;
                try {
                    saIsSlotEnabled = cls.getMethod("isSlotEnabled", int.class);
                } catch (NoSuchMethodException ignored) {
                    saIsSlotEnabled = null;
                }
            }
            return (IItemHandlerModifiable) instance;
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] SA Survival is installed but its backpack handler "
                    + "couldn't be created - falling back to the generic capability path.", e);
            return null;
        }
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

    /** Generic path: the worn bag's own inventory via Forge's standard capability. */
    private IItemHandler capabilityDelegate() {
        ItemStack bag = getBagStack();
        if (bag.isEmpty()) return null;

        // Last-resort path for SA Survival bags if their Player-based handler didn't
        // resolve (e.g. an unusual equip state) - uses SA's own inventory class.
        IItemHandler sa = SaSurvivalBackpackAccess.open(bag);
        if (sa != null) return sa;

        return bag.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }

    /**
     * Real usable slot count for the worn bag, correcting for mods whose handler is
     * allocated larger than the bag's actual tier capacity.
     */
    private int usableCapabilitySlots(IItemHandler handler) {
        if (handler == null) return 0;
        int reported = handler.getSlots();

        ItemStack bag = getBagStack();
        if (bag.isEmpty()) return 0;

        Integer tiered = warbornTierCapacity(bag);
        // Only trust the tier lookup when it returns something positive. A base-tier bag
        // can report tier 0, and if getSlotsForTier(0) yields 0 we'd blank the whole bag -
        // which is exactly how Fracture Point backpacks ended up showing nothing. Falling
        // back to the handler's own size is always safe: it's the real allocation.
        if (tiered != null && tiered > 0) {
            return Math.min(reported, tiered);
        }
        return reported;
    }

    /** Warborn-specific: getSlotsForTier(getTier(stack)), or null if that mod isn't the source. */
    private Integer warbornTierCapacity(ItemStack bag) {
        if (!warbornResolved) {
            warbornResolved = true;
            try {
                warbornBackpackItemClass = Class.forName(WARBORN_ITEM_CLASS);
                warbornGetTier = warbornBackpackItemClass.getMethod("getTier", ItemStack.class);
                warbornGetSlotsForTier = warbornBackpackItemClass.getMethod("getSlotsForTier", int.class);
                try {
                    warbornGetVisibleRowsForTier =
                            warbornBackpackItemClass.getMethod("getVisibleRowsForTier", int.class);
                } catch (NoSuchMethodException ignored) {
                    warbornGetVisibleRowsForTier = null;
                }
            } catch (Exception ignored) {
                warbornBackpackItemClass = null;
                warbornGetTier = null;
                warbornGetSlotsForTier = null;
                warbornGetVisibleRowsForTier = null;
            }
        }
        if (warbornBackpackItemClass == null || warbornGetTier == null || warbornGetSlotsForTier == null) {
            return null;
        }
        if (!warbornBackpackItemClass.isInstance(bag.getItem())) return null;

        try {
            int tier = (int) warbornGetTier.invoke(null, bag);
            int slots = (int) warbornGetSlotsForTier.invoke(null, tier);
            if (slots > 0) return slots;

            // Some tiers express capacity as visible rows instead; 9 columns per row.
            if (warbornGetVisibleRowsForTier != null) {
                int rows = (int) warbornGetVisibleRowsForTier.invoke(null, tier);
                if (rows > 0) return rows * 9;
            }
            return null; // let the caller fall back to the handler's own size
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Warborn backpack tier lookup failed.", e);
            return null;
        }
    }

    /**
     * Whether SA Survival's handler is the right one for the bag CURRENTLY worn.
     *
     * Critically this is not just "is SA Survival installed" - its handler only resolves
     * its own backpacks, so with a different mod's bag equipped it reports zero slots. If
     * we routed on mod presence alone, every other mod's backpack would silently show as
     * empty whenever SA Survival happened to be in the pack.
     */
    private boolean saActive() {
        return saHandler != null && saHandler.getSlots() > 0;
    }

    /** True if this index should be shown as a usable slot right now. */
    public boolean isSlotUsable(int slot) {
        if (isClient()) {
            return slot >= 0 && slot < syncedSlotCount.getAsInt();
        }
        if (saActive()) {
            if (slot >= saHandler.getSlots()) return false;
            if (saIsSlotEnabled != null) {
                try {
                    return (boolean) saIsSlotEnabled.invoke(saHandler, slot);
                } catch (Exception ignored) {
                    // fall through to the size check below
                }
            }
            return true;
        }
        return slot < usableCapabilitySlots(capabilityDelegate());
    }

    private boolean inMirror(int slot) {
        return slot >= 0 && slot < MIRROR_SIZE;
    }

    private boolean valid(IItemHandler h, int slot) {
        return h != null && slot >= 0 && slot < h.getSlots();
    }

    @Override
    public int getSlots() {
        if (isClient()) return Math.min(MIRROR_SIZE, syncedSlotCount.getAsInt());
        if (saActive()) return saHandler.getSlots();
        return usableCapabilitySlots(capabilityDelegate());
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (isClient()) return inMirror(slot) ? clientMirror.getStackInSlot(slot) : ItemStack.EMPTY;
        if (saActive()) {
            return valid(saHandler, slot) ? saHandler.getStackInSlot(slot) : ItemStack.EMPTY;
        }
        IItemHandler h = capabilityDelegate();
        return valid(h, slot) ? h.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (isClient()) return inMirror(slot) ? clientMirror.insertItem(slot, stack, simulate) : stack;
        if (!isSlotUsable(slot)) return stack;
        if (saActive()) {
            return valid(saHandler, slot) ? saHandler.insertItem(slot, stack, simulate) : stack;
        }
        IItemHandler h = capabilityDelegate();
        return valid(h, slot) ? h.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (isClient()) return inMirror(slot) ? clientMirror.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        if (saActive()) {
            return valid(saHandler, slot) ? saHandler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        IItemHandler h = capabilityDelegate();
        return valid(h, slot) ? h.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (!isSlotUsable(slot)) return 0;
        if (saActive()) {
            return valid(saHandler, slot) ? saHandler.getSlotLimit(slot) : 0;
        }
        IItemHandler h = capabilityDelegate();
        return valid(h, slot) ? h.getSlotLimit(slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!isSlotUsable(slot)) return false;
        if (saActive()) {
            return valid(saHandler, slot) && saHandler.isItemValid(slot, stack);
        }
        IItemHandler h = capabilityDelegate();
        return valid(h, slot) && h.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (isClient()) {
            if (inMirror(slot)) clientMirror.setStackInSlot(slot, stack);
            return;
        }
        if (saActive()) {
            if (valid(saHandler, slot)) saHandler.setStackInSlot(slot, stack);
            return;
        }
        IItemHandler h = capabilityDelegate();
        if (valid(h, slot) && h instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        }
    }
}
