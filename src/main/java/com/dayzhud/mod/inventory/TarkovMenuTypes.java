package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TarkovMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DayzHudMod.MOD_ID);

    /**
     * One menu type covers both the plain inventory screen and the inventory-plus-container
     * view. The open packet carries the opened container's size (0 for none) so the client
     * can build a dummy SimpleContainer of matching size - the slot COUNT has to agree on
     * both sides or the sync packets won't line up, but the contents arrive from the server.
     */
    public static final RegistryObject<MenuType<TarkovInventoryMenu>> TARKOV_INVENTORY =
            MENU_TYPES.register("tarkov_inventory",
                    () -> IForgeMenuType.create((windowId, inv, buf) -> {
                        int containerSize = buf.readVarInt();
                        return new TarkovInventoryMenu(windowId, inv,
                                containerSize > 0 ? new SimpleContainer(containerSize) : null);
                    }));
}
