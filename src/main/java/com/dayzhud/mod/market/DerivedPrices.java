package com.dayzhud.mod.market;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Prices for items nobody wrote a price for.
 *
 * The alternative is hand-authoring a row per item, and that does not survive contact with a
 * real modpack: one tactical gear mod in this pack ships 238 armour pieces alone. Deriving
 * from the stats the item already declares means a pack can add or swap a gear mod and the
 * trader follows, the same way TACZ weapons already do.
 *
 * An explicit entry in prices.json always wins - this only fills the gap.
 */
public final class DerivedPrices {

    private DerivedPrices() {}

    /** Derived value of one of these, or 0 when nothing here applies. */
    public static int valueOf(ItemStack stack) {
        int armour = armourValue(stack);
        if (armour > 0) return armour;
        int food = foodValue(stack);
        if (food > 0) return food;
        return 0;
    }

    /**
     * Armour is priced off defence and toughness, with knockback resistance as a small
     * premium and durability as a multiplier.
     *
     * Toughness is weighted heavily relative to defence because that is what actually
     * separates a plate carrier from a cap in a pack with real ballistics - defence alone
     * would price a full set of cloth the same as a set of ceramics with the same total.
     */
    private static int armourValue(ItemStack stack) {
        if (!MarketConfig.DERIVE_ARMOR.get()) return 0;
        if (!(stack.getItem() instanceof ArmorItem armor)) return 0;

        int defense = armor.getDefense();
        float toughness = armor.getToughness();
        float knockback = armor.getMaterial().getKnockbackResistance();
        int durability = Math.max(1, stack.getMaxDamage());

        double base = 400 + defense * 2100.0 + toughness * 3400.0 + knockback * 9000.0;
        // Durability scaled gently: a piece that lasts twice as long is worth more, but not
        // twice as much, or a high-durability cosmetic outprices real armour.
        base *= 0.75 + Math.min(1.5, durability / 400.0) * 0.5;
        base *= MarketConfig.DERIVE_SCALE.get();

        // Damaged gear is worth less, which also stops a trader paying full price for a
        // helmet that ate a rifle round.
        if (stack.isDamaged()) {
            double left = 1.0 - (double) stack.getDamageValue() / durability;
            base *= Math.max(0.15, left);
        }
        return round(base);
    }

    private static int foodValue(ItemStack stack) {
        if (!MarketConfig.DERIVE_FOOD.get()) return 0;
        Item item = stack.getItem();
        if (!item.isEdible()) return 0;
        FoodProperties food = item.getFoodProperties();
        if (food == null) return 0;
        double base = food.getNutrition() * 95.0 + food.getSaturationModifier() * 260.0;
        if (!food.getEffects().isEmpty()) base += 900;
        base *= MarketConfig.DERIVE_SCALE.get();
        return round(base);
    }

    /** Prices read as prices, not as measurements. */
    private static int round(double value) {
        if (value < 50) return 0;
        long step = value < 2000 ? 10 : 100;
        long rounded = Math.round(value / step) * step;
        return (int) Math.max(step, Math.min(Integer.MAX_VALUE, rounded));
    }
}
