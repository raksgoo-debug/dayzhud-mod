package com.dayzhud.mod.quest;

import com.dayzhud.mod.market.MarketCatalog;
import com.dayzhud.mod.market.NbtVariants;
import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Quest item keys (2.15.0) - the market's key formats plus #tags - matched against stacks.
 * Used on both sides: the server takes delivered items, the client shows "have 2/3".
 * Only the inventory, hotbar and offhand count; what's in your backpack, you get out first.
 */
public final class QuestItems {

    private QuestItems() {}

    public static boolean matches(ItemStack stack, String key) {
        if (stack.isEmpty()) return false;
        if (key.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(key.substring(1));
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        if (key.startsWith("tacz:gun/")) {
            return TaczMarketCompat.gunIdOf(stack).map(id -> key.equals("tacz:gun/" + id)).orElse(false);
        }
        if (key.indexOf('/') > 0) return key.equals(NbtVariants.keyOf(stack));
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && key.equals(id.toString());
    }

    public static int countIn(Player player, String key) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.items) if (matches(s, key)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (matches(s, key)) n += s.getCount();
        return n;
    }

    /** Removes {@code count} matching items; the caller has checked there are enough. */
    public static void take(Player player, String key, int count) {
        Inventory inv = player.getInventory();
        for (var list : java.util.List.of(inv.items, inv.offhand)) {
            for (ItemStack s : list) {
                if (count <= 0) return;
                if (!matches(s, key)) continue;
                int n = Math.min(count, s.getCount());
                s.shrink(n);
                count -= n;
            }
        }
    }

    /** A stack to show for this key, or EMPTY. */
    public static ItemStack icon(String key, int count) {
        if (key.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(key.substring(1));
            if (tag == null) return ItemStack.EMPTY;
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) {
                Item item = holder.value();
                return new ItemStack(item, Math.max(1, Math.min(count, item.getMaxStackSize())));
            }
            return ItemStack.EMPTY;
        }
        return MarketCatalog.stackFor(key, count);
    }
}
