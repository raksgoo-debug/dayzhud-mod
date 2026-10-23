package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Convenience senders, so the call sites do not each repeat the distributor boilerplate -
 * plus keeping an open trader screen's catalogue current.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class MarketNetwork {

    /** How often an open trader screen's catalogue is checked for changes (ticks). */
    private static final int REFRESH_INTERVAL = 20;

    /** The catalogue revision each player was last sent. */
    private static final Map<UUID, Integer> SENT_REVISION = new HashMap<>();

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
        // offers() first: it may rebuild, and the rebuild bumps the revision.
        var offers = MarketCatalog.offers();
        int revision = MarketCatalog.revision();
        SENT_REVISION.put(player.getUUID(), revision);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MarketPackets.Catalogue(revision, offers));
    }

    /**
     * Keeps an open trader screen current. The catalogue used to go out once, as the screen
     * opened - and TaCZ Magazines lists its magazines only once gun data has loaded, which
     * can be after that. The catalogue rebuilt itself on the next request, but nothing made
     * one while the screen stayed open, so the MAGAZINES section only showed up on the next
     * visit. Now, once a second, anyone with the trader open is sent the catalogue again if
     * it has changed since (offers() is what notices the late magazines and rebuilds).
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % REFRESH_INTERVAL != 0 || !(player.containerMenu instanceof MarketMenu)) return;
        MarketCatalog.offers();
        Integer sent = SENT_REVISION.get(player.getUUID());
        if (sent == null || sent != MarketCatalog.revision()) sendCatalogue(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT_REVISION.remove(event.getEntity().getUUID());
    }
}
