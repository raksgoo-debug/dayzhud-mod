package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.MarketScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

        // Don't re-wrap our own screens (that would recurse).
        //
        // MarketScreen has to be here too, and its absence was a real bug. This class
        // restyles by DEFAULT and only excludes known-complex menus, so a new screen of our
        // own gets caught by exactly the same net as any other mod's: MarketMenu has 45
        // slots, clears the >36 test, and its simple name was not in EXCLUDED_MENU_CLASSES,
        // so the trader opened as a plain styled grid with the stock list, tabs, balance and
        // buttons all gone. Anything added later that draws its own widgets needs this line.
        if (incoming instanceof StyledContainerScreen<?>
                || incoming instanceof TarkovInventoryScreen
                || incoming instanceof MarketScreen) return;

        // The creative menu and the vanilla survival inventory are tabbed screens with their
        // own widgets - swapping in a plain slot grid destroys the tabs and item list, which
        // is exactly what broke creative mode. TarkovInventoryClientEvents handles the
        // survival inventory separately; in creative both are intentionally left alone.
        if (incoming instanceof CreativeModeInventoryScreen || incoming instanceof InventoryScreen) return;

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
            "HorseInventoryMenu", "BrewingStandMenu", "CrafterMenu",
            "ItemPickerMenu",  // creative inventory's menu
            "InventoryMenu",   // vanilla survival inventory

            // Ragdollified's corpse menu. Normally CorpseOpenRedirect has already replaced
            // this server-side and it never reaches the client at all - but when the redirect
            // stands down (unrecognised layout, or the addon moved again), restyling it here
            // gives the worst of both worlds: the addon's slot layout wearing our skin, minus
            // its TAKE ALL / SWAP buttons, which any screen swap discards. Excluding it means
            // a stood-down redirect falls back to the addon's real, fully working screen -
            // and that visible difference is also the quickest way to tell, in game, whether
            // the redirect fired at all.
            "CorpseMenu",

            // Our own trader. Redundant with the MarketScreen check above and kept as belt
            // and braces: that check only helps once MarketScreen is the incoming screen.
            "MarketMenu"
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
