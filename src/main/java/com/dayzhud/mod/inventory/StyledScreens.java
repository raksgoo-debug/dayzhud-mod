package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Re-registers vanilla menu types against this mod's styled screens, so the whole game's
 * common container UIs match the inventory.
 *
 * Registering a MenuType a second time simply replaces the existing screen factory, which
 * is how a mod can restyle vanilla screens without mixins.
 *
 * DELIBERATELY NOT COVERED:
 *  - Creative inventory, recipe book, pause/options/title screens: these are custom widget
 *    layouts rather than slot grids, so a generic restyle can't handle them.
 *  - Anvil, enchanting table, beacon, loom, stonecutter, cartography, smithing: each has
 *    bespoke widgets (text fields, buttons, recipe lists). They keep the vanilla look for
 *    now - restyling them properly means one custom screen per type.
 *  - Other mods' screens (JEI, backpack GUIs, etc): those belong to their own mods and
 *    can't be safely overridden from here.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StyledScreens {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // NOTE: the explicit <Menu, Screen> type witnesses are required. Because
            // StyledContainerScreen is generic, javac can't infer the two type variables
            // from a bare constructor reference and the call fails to compile.

            // Chests, barrels, ender chests and anything using the generic sizes.
            MenuScreens.<ChestMenu, StyledContainerScreen<ChestMenu>>register(
                    MenuType.GENERIC_9x1, StyledContainerScreen::new);
            MenuScreens.<ChestMenu, StyledContainerScreen<ChestMenu>>register(
                    MenuType.GENERIC_9x2, StyledContainerScreen::new);
            MenuScreens.<ChestMenu, StyledContainerScreen<ChestMenu>>register(
                    MenuType.GENERIC_9x3, StyledContainerScreen::new);
            MenuScreens.<ChestMenu, StyledContainerScreen<ChestMenu>>register(
                    MenuType.GENERIC_9x4, StyledContainerScreen::new);
            MenuScreens.<ChestMenu, StyledContainerScreen<ChestMenu>>register(
                    MenuType.GENERIC_9x5, StyledContainerScreen::new);
            MenuScreens.<ChestMenu, StyledContainerScreen<ChestMenu>>register(
                    MenuType.GENERIC_9x6, StyledContainerScreen::new);
            MenuScreens.<DispenserMenu, StyledContainerScreen<DispenserMenu>>register(
                    MenuType.GENERIC_3x3, StyledContainerScreen::new); // dispenser/dropper
            MenuScreens.<HopperMenu, StyledContainerScreen<HopperMenu>>register(
                    MenuType.HOPPER, StyledContainerScreen::new);
            MenuScreens.<ShulkerBoxMenu, StyledContainerScreen<ShulkerBoxMenu>>register(
                    MenuType.SHULKER_BOX, StyledContainerScreen::new);
            MenuScreens.<CraftingMenu, StyledContainerScreen<CraftingMenu>>register(
                    MenuType.CRAFTING, StyledContainerScreen::new);

            // Furnace family needs the progress/burn indicators.
            MenuScreens.<AbstractFurnaceMenu, StyledFurnaceScreen>register(
                    MenuType.FURNACE, StyledFurnaceScreen::new);
            MenuScreens.<AbstractFurnaceMenu, StyledFurnaceScreen>register(
                    MenuType.BLAST_FURNACE, StyledFurnaceScreen::new);
            MenuScreens.<AbstractFurnaceMenu, StyledFurnaceScreen>register(
                    MenuType.SMOKER, StyledFurnaceScreen::new);

            DayzHudMod.LOGGER.info("[dayzhud] Restyled vanilla container screens registered.");
        });
    }
}
