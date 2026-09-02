package com.dayzhud.mod.market;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One line in the shop: a prototype stack, what it costs, and which tab it sits under.
 *
 * The prototype is a full ItemStack rather than an item id so that TACZ weapons - which are
 * all the same item and differ only in NBT - can be listed at all.
 */
public record MarketOffer(ItemStack prototype, long price, String category) {

    public static void write(FriendlyByteBuf buf, MarketOffer offer) {
        buf.writeItem(offer.prototype());
        buf.writeVarLong(offer.price());
        buf.writeUtf(offer.category());
    }

    public static MarketOffer read(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        long price = buf.readVarLong();
        String category = buf.readUtf();
        return new MarketOffer(stack, price, category);
    }
}
