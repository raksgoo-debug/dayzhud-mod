package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.util.LinkedHashSet;
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

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        AbstractContainerMenu menu = event.getContainer();
        if (menu instanceof TarkovInventoryMenu) return;      // already ours
        if (REDIRECTING.contains(serverPlayer)) return;       // re-entrancy guard
        if (!isSimpleStorage(menu)) return;

        Container backing = findBackingContainer(menu, serverPlayer);
        if (backing == null || backing.getContainerSize() == 0) return;

        Component title = menu instanceof ChestMenu
                ? Component.literal("Container")
                : Component.literal("Container");

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
            }, buf -> buf.writeVarInt(backing.getContainerSize()));
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Failed to open merged container view; "
                    + "leaving the vanilla screen in place.", e);
        } finally {
            REDIRECTING.remove(serverPlayer);
        }
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
