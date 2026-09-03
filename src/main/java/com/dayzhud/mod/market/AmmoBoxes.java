package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Makes tarkovdayz's ammo boxes open into TACZ rounds.
 *
 * They were sitting in the shop doing nothing at all, which is worse than not stocking them -
 * a player pays for one, right-clicks it, and gets no feedback whatsoever. So a box that
 * cannot resolve its ammo is dropped from the catalogue too (see MarketCatalog): either it
 * works or it is not for sale.
 *
 * The mapping is config, not code, because ammo ids belong to whatever gun pack is installed.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class AmmoBoxes {

    public record Box(ResourceLocation ammoId, int count) {}

    private static Map<Item, Box> boxes;

    private AmmoBoxes() {}

    public static void invalidate() {
        boxes = null;
    }

    private static synchronized void load() {
        if (boxes != null) return;
        Map<Item, Box> map = new LinkedHashMap<>();
        for (String row : MarketConfig.AMMO_BOXES.get()) {
            int eq = row.indexOf('=');
            if (eq <= 0) continue;
            ResourceLocation boxId = ResourceLocation.tryParse(row.substring(0, eq).trim());
            if (boxId == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(boxId);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;

            String rest = row.substring(eq + 1).trim();
            int comma = rest.indexOf(',');
            String ammo = comma < 0 ? rest : rest.substring(0, comma);
            int count = 30;
            if (comma >= 0) {
                try {
                    count = Math.max(1, Integer.parseInt(rest.substring(comma + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            ResourceLocation ammoId = ResourceLocation.tryParse(ammo.trim());
            if (ammoId != null) map.put(item, new Box(ammoId, count));
        }
        boxes = map;
    }

    /** The box definition for this item, or null. */
    public static Box of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        load();
        return boxes.get(stack.getItem());
    }

    /** True when this box's ammo actually exists in the installed gun pack. */
    public static boolean isUsable(ItemStack stack) {
        Box box = of(stack);
        if (box == null) return false;
        return !TaczMarketCompat.makeAmmo(box.ammoId(), box.count()).isEmpty();
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        open(event.getItemStack(), player, event.getHand(), event);
    }

    /**
     * RightClickItem does not fire when the crosshair is on a block, so opening a box while
     * looking at the floor would otherwise do nothing - the same trap the laptop hit.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isShiftKeyDown()) return;   // placing a block against something
        open(event.getItemStack(), player, event.getHand(), event);
    }

    private static void open(ItemStack stack, ServerPlayer player, InteractionHand hand,
                             PlayerInteractEvent event) {
        Box box = of(stack);
        if (box == null) return;

        ItemStack ammo = TaczMarketCompat.makeAmmo(box.ammoId(), box.count());
        if (ammo.isEmpty()) {
            DayzHudMod.LOGGER.warn("Ammo box {} maps to '{}', which no installed gun pack "
                    + "provides - check market.ammoBoxes.boxes",
                    ForgeRegistries.ITEMS.getKey(stack.getItem()), box.ammoId());
            return;
        }

        stack.shrink(1);
        if (!player.getInventory().add(ammo)) player.drop(ammo, false);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        event.setCanceled(true);
    }
}
