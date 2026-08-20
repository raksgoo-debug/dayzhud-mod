package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

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
        } else if (canRestyle(menu)) {
            event.setNewScreen(new StyledContainerScreen<>(menu, inventory, title));
        }
    }

    /**
     * Menu classes with bespoke widgets - text fields, buttons, recipe lists, trade lists.
     * Substituting a plain slot-grid screen for these would render the widgets unreachable,
     * so they keep their vanilla look.
     */
    private static final Set<String> EXCLUDED_MENU_CLASSES = Set.of(
            "AnvilMenu", "EnchantmentMenu", "BeaconMenu", "LoomMenu", "StonecutterMenu",
            "CartographyTableMenu", "SmithingMenu", "GrindstoneMenu", "MerchantMenu",
            "HorseInventoryMenu", "BrewingStandMenu", "CrafterMenu"
    );

    /**
     * We restyle by default and exclude the known-complex screens, rather than allow-listing
     * only vanilla grids. That way OTHER MODS' simple container GUIs (backpacks, lockers,
     * crates) get the same look automatically instead of standing out as vanilla-grey.
     *
     * TRADE-OFF: a modded screen that draws its own buttons or extra widgets will lose them,
     * because we replace the screen object rather than just its background. If a mod's GUI
     * misbehaves, add its menu class simple-name to EXCLUDED_MENU_CLASSES above.
     */
    private static boolean canRestyle(AbstractContainerMenu menu) {
        if (EXCLUDED_MENU_CLASSES.contains(menu.getClass().getSimpleName())) return false;
        // Needs at least the player's 36 inventory slots plus something of its own.
        return menu.slots.size() > 36;
    }
}
