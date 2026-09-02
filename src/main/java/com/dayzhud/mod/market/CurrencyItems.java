package com.dayzhud.mod.market;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Physical cash: which items are rouble notes and what each one is worth. */
public final class CurrencyItems {

    private static Map<Item, Long> values;
    private static List<Map.Entry<Item, Long>> descending;

    private CurrencyItems() {}

    public static void invalidate() {
        values = null;
        descending = null;
    }

    private static synchronized void load() {
        if (values != null) return;
        Map<Item, Long> map = new LinkedHashMap<>();
        for (String row : MarketConfig.CURRENCY_ITEMS.get()) {
            int eq = row.lastIndexOf('=');
            if (eq <= 0) continue;
            ResourceLocation id = ResourceLocation.tryParse(row.substring(0, eq).trim());
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            try {
                long value = Long.parseLong(row.substring(eq + 1).trim());
                if (value > 0) map.put(item, value);
            } catch (NumberFormatException ignored) {
            }
        }
        values = map;
        List<Map.Entry<Item, Long>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        descending = sorted;
    }

    /** Value of ONE of this item as cash, or 0 if it is not cash. */
    public static long valueOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        load();
        Long value = values.get(stack.getItem());
        return value == null ? 0L : value;
    }

    public static boolean isCurrency(ItemStack stack) {
        return valueOf(stack) > 0;
    }

    /** Note denominations, largest first - the order a withdrawal pays out in. */
    public static List<Map.Entry<Item, Long>> denominations() {
        load();
        return descending;
    }
}
