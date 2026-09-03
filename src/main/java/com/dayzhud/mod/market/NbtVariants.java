package com.dayzhud.mod.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Items that are really many items wearing one registry name, distinguished by a single NBT
 * string.
 *
 * TACZ does this for guns and ammo; LesRaisins Tactical does it for medical, throwables and
 * melee - {@code lrtactical:consumable} carries a {@code ConsumableId} of
 * {@code lrtactical:ai2}, {@code lrtactical:m67} and so on. Pricing those by registry name
 * gives twenty different medkits one shared price, and an entry for "lrtactical:ai2" matches
 * nothing at all, because no such item is registered. That is exactly what happened first
 * time round: the whole mod was silently absent from the trader.
 *
 * Driven by config rather than hardcoded, so the next mod built this way needs a config line
 * instead of a code change. Key format: {@code <item id>=<nbt tag>}.
 */
public final class NbtVariants {

    private static Map<Item, String> tagByItem;
    private static Map<String, String> tagById;

    private NbtVariants() {}

    public static void invalidate() {
        tagByItem = null;
        tagById = null;
    }

    private static synchronized void load() {
        if (tagByItem != null) return;
        Map<Item, String> byItem = new LinkedHashMap<>();
        Map<String, String> byId = new LinkedHashMap<>();
        for (String row : MarketConfig.NBT_VARIANT_ITEMS.get()) {
            int eq = row.indexOf('=');
            if (eq <= 0) continue;
            String itemId = row.substring(0, eq).trim();
            String tag = row.substring(eq + 1).trim();
            if (tag.isEmpty()) continue;
            byId.put(itemId, tag);
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) byItem.put(item, tag);
        }
        tagByItem = byItem;
        tagById = byId;
    }

    /**
     * The price key for a variant stack - "&lt;item id&gt;/&lt;variant id&gt;" - or null when
     * this item is not a variant carrier or is carrying no id.
     */
    public static String keyOf(ItemStack stack) {
        load();
        String tag = tagByItem.get(stack.getItem());
        if (tag == null) return null;
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(tag)) return null;
        String value = nbt.getString(tag);
        if (value.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id + "/" + value;
    }

    /** Rebuilds a stack from a key produced by {@link #keyOf}. Empty when it does not fit. */
    public static ItemStack stackFor(String key, int count) {
        load();
        int slash = key.indexOf('/');
        if (slash <= 0) return ItemStack.EMPTY;
        String itemId = key.substring(0, slash);
        String tag = tagById.get(itemId);
        if (tag == null) return ItemStack.EMPTY;

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item, Math.max(1, count));
        CompoundTag nbt = new CompoundTag();
        nbt.putString(tag, key.substring(slash + 1));
        stack.setTag(nbt);
        return stack;
    }

    /** True when this key names a variant carrier this config knows about. */
    public static boolean isVariantKey(String key) {
        load();
        int slash = key.indexOf('/');
        return slash > 0 && tagById.containsKey(key.substring(0, slash));
    }
}
