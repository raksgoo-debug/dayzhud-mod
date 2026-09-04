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

    public static void sendPrices(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.PricesSync(MarketPrices.all()));
    }

    public static void sendCatalogue(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.Catalogue(MarketCatalog.revision(), MarketCatalog.offers()));
    }
}
