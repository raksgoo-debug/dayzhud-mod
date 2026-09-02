package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The price table: what everything is worth, on both sides.
 *
 * Loaded server-side from data/dayzhud/market/prices.json (see MarketPriceLoader) and
 * synced whole to each client on join, because the client needs it for sell quotes and
 * tooltips and it is only a few kilobytes.
 *
 * KEYS ARE STRINGS, NOT ITEM IDS. TACZ puts every gun on a single item and distinguishes
 * them in NBT, so keying by item id would give every gun in the game one shared price.
 * {@link #keyOf} therefore returns a synthetic "tacz:gun/<id>" for those, and a plain item
 * id for everything else. One lookup path, no special cases at the call sites.
 */
public final class MarketPrices {

    /** value / buyable / listing size / tab, per key. */
    public record Entry(int value, boolean buy, int count, String category) {}

    public static final String CAT_DEFAULT = "misc";

    private static Map<String, Entry> table = new LinkedHashMap<>();

    private MarketPrices() {}

    public static void set(Map<String, Entry> entries) {
        table = new LinkedHashMap<>(entries);
    }

    public static Map<String, Entry> all() {
        return table;
    }

    /**
     * The price-table key for a stack. TACZ guns and ammo get synthetic keys derived from
     * their NBT id; everything else is its registry name.
     */
    public static String keyOf(ItemStack stack) {
        if (stack.isEmpty()) return "";
        ResourceLocation gun = TaczMarketCompat.gunIdOf(stack).orElse(null);
        if (gun != null) return "tacz:gun/" + gun;
        ResourceLocation ammo = TaczMarketCompat.ammoIdOf(stack).orElse(null);
        if (ammo != null) return "tacz:ammo/" + ammo;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /** Value of ONE of this item, before any buy/sell multiplier. 0 means worthless. */
    public static int valueOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String key = keyOf(stack);
        Entry entry = table.get(key);
        if (entry != null) return entry.value();

        // No explicit entry: fall back to a derived price for TACZ content, so a pack that
        // adds a gun pack gets working prices without editing any data file.
        if (key.startsWith("tacz:gun/")) {
            if (!MarketConfig.TACZ_SELL_GUNS.get()) return 0;
            ResourceLocation id = ResourceLocation.tryParse(key.substring("tacz:gun/".length()));
            Integer price = id == null ? null : TaczMarketCompat.priceOfGun(id);
            return price == null ? 0 : price;
        }
        if (key.startsWith("tacz:ammo/")) {
            if (!MarketConfig.TACZ_SELL_GUNS.get()) return 0;
            return MarketConfig.TACZ_AMMO_PRICE.get();
        }
        return 0;
    }

    /** What a trader pays for one of these. Never rounds a valuable item down to nothing. */
    public static long sellPrice(ItemStack stack) {
        int value = valueOf(stack);
        if (value <= 0) return 0;
        long paid = Math.round(value * MarketConfig.SELL_MULTIPLIER.get());
        return Math.max(1L, paid);
    }

    /** Total a trader pays for a whole stack. */
    public static long sellPrice(ItemStack stack, int count) {
        return sellPrice(stack) * Math.max(0, count);
    }

    public static long buyPrice(int unitValue, int count) {
        long total = Math.round((double) unitValue * count * MarketConfig.BUY_MULTIPLIER.get());
        return Math.max(1L, total);
    }

    // ---------------------------------------------------------------- wire format

    public static void write(FriendlyByteBuf buf) {
        write(buf, table);
    }

    public static void write(FriendlyByteBuf buf, Map<String, Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().value());
            buf.writeBoolean(e.getValue().buy());
            buf.writeVarInt(e.getValue().count());
            buf.writeUtf(e.getValue().category());
        }
    }

    public static Map<String, Entry> read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Entry> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String key = buf.readUtf();
            int value = buf.readVarInt();
            boolean buy = buf.readBoolean();
            int count = buf.readVarInt();
            String cat = buf.readUtf();
            out.put(key, new Entry(value, buy, count, cat));
        }
        return out;
    }

    // ---------------------------------------------------------------- json

    /** Parses one prices.json body into entries. Bad rows are skipped, not fatal. */
    public static Map<String, Entry> parse(JsonElement root) {
        Map<String, Entry> out = new HashMap<>();
        if (!root.isJsonObject()) return out;
        JsonObject obj = root.getAsJsonObject();
        JsonElement entries = obj.get("entries");
        if (entries == null || !entries.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : entries.getAsJsonObject().entrySet()) {
            try {
                JsonObject v = e.getValue().getAsJsonObject();
                int value = v.get("value").getAsInt();
                boolean buy = v.has("buy") && v.get("buy").getAsBoolean();
                int count = v.has("count") ? Math.max(1, v.get("count").getAsInt()) : 1;
                String cat = v.has("category") ? v.get("category").getAsString() : CAT_DEFAULT;
                out.put(e.getKey(), new Entry(value, buy, count, cat));
            } catch (Exception ex) {
                DayzHudMod.LOGGER.warn("Skipping malformed market price entry '{}': {}",
                        e.getKey(), ex.toString());
            }
        }
        return out;
    }
}
