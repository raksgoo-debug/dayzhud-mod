package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.RummageCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * When the player opens a simple storage container, this reopens it as the merged
 * inventory-plus-container menu, so the full loadout screen stays on the left with the
 * container's slots laid out to its right.
 *
 * HOW: PlayerContainerEvent.Open isn't cancellable, so instead of preventing vanilla's menu
 * we let it open and immediately replace it. Opening a second menu closes the first, and
 * because we reuse the SAME backing Container the contents are identical - we're only
 * changing which menu presents it. The guard flag stops the replacement menu from
 * triggering another round of this.
 *
 * ONLY simple storage is redirected. Furnaces, brewing stands, anvils and the like carry
 * data slots and bespoke behaviour that a generic merge would silently drop, so those keep
 * their own (styled) screens.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public class ContainerOpenRedirect {

    private static final Set<ServerPlayer> REDIRECTING = new LinkedHashSet<>();

    /**
     * Mods that manage container contents around the open/close lifecycle. Redirecting
     * breaks them, so their containers keep the vanilla flow (styled, but not merged).
     *
     * WHY: this redirect works by letting vanilla open its menu, then immediately opening
     * ours over the top. Opening a menu closes the previous one - and a mod that fills
     * loot in PlayerContainerEvent.Open and saves/clears it in PlayerContainerEvent.Close
     * will therefore have its loot written back and wiped the instant we swap menus. The
     * player then sees an empty container. sa_decor does exactly this via
     * DecorLootContainerEvents + DecorLootSessionManager's per-player loot sessions.
     *
     * There's no general way to detect "this mod cares about open/close", so this is an
     * explicit list. Add a modid here if another loot/container mod goes empty.
     */
    private static final List<String> LOOT_SESSION_MODS = List.of("sa_decor");

    private static Boolean lootSessionModPresent = null;

    private static boolean lootSessionModInstalled() {
        if (lootSessionModPresent == null) {
            lootSessionModPresent = LOOT_SESSION_MODS.stream().anyMatch(id -> ModList.get().isLoaded(id));
        }
        return lootSessionModPresent;
    }

    /**
     * True only when a loot mod actually has a live per-player session for this player.
     *
     * Verified against sa_decor: its session machinery is gated behind usesPerPlayerLoot(),
     * so with per-player loot turned OFF in its config there is no session to break and the
     * merged view is safe. Checking for a live session - rather than merely "is the mod
     * installed" - means players only lose the merged container view in the exact
     * configuration where it would actually empty their loot.
     */
    private static boolean hasActiveLootSession(ServerPlayer player) {
        if (!lootSessionModInstalled()) return false;
        try {
            Class<?> mgr = Class.forName("com.ogaba.sa_decor.common.loot.DecorLootSessionManager");
            Field pending = mgr.getDeclaredField("PENDING_PER_PLAYER_CONTAINERS");
            pending.setAccessible(true);
            Object map = pending.get(null);
            if (map instanceof Map<?, ?> m && m.containsKey(player.getUUID())) return true;
        } catch (Exception e) {
            // Can't tell - assume a session exists so we never risk wiping loot.
            DayzHudMod.LOGGER.debug("[dayzhud] Couldn't inspect sa_decor loot sessions; "
                    + "skipping the merged container view to be safe.", e);
            return true;
        }
        return false;
    }

    /**
     * LOWEST priority so every other mod's PlayerContainerEvent.Open handler runs first.
     * Some mods populate the container at open time (sa_decor generates per-player loot
     * here), and redirecting before they've run would show an unfilled container.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        AbstractContainerMenu menu = event.getContainer();
        if (menu instanceof TarkovInventoryMenu) return;      // already ours
        if (REDIRECTING.contains(serverPlayer)) return;       // re-entrancy guard
        // Only skip when a per-player loot session is actually live - see
        // hasActiveLootSession. With sa_decor's per_player setting off there's no session,
        // so the merged view works normally.
        if (hasActiveLootSession(serverPlayer)) return;
        if (!isSimpleStorage(menu)) return;

        Container backing = findBackingContainer(menu, serverPlayer);

        // Rummage hides a container until it has been searched. The masking follows our slots,
        // but the searching interaction belongs to Rummage's own screen - merging would keep
        // the items hidden and remove the way to reveal them. Stand down until it is searched.
        if (RummageCompat.needsSearching(backing, serverPlayer)) return;
        if (backing == null || backing.getContainerSize() == 0) return;

        Component title = containerName(backing);

        REDIRECTING.add(serverPlayer);
        try {
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return title;
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                    return new TarkovInventoryMenu(windowId, inv, backing);
                }
            }, buf -> {
                // Payload order is fixed by TarkovMenuTypes - see its class notes.
                buf.writeVarInt(backing.getContainerSize());
                buf.writeBoolean(false);  // a chest/barrel/hopper, not a corpse
                buf.writeVarInt(0);       // no curio slots
            });
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Failed to open merged container view; "
                    + "leaving the vanilla screen in place.", e);
        } finally {
            REDIRECTING.remove(serverPlayer);
            // Drop whatever mask Rummage computed for the menu we just replaced; it does
            // not clear it itself. Sent after the open packet, so it cannot arrive first.
            com.dayzhud.mod.market.MarketNetwork.clearRummageMask(serverPlayer);
            // Snapshot Rummage's view of the menu we just opened. Taken here because the
            // command form cannot see it - opening chat closes the container first.
            RummageCompat.capture(serverPlayer.containerMenu, serverPlayer);
            // Drop whatever mask Rummage computed for the menu we just replaced; it will not
            // clear it itself. Sent after the open packet, so it cannot arrive first.
            com.dayzhud.mod.market.MarketNetwork.clearRummageMask(serverPlayer);
            // Snapshot Rummage's view of the menu we just opened. Taken here because the
            // command form cannot see it - opening chat closes the container first.
            RummageCompat.capture(serverPlayer.containerMenu, serverPlayer);
        }
    }

    /**
     * The opened block's own display name, so the header reads "Chest" / "Barrel" / whatever
     * the player renamed it to, rather than a generic label.
     *
     * Most container block entities implement Nameable and already handle custom anvil names.
     * Double chests use CompoundContainer, which doesn't, hence the fallback.
     */
    private static Component containerName(Container backing) {
        if (backing instanceof Nameable nameable) {
            Component name = nameable.getDisplayName();
            if (name != null) return name;
        }
        return Component.literal("Container");
    }

    private static boolean isSimpleStorage(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof DispenserMenu
                || menu instanceof HopperMenu;
    }

    /**
     * Pulls the container being viewed out of the vanilla menu by looking at which Container
     * its non-player slots point at. Reusing the real backing object (rather than copying
     * items) is what keeps hoppers, comparators and other observers working normally.
     */
    private static Container findBackingContainer(AbstractContainerMenu menu, ServerPlayer player) {
        Container playerInv = player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container != playerInv) {
                return slot.container;
            }
        }
        return null;
    }
}
