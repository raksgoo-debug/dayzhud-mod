package com.dayzhud.mod.market;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds the list of things a trader will sell you.
 *
 * Rebuilt lazily and cached, because it depends on the price table (datapack-reloadable)
 * and on whatever gun pack is installed, neither of which changes during play. Anything
 * that can change it calls {@link #invalidate()}.
 */
public final class MarketCatalog {

    public static final String CAT_WEAPONS = "weapons";
    public static final String CAT_AMMO = "ammo";

    private static List<MarketOffer> cached;

    /**
     * Bumped every time the catalogue changes. Buy packets carry the revision the client
     * was looking at, because offers are addressed by index: a datapack reload between the
     * screen opening and the click would otherwise silently sell the player a different
     * item at the price of the one they clicked.
     */
    private static int revision;

    private MarketCatalog() {}

    public static void invalidate() {
        cached = null;
        revision++;
    }

    public static int revision() {
        return revision;
    }

    public static synchronized List<MarketOffer> offers() {
        if (cached == null) cached = build();
        return cached;
    }

    /** Tabs, in display order, with the ones that exist in the current catalogue only. */
    public static List<String> categories() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MarketOffer o : offers()) seen.add(o.category());
        List<String> ordered = new ArrayList<>(seen);
        // Stable, readable tab order; unknown categories fall to the end alphabetically.
        List<String> preferred = List.of(CAT_WEAPONS, CAT_AMMO, "meds", "provisions",
                "supplies", "materials", "electronics", "valuables", "misc");
        ordered.sort((a, b) -> {
            int ia = preferred.indexOf(a), ib = preferred.indexOf(b);
            if (ia < 0 && ib < 0) return a.compareTo(b);
            if (ia < 0) return 1;
            if (ib < 0) return -1;
            return Integer.compare(ia, ib);
        });
        return ordered;
    }

    private static List<MarketOffer> build() {
        List<MarketOffer> out = new ArrayList<>();

        for (Map.Entry<String, MarketPrices.Entry> e : MarketPrices.all().entrySet()) {
            MarketPrices.Entry entry = e.getValue();
            if (!entry.buy()) continue;
            ItemStack stack = stackFor(e.getKey(), entry.count());
            if (stack.isEmpty()) continue;   // item belongs to a mod that isn't installed
            out.add(new MarketOffer(stack, MarketPrices.buyPrice(entry.value(), entry.count()),
                    entry.category()));
        }

        if (TaczMarketCompat.isActive()) {
            if (MarketConfig.TACZ_STOCK_GUNS.get()) {
                for (TaczMarketCompat.GunEntry gun : TaczMarketCompat.listGuns()) {
                    ItemStack stack = TaczMarketCompat.makeGun(gun.id());
                    if (stack.isEmpty()) continue;
                    // An explicit "tacz:gun/<id>" row in prices.json overrides the derived price.
                    MarketPrices.Entry override = MarketPrices.all().get("tacz:gun/" + gun.id());
                    int unit = override != null ? override.value() : gun.price();
                    out.add(new MarketOffer(stack, MarketPrices.buyPrice(unit, 1), CAT_WEAPONS));
                }
            }
            if (MarketConfig.TACZ_STOCK_AMMO.get()) {
                int batch = MarketConfig.TACZ_AMMO_BATCH.get();
                for (ResourceLocation id : TaczMarketCompat.listAmmo()) {
                    ItemStack stack = TaczMarketCompat.makeAmmo(id, batch);
                    if (stack.isEmpty()) continue;
                    MarketPrices.Entry override = MarketPrices.all().get("tacz:ammo/" + id);
                    Integer derived = TaczMarketCompat.priceOfAmmo(id);
                    int unit = override != null ? override.value()
                            : (derived != null ? derived : MarketConfig.TACZ_AMMO_PRICE.get());
                    out.add(new MarketOffer(stack, MarketPrices.buyPrice(unit, batch), CAT_AMMO));
                }
            }
        }

        out.sort((a, b) -> {
            int c = a.category().compareTo(b.category());
            return c != 0 ? c : Long.compare(a.price(), b.price());
        });
        return Collections.unmodifiableList(out);
    }

    private static ItemStack stackFor(String key, int count) {
        if (key.startsWith("tacz:gun/")) {
            ResourceLocation id = ResourceLocation.tryParse(key.substring("tacz:gun/".length()));
            return id == null ? ItemStack.EMPTY : TaczMarketCompat.makeGun(id);
        }
        if (key.startsWith("tacz:ammo/")) {
            ResourceLocation id = ResourceLocation.tryParse(key.substring("tacz:ammo/".length()));
            return id == null ? ItemStack.EMPTY : TaczMarketCompat.makeAmmo(id, count);
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        // getValue falls back to AIR for an unknown id, which is how an entry for an item
        // from a mod the pack does not have quietly drops out of the shop instead of
        // listing a stack of nothing.
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }
}
