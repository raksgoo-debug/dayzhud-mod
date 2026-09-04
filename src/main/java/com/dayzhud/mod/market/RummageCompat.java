package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Rummage compatibility: do not steal a container that still has to be searched.
 *
 * Rummage hides a container's contents until the player has rummaged through it, by masking
 * {@code Slot.getItem} for slots bound to an {@code IRummageable} container. This mod's
 * container merging opens its own menu over that same container, and while the masking
 * follows the slots, the search interaction, its progress bar and its sounds belong to
 * Rummage's own screen - replacing that screen removes the mechanic even though the items
 * stay hidden.
 *
 * So the rule is simple and one-directional: while a container still needs rummaging for this
 * player, the redirect stands down and Rummage's screen is left alone. Once it has been
 * searched, opening it again gives the merged view as usual. That keeps the searching
 * mechanic exactly as its author wrote it rather than reimplementing it badly.
 */
public final class RummageCompat {

    public static final String MOD_ID = "rummage";

    private static Boolean loaded;
    private static boolean resolved;
    private static Class<?> rummageable;
    private static Method isNeedRummageForPlayer;
    private static Method isFullyRummagedForPlayer;
    private static Method getTargetForSlot;
    private static Method targetLocalSlotIndex;

    /**
     * Last snapshot taken when a merged menu opened, per player.
     *
     * The command version of this was useless: opening chat to type it closes the container,
     * so player.containerMenu is already back to InventoryMenu by the time it runs. The
     * snapshot has to be taken at redirect time and read back afterwards.
     */
    private static final java.util.Map<java.util.UUID, List<String>> SNAPSHOTS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean clientResolved;
    private static Method clearClientMask;
    private static java.lang.reflect.Field maskedMenuSlots;

    private RummageCompat() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MOD_ID);
        return loaded;
    }

    private static synchronized boolean resolve() {
        if (resolved) return rummageable != null;
        resolved = true;
        try {
            ClassLoader cl = RummageCompat.class.getClassLoader();
            rummageable = Class.forName("com.scarasol.rummage.api.mixin.IRummageable", false, cl);
            // The one-arg overload is the per-player question; the no-arg one only says
            // whether the container is the kind that CAN be rummaged.
            isNeedRummageForPlayer = rummageable.getMethod("isNeedRummage", Player.class);
            isFullyRummagedForPlayer = rummageable.getMethod("isFullyRummaged", Player.class);
            Class<?> util = Class.forName("com.scarasol.rummage.util.CommonContainerUtil", false, cl);
            getTargetForSlot = util.getMethod("getTarget",
                    net.minecraft.world.inventory.Slot.class,
                    net.minecraft.world.inventory.AbstractContainerMenu.class);
            Class<?> target = Class.forName("com.scarasol.rummage.data.RummageTarget", false, cl);
            targetLocalSlotIndex = target.getMethod("localSlotIndex");
            return true;
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("Rummage is installed but its API did not resolve - "
                    + "container merging will not stand down for unsearched containers: {}",
                    t.toString());
            rummageable = null;
            return false;
        }
    }

    /**
     * Per-slot diagnostic for an open menu: which slots Rummage resolves a target for, and
     * therefore which menu indices it will mask.
     *
     * Built after three rounds of inferring this from screenshots and getting it wrong twice.
     * Comparing this list against what is actually hatched on screen says immediately whether
     * the problem is target resolution (wrong slots listed here) or client state (right slots
     * here, wrong ones drawn).
     */
    public static List<String> describe(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                        Player player) {
        List<String> out = new ArrayList<>();
        if (!isModLoaded()) {
            out.add("rummage not installed");
            return out;
        }
        if (!resolve() || getTargetForSlot == null) {
            out.add("rummage api did not resolve");
            return out;
        }
        out.add("menu " + menu.getClass().getSimpleName() + ", " + menu.slots.size() + " slots");
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(i);
            String container = slot.container == null
                    ? "null" : slot.container.getClass().getSimpleName();
            Object target;
            try {
                target = getTargetForSlot.invoke(null, slot, menu);
            } catch (Throwable t) {
                target = null;
            }
            if (target == null) continue;
            int local = -1;
            try {
                local = ((Number) targetLocalSlotIndex.invoke(target)).intValue();
            } catch (Throwable ignored) {
            }
            out.add("  menuSlot " + i + " -> " + container + "[" + slot.getContainerSlot()
                    + "] target local=" + local);
            if (masked.length() > 0) masked.append(',');
            masked.append(i);
        }
        out.add(masked.length() == 0
                ? "NO slots resolve a target - Rummage will send nothing and any existing "
                  + "client mask will persist"
                : "menu indices Rummage will consider: " + masked);
        return out;
    }

    /**
     * Captures the slot/target map for a menu the moment it opens, and logs it once per
     * distinct menu shape so the log does not fill up with repeats.
     */
    public static boolean capture(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                  Player player) {
        if (!isModLoaded() || menu == null || player == null) return false;
        List<String> lines = describe(menu, player);
        SNAPSHOTS.put(player.getUUID(), lines);
        String key = menu.getClass().getName() + ":" + menu.slots.size();
        if (LOGGED.add(key)) {
            for (String line : lines) DayzHudMod.LOGGER.info("[rummage] {}", line);
        }
        return resolvesAnyTarget(menu, player);
    }

    /**
     * Whether ANY slot in this menu resolves a Rummage target.
     *
     * This is the question that decides whether clearing the client's mask is safe. Rummage
     * skips its state packet when its recomputed bitset is empty, so:
     *
     *   targets exist  -> Rummage sends a correct bitset that overwrites the stale one, and
     *                     clearing afterwards would destroy the CORRECT mask.
     *   no targets     -> Rummage sends nothing, the stale bitset survives, and clearing is
     *                     the only way to get rid of it.
     *
     * Clearing unconditionally was wrong in exactly the case that matters.
     */
    public static boolean resolvesAnyTarget(
            net.minecraft.world.inventory.AbstractContainerMenu menu, Player player) {
        if (!isModLoaded() || !resolve() || getTargetForSlot == null || menu == null) return false;
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            try {
                if (getTargetForSlot.invoke(null, slot, menu) != null) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** The last captured snapshot for this player, for /market rummage to print. */
    public static List<String> lastSnapshot(Player player) {
        List<String> lines = SNAPSHOTS.get(player.getUUID());
        if (lines == null || lines.isEmpty()) {
            return List.of("No snapshot yet - open a corpse or container with the merged view "
                    + "first, then run this. (Opening chat closes the screen, which is why "
                    + "reading the live menu did not work.)");
        }
        return lines;
    }

    /**
     * Logs what the CLIENT actually has masked, next to how many slots its menu has.
     *
     * The server side is now known good: it resolves every corpse slot and computes bits in
     * the 90+ range. The screenshot shows six slots masked at the very start of the menu, and
     * the corpse had exactly six occupied slots - so the client is holding a bitset that is
     * not the one the server computed for this menu. This prints both numbers so they can be
     * compared instead of inferred.
     */
    public static void logClientMask(int clientSlotCount) {
        if (!isModLoaded()) return;
        if (!clientResolved) clearClientMask();   // resolves the manager class as a side effect
        try {
            if (maskedMenuSlots == null) {
                maskedMenuSlots = Class.forName(
                        "com.scarasol.rummage.manager.ClientRummageManager", false,
                        RummageCompat.class.getClassLoader()).getField("MASKED_MENU_SLOTS");
            }
            Object bits = maskedMenuSlots.get(null);
            DayzHudMod.LOGGER.info("[rummage-client] menu has {} slots, masked set = {}",
                    clientSlotCount, bits);
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("[rummage-client] could not read the mask: {}", t.toString());
        }
    }

    /**
     * Wipes the client's slot mask.
     *
     * THE BUG THIS FIXES. Rummage recomputes its mask on PlayerContainerEvent.Open and sends
     * it as a BitSet of MENU slot indices - but it skips the packet entirely when the set
     * comes out empty. This mod replaces the menu inside that same event, so the sequence is:
     * the original corpse menu is opened and masked (its slots 0..40 being the corpse), we
     * swap in the merged menu, Rummage recomputes, gets nothing, and sends nothing - leaving
     * the client holding the OLD bitset and painting it over the new menu, where indices
     * 0..5 are the player's own armour and curios. Which is exactly what the screenshot
     * showed: equipment hatched, corpse untouched.
     *
     * Chests never showed it because a merged chest still resolves targets, so the recomputed
     * set is non-empty and overwrites the stale one.
     *
     * Clearing on screen open is safe: the server sends its fresh state after the open packet,
     * so a legitimate mask re-applies a moment later. An empty one correctly stays empty.
     */
    public static void clearClientMask() {
        if (!isModLoaded()) return;
        if (!clientResolved) {
            clientResolved = true;
            try {
                clearClientMask = Class.forName(
                        "com.scarasol.rummage.manager.ClientRummageManager", false,
                        RummageCompat.class.getClassLoader()).getMethod("clear");
            } catch (Throwable t) {
                DayzHudMod.LOGGER.warn("Rummage client manager did not resolve; a stale slot "
                        + "mask may persist across a merged screen: {}", t.toString());
            }
        }
        if (clearClientMask == null) return;
        try {
            clearClientMask.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    /**
     * True when this container has not yet been searched by this player, and the redirect
     * should therefore leave Rummage's own screen in place.
     *
     * Fails open: any doubt and we report false, because a false negative costs the merged
     * view for one container while a false positive would hide a container from the player.
     */
    /**
     * True when this container is one Rummage gates AND the player has finished searching it.
     * False when Rummage is absent or the container is not rummageable, so callers that use
     * this to unlock something must treat "not gated" as "unlocked" themselves.
     */
    public static boolean isFullyRummaged(Container container, Player player) {
        if (container == null || player == null) return false;
        if (!isModLoaded() || !resolve()) return false;
        if (!rummageable.isInstance(container)) return false;
        try {
            Object result = isFullyRummagedForPlayer.invoke(container, player);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when Rummage gates this container at all. */
    public static boolean gates(Container container) {
        if (container == null || !isModLoaded() || !resolve()) return false;
        return rummageable.isInstance(container);
    }

    public static boolean needsSearching(Container container, Player player) {
        if (container == null || player == null) return false;
        if (!MarketConfig.RESPECT_RUMMAGE.get() || !isModLoaded() || !resolve()) return false;
        if (!rummageable.isInstance(container)) return false;
        try {
            Object result = isNeedRummageForPlayer.invoke(container, player);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }
}
