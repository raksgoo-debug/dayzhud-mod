package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TarkovMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DayzHudMod.MOD_ID);

    public static final RegistryObject<MenuType<TarkovInventoryMenu>> TARKOV_INVENTORY =
            MENU_TYPES.register("tarkov_inventory",
                    () -> IForgeMenuType.create((windowId, inv, buf) -> new TarkovInventoryMenu(windowId, inv)));
}
