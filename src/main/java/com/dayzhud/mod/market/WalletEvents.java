package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wallet lifecycle: absorbing cash, surviving death, and staying in sync with the client.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class WalletEvents {

    private WalletEvents() {}

    /**
     * Rouble notes go straight into the balance instead of the inventory.
     *
     * Handled at pickup rather than by scanning the inventory on a timer so that the number
     * is right the instant the player walks over the money, and so a full inventory never
     * leaves cash on the ground.
     */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!MarketConfig.ENABLED.get() || !MarketConfig.AUTO_DEPOSIT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        long unit = CurrencyItems.valueOf(stack);
        if (unit <= 0) return;

        Wallet wallet = WalletCapability.of(player);
        if (wallet == null) return;

        int count = stack.getCount();
        wallet.add(unit * count);

        // take() drives the pickup animation and sound the player expects; the entity is
        // then removed by hand because we are cancelling vanilla's own pickup handling.
        player.take(entity, count);
        entity.discard();
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.25f, 1.6f);
        MarketNetwork.syncWallet(player);
        event.setCanceled(true);
    }

    /**
     * Carry the balance across a respawn or a dimension change.
     *
     * Capabilities are NOT copied automatically on clone, and the original player entity's
     * capabilities have to be revived first - on death the provider is already invalidated,
     * so reading it without reviveCaps() silently yields an empty wallet and the player
     * respawns broke.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        Wallet old = WalletCapability.of(event.getOriginal());
        Wallet fresh = WalletCapability.of(event.getEntity());
        if (old != null && fresh != null) fresh.copyFrom(old);
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarketNetwork.sendPrices(player);
            MarketNetwork.syncWallet(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MarketNetwork.syncWallet(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MarketNetwork.syncWallet(player);
    }

    /** Datapack reload: re-read prices, then push the new table to everyone. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new MarketPriceLoader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        CurrencyItems.invalidate();
        MarketCatalog.invalidate();
        if (event.getPlayer() != null) {
            MarketNetwork.sendPrices(event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(MarketNetwork::sendPrices);
        }
    }
}
