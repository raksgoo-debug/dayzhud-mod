package com.dayzhud.mod.registry;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Creative tab.
 *
 * NOTE the registry source: creative tabs are a VANILLA registry in 1.20.1, so the
 * DeferredRegister is built from {@code Registries.CREATIVE_MODE_TAB} rather than a
 * ForgeRegistries constant. There is no ForgeRegistries.CREATIVE_MODE_TABS field - only a
 * ResourceKey under ForgeRegistries.Keys - which is why reaching for the usual
 * ForgeRegistries.X pattern here does not compile.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DayzHudMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dayzhud.main"))
                    .icon(() -> new ItemStack(ModItems.WATER_BOTTLE.get()))
                    .displayItems((params, output) -> output.accept(ModItems.WATER_BOTTLE.get()))
                    .build());

    private ModCreativeTabs() {}
}
