package com.dayzhud.mod.registry;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(ForgeRegistries.CREATIVE_MODE_TABS, DayzHudMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dayzhud.main"))
                    .icon(() -> new ItemStack(ModItems.WATER_BOTTLE.get()))
                    .displayItems((params, output) -> output.accept(ModItems.WATER_BOTTLE.get()))
                    .build());

    private ModCreativeTabs() {}
}
