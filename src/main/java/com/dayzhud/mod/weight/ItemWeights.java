package com.dayzhud.mod.weight;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.SaSurvivalBackpackAccess;
import com.dayzhud.mod.inventory.grid.DefaultItemFootprints;
import com.dayzhud.mod.inventory.grid.Footprint;
import com.dayzhud.mod.inventory.grid.ItemFootprints;
import com.dayzhud.mod.inventory.grid.ItemGrid;
import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/** How much a stack weighs, in kg (2.15.0). See WeightConfig.OVERRIDES for the rules. */
public final class ItemWeights {

    private static final float BACKPACK_KG = 1.5f;
    private static final float PER_CELL_KG = 0.4f;
    /** A full stack of anything stackable weighs this much. */
    private static final float FULL_STACK_KG = 1.0f;

    private static Map<ResourceLocation, Float> overrides;

    private ItemWeights() {}

    /** Config was reloaded. */
    public static void invalidate() {
        overrides = null;
    }

    private static Map<ResourceLocation, Float> overrides() {
        Map<ResourceLocation, Float> o = overrides;
        if (o != null) return o;
        o = new HashMap<>();
        for (String line : WeightConfig.OVERRIDES.get()) {
            int eq = line.lastIndexOf('=');
            ResourceLocation id = ResourceLocation.tryParse(line.substring(0, eq).trim());
            try {
                if (id != null) o.put(id, Float.parseFloat(line.substring(eq + 1).trim()));
            } catch (NumberFormatException e) {
                DayzHudMod.LOGGER.warn("dayzhud weight config: ignoring '{}' (expected modid:item=kg)", line);
            }
        }
        overrides = o;
        return o;
    }

    public static float weightOf(ItemStack stack) {
        if (stack.isEmpty() || ItemGrid.isReservation(stack)) return 0f;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Float override = id == null ? null : overrides().get(id);
        if (override != null) return override * stack.getCount();

        float gun = TaczMarketCompat.gunWeightOf(stack);
        if (gun >= 0) return gun * stack.getCount();
        float attachment = TaczMarketCompat.attachmentItemWeightOf(stack);
        if (attachment >= 0) return attachment * stack.getCount();

        if (stack.getItem() instanceof ArmorItem armor) {
            return (0.6f + armor.getDefense() * 0.7f) * stack.getCount();
        }
        if ((id != null && DefaultItemFootprints.BACKPACKS.contains(id.toString()))
                || SaSurvivalBackpackAccess.isSaBackpack(stack)) {
            return BACKPACK_KG * stack.getCount();
        }
        int max = stack.getMaxStackSize();
        if (max <= 1) {
            Footprint fp = ItemFootprints.baseFootprintOf(stack);
            return PER_CELL_KG * fp.width() * fp.height() * stack.getCount();
        }
        return FULL_STACK_KG * stack.getCount() / max;
    }
}
