package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** This mod's blocks: the laptop trader terminal (2.13.12). */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DayzHudMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DayzHudMod.MOD_ID);

    public static final RegistryObject<Block> LAPTOP = BLOCKS.register("laptop",
            () -> new LaptopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final RegistryObject<Item> LAPTOP_ITEM = ITEMS.register("laptop",
            () -> new BlockItem(LAPTOP.get(), new Item.Properties()));

    private ModBlocks() {}

    /** No tab of our own - it goes with vanilla's other usable blocks. */
    @SubscribeEvent
    public static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(LAPTOP_ITEM);
    }
}
