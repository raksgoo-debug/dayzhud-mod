package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.List;
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
                        // Corpse opens also send a curio count; plain inventory/chest opens
                        // don't, so only read it when there's payload left.
                        int curioCount = buf.isReadable() ? buf.readVarInt() : 0;
                        if (containerSize <= 0) {
                            return new TarkovInventoryMenu(windowId, inv, null);
                        }
                        List<String> curioIds = new ArrayList<>();
                        for (int i = 0; i < curioCount; i++) {
                            // Only the COUNT matters client-side - identifiers are used for
                            // layout decisions the server already made. Placeholders keep the
                            // slot count identical on both sides, which is what must match.
                            curioIds.add("curio" + i);
                        }
                        return new TarkovInventoryMenu(windowId, inv,
                                new SimpleContainer(containerSize), curioIds);
                    }));
}
