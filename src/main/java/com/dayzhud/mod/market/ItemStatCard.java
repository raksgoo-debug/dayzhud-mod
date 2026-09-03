package com.dayzhud.mod.market;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The stat block shown in the trader's details panel.
 *
 * Runs on the client. TACZ's gun index is common-side data, so the same reflective compat that
 * prices a weapon on the server can read its ballistics here - no extra packet, and the panel
 * stays correct for whatever gun pack is installed.
 *
 * Bars are drawn for stats with a meaningful ceiling and plain numbers for the rest; a bar
 * against an invented maximum is worse than no bar, so anything without an honest scale
 * (weight, calibre, magazine size) gets a value only.
 */
public final class ItemStatCard {

    /** {@code bar} is 0..1, or negative for "no bar, value only". */
    public record Stat(String label, String value, float bar) {
        public static Stat of(String label, String value) {
            return new Stat(label, value, -1f);
        }
    }

    private ItemStatCard() {}

    public static List<Stat> forStack(ItemStack stack) {
        List<Stat> out = new ArrayList<>();
        if (stack.isEmpty()) return out;
        if (gun(stack, out)) return out;
        if (armour(stack, out)) return out;
        food(stack, out);
        tool(stack, out);
        if (stack.isDamageableItem()) {
            out.add(Stat.of("DURABILITY", (stack.getMaxDamage() - stack.getDamageValue())
                    + " / " + stack.getMaxDamage()));
        }
        return out;
    }

    private static boolean gun(ItemStack stack, List<Stat> out) {
        TaczMarketCompat.GunStats g = TaczMarketCompat.statsOf(stack);
        if (g == null) return false;

        float perShot = g.damage() * Math.max(1, g.pellets());
        out.add(new Stat("DAMAGE", fmt(perShot) + (g.pellets() > 1
                ? " (" + g.pellets() + "x" + fmt(g.damage()) + ")" : ""),
                clamp(perShot / 30f)));
        out.add(new Stat("FIRE RATE", g.rpm() + " RPM", clamp(g.rpm() / 1200f)));
        out.add(new Stat("DPS", fmt(perShot * g.rpm() / 60f), clamp(perShot * g.rpm() / 60f / 300f)));
        out.add(new Stat("PENETRATION", String.valueOf(g.pierce()), clamp(g.pierce() / 6f)));
        out.add(new Stat("ARMOUR IGNORE", Math.round(g.armorIgnore() * 100) + "%",
                clamp(g.armorIgnore())));
        out.add(new Stat("MUZZLE VELOCITY", Math.round(g.speed()) + " m/s", clamp(g.speed() / 1200f)));
        if (g.headshot() > 0) out.add(Stat.of("HEADSHOT", "x" + fmt(g.headshot())));
        out.add(Stat.of("MAGAZINE", g.magazine() + " rounds"));
        if (g.ammoId() != null) out.add(Stat.of("CALIBRE", pretty(g.ammoId())));
        if (g.fireModes() != null && !g.fireModes().isEmpty()) {
            out.add(Stat.of("FIRE MODE", g.fireModes()));
        }
        if (g.weight() > 0) out.add(Stat.of("WEIGHT", fmt(g.weight()) + " kg"));
        return true;
    }

    private static boolean armour(ItemStack stack, List<Stat> out) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return false;
        out.add(new Stat("ARMOUR", String.valueOf(armor.getDefense()),
                clamp(armor.getDefense() / 10f)));
        out.add(new Stat("TOUGHNESS", fmt(armor.getToughness()),
                clamp(armor.getToughness() / 5f)));
        float kb = armor.getMaterial().getKnockbackResistance();
        if (kb > 0) out.add(new Stat("KNOCKBACK RES", Math.round(kb * 100) + "%", clamp(kb)));
        out.add(Stat.of("SLOT", armor.getEquipmentSlot().getName().toUpperCase(Locale.ROOT)));
        if (stack.isDamageableItem()) out.add(Stat.of("DURABILITY", String.valueOf(stack.getMaxDamage())));
        return true;
    }

    private static void food(ItemStack stack, List<Stat> out) {
        Item item = stack.getItem();
        if (!item.isEdible()) return;
        FoodProperties food = item.getFoodProperties();
        if (food == null) return;
        out.add(new Stat("NUTRITION", String.valueOf(food.getNutrition()),
                clamp(food.getNutrition() / 20f)));
        out.add(new Stat("SATURATION", fmt(food.getSaturationModifier()),
                clamp(food.getSaturationModifier() / 2f)));
        if (!food.getEffects().isEmpty()) {
            out.add(Stat.of("EFFECTS", String.valueOf(food.getEffects().size())));
        }
    }

    private static void tool(ItemStack stack, List<Stat> out) {
        if (!(stack.getItem() instanceof TieredItem tiered)) return;
        out.add(new Stat("ATTACK", fmt(tiered.getTier().getAttackDamageBonus() + 1f),
                clamp((tiered.getTier().getAttackDamageBonus() + 1f) / 12f)));
        out.add(Stat.of("TIER", String.valueOf(tiered.getTier().getLevel())));
    }

    private static String pretty(String id) {
        int colon = id.indexOf(':');
        String path = colon < 0 ? id : id.substring(colon + 1);
        return path.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static String fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 0.05f) return String.valueOf(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
