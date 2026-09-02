package com.dayzhud.mod.registry;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.item.WaterBottleItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items this mod adds. Currently just the water bottle. */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DayzHudMod.MOD_ID);

    public static final RegistryObject<Item> WATER_BOTTLE = ITEMS.register("water_bottle",
            () -> new WaterBottleItem(new Item.Properties().stacksTo(8), 8, 6));

    private ModItems() {}
}
