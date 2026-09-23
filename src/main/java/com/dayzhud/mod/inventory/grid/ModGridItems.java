package com.dayzhud.mod.inventory.grid;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * A single hidden item: the marker {@link ItemGrid} puts in a "shadow" cell behind a
 * multi-cell item's footprint, so the cell reads as genuinely occupied to everything that
 * isn't this mod's own grid code - vanilla auto-pickup, a hopper feeding a chest, another
 * mod's UI. A slot-level "don't allow placing here" check only stops OUR screen; it does
 * nothing about any of those, which all write straight into the underlying container. A real
 * (if invisible) ItemStack is occupied everywhere, which is the actual property being relied
 * on. See {@link ItemGrid} for how the marker is placed, read, and cleared.
 *
 * Never added to a creative tab, so it's not obtainable by any normal means. Its texture is
 * fully transparent and its name is blank, so even if something manages to render or hover
 * it directly, nothing is shown.
 */
public final class ModGridItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DayzHudMod.MOD_ID);

    public static final RegistryObject<Item> GRID_RESERVED = ITEMS.register("grid_reserved",
            () -> new Item(new Item.Properties().stacksTo(1)));

    private ModGridItems() {}
}
