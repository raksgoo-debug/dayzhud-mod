package com.dayzhud.mod.market;

import com.dayzhud.mod.inventory.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Convenience senders, so the call sites do not each repeat the distributor boilerplate. */
public final class MarketNetwork {

    private MarketNetwork() {}

    public static void syncWallet(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.WalletSync(WalletCapability.balanceOf(player)));
    }

    /**
     * Replaces the client's Rummage mask with one computed for the menu we just opened.
     *
     * Rummage will not do it: it recomputes on container open, but its state packet is skipped
     * when the bitset is empty and - as the client log showed - the merged menu never gets a
     * replacement at all, so the client keeps the bitset from the corpse menu we swapped out.
     * Its own later updates, once the player starts searching, are correct; only the initial
     * state is wrong, so this corrects exactly that.
     */
    public static void syncRummageMask(ServerPlayer player) {
        if (!RummageCompat.isModLoaded()) return;
        int[] indices = RummageCompat.computeMaskIndices(player.containerMenu, player);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.ClearRummageMask(indices));
    }

    public static void sendPrices(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.PricesSync(MarketPrices.all()));
    }

    public static void sendCatalogue(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.Catalogue(MarketCatalog.revision(), MarketCatalog.offers()));
    }
}
