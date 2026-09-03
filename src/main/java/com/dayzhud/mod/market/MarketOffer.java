package com.dayzhud.mod.market;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One line in the shop: a prototype stack, what it costs, and where it files.
 *
 * The prototype is a full ItemStack rather than an item id so that TACZ weapons and
 * lrtactical consumables - which share one registry name and differ only in NBT - can be
 * listed at all.
 *
 * {@code sub} is the section within a category ("helmets" inside "armor"). Empty means the
 * category has no sections, which is normal for most of them.
 */
public record MarketOffer(ItemStack prototype, long price, String category, String sub) {

    public MarketOffer(ItemStack prototype, long price, String category) {
        this(prototype, price, category, "");
    }

    public static void write(FriendlyByteBuf buf, MarketOffer offer) {
        buf.writeItem(offer.prototype());
        buf.writeVarLong(offer.price());
        buf.writeUtf(offer.category());
        buf.writeUtf(offer.sub());
    }

    public static MarketOffer read(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        long price = buf.readVarLong();
        String category = buf.readUtf();
        String sub = buf.readUtf();
        return new MarketOffer(stack, price, category, sub);
    }
}
