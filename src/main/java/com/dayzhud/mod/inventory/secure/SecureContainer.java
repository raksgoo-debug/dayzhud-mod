package com.dayzhud.mod.inventory.secure;

import com.dayzhud.mod.inventory.SaSurvivalBackpackAccess;
import com.dayzhud.mod.inventory.grid.DefaultItemFootprints;
import com.dayzhud.mod.inventory.grid.ItemGrid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The secure container (2.14.0): a 3x3 grid in the inventory screen whose contents survive
 * death - Tarkov's Alpha/Gamma container. Lives in a player capability, not the inventory,
 * so death drops (and the corpse built from them) never see it; SecureContainerCapability
 * carries it across the respawn.
 *
 * No containers inside it: a backpack or pouch in here would carry everything in it through
 * death, which is the whole point of having a SMALL secure container.
 */
public class SecureContainer extends ItemStackHandler {

    public static final int COLS = 3, ROWS = 3, SIZE = COLS * ROWS;

    public SecureContainer() {
        super(SIZE);
    }

    /** Whether {@code stack} may go in a secure container at all (size is the grid's job). */
    public static boolean allowed(ItemStack stack) {
        if (stack.isEmpty() || ItemGrid.isReservation(stack)) return true;
        if (!stack.getItem().canFitInsideContainerItems()) return false;     // shulker boxes, bundles, bags that say so
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && DefaultItemFootprints.BACKPACKS.contains(id.toString())) return false;
        return !SaSurvivalBackpackAccess.isSaBackpack(stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return allowed(stack);
    }
}
