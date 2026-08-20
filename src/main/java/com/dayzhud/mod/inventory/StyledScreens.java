package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Swaps vanilla container screens for this mod's styled equivalents, so the game's common
 * UIs match the inventory.
 *
 * IMPLEMENTATION NOTE: this intentionally does NOT use MenuScreens.register. That method
 * throws "Duplicate registration" if a MenuType already has a screen - it can only add
 * mappings, never replace vanilla ones. Instead we let vanilla build its screen, then
 * substitute ours at open time, reusing the SAME menu instance. That's important: the menu
 * is already synced with the server, so no extra networking is involved and slot indices
 * stay valid.
 *
 * DELIBERATELY NOT COVERED:
 *  - Creative inventory, recipe book, pause/options/title screens: custom widget layouts
 *    rather than slot grids, so a generic restyle can't handle them.
 *  - Anvil, enchanting, beacon, loom, stonecutter, cartography, smithing: bespoke widgets
 *    (text fields, buttons, recipe lists) that need one custom screen each.
 *  - Other mods' screens: those belong to their own mods and can't be safely swapped here.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, value = Dist.CLIENT)
public class StyledScreens {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen incoming = event.getNewScreen();
        if (!(incoming instanceof AbstractContainerScreen<?> containerScreen)) return;

        // Don't re-wrap our own screens (that would recurse) or the player inventory,
        // which TarkovInventoryClientEvents already handles.
        if (incoming instanceof StyledContainerScreen<?> || incoming instanceof TarkovInventoryScreen) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractContainerMenu menu = containerScreen.getMenu();
        Inventory inventory = mc.player.getInventory();
        Component title = incoming.getTitle();

        if (menu instanceof AbstractFurnaceMenu furnaceMenu) {
            event.setNewScreen(new StyledFurnaceScreen(furnaceMenu, inventory, title));
        } else if (isSimpleSlotGrid(menu)) {
            event.setNewScreen(new StyledContainerScreen<>(menu, inventory, title));
        }
    }

    /** Containers whose UI is purely a grid of slots, so the generic restyle is safe. */
    private static boolean isSimpleSlotGrid(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu          // chests, barrels, ender chests
                || menu instanceof ShulkerBoxMenu
                || menu instanceof DispenserMenu  // dispenser + dropper
                || menu instanceof HopperMenu
                || menu instanceof CraftingMenu;
    }
}
