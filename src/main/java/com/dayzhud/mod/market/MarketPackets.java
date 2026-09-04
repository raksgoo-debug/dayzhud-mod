package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every market packet, in one file so the wire formats sit next to each other and a change
 * to one is hard to make without seeing the others.
 *
 * Packet IDs are positional in NetworkHandler, so these are all registered at the END of
 * the existing list. Inserting one in the middle would renumber everything after it and a
 * client on the previous build would decode later packets as the wrong type.
 */
public final class MarketPackets {

    private MarketPackets() {}

    // ------------------------------------------------------------------ S -> C wallet

    public static class WalletSync {
        public final long balance;

        public WalletSync(long balance) {
            this.balance = balance;
        }

        public static void encode(WalletSync p, FriendlyByteBuf buf) {
            buf.writeLong(p.balance);
        }

        public static WalletSync decode(FriendlyByteBuf buf) {
            return new WalletSync(buf.readLong());
        }

        public static void handle(WalletSync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientWallet.accept(p.balance));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ S -> C prices

    public static class PricesSync {
        public final Map<String, MarketPrices.Entry> entries;

        public PricesSync(Map<String, MarketPrices.Entry> entries) {
            this.entries = entries;
        }

        public static void encode(PricesSync p, FriendlyByteBuf buf) {
            MarketPrices.write(buf, p.entries);
        }

        public static PricesSync decode(FriendlyByteBuf buf) {
            return new PricesSync(MarketPrices.read(buf));
        }

        public static void handle(PricesSync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> MarketPrices.set(p.entries));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ S -> C catalogue

    public static class Catalogue {
        public final int revision;
        public final List<MarketOffer> offers;

        public Catalogue(int revision, List<MarketOffer> offers) {
            this.revision = revision;
            this.offers = offers;
        }

        public static void encode(Catalogue p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.revision);
            buf.writeVarInt(p.offers.size());
            for (MarketOffer o : p.offers) MarketOffer.write(buf, o);
        }

        public static Catalogue decode(FriendlyByteBuf buf) {
            int revision = buf.readVarInt();
            int n = buf.readVarInt();
            List<MarketOffer> offers = new ArrayList<>(n);
            for (int i = 0; i < n; i++) offers.add(MarketOffer.read(buf));
            return new Catalogue(revision, offers);
        }

        public static void handle(Catalogue p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientMarketState.accept(p.revision, p.offers));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ S -> C clear mask

    /**
     * Tells the client to drop Rummage's slot mask.
     *
     * Sent explicitly after a menu swap instead of clearing in the screen's init(), because
     * init() runs when the open-screen packet arrives and Rummage's state packet may or may
     * not have landed by then - the race can go either way. A packet sent AFTER ours on the
     * same connection cannot arrive before it, so this is deterministic.
     *
     * Rummage will not send a clearing packet of its own: it skips the send entirely when its
     * recomputed bitset is empty, which is exactly the case after we replace a corpse menu.
     */
    public static class ClearRummageMask {
        public static void encode(ClearRummageMask p, FriendlyByteBuf buf) {}

        public static ClearRummageMask decode(FriendlyByteBuf buf) {
            return new ClearRummageMask();
        }

        public static void handle(ClearRummageMask p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(RummageCompat::clearClientMask);
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C -> S buy

    public static class Buy {
        public final int revision;
        public final int index;
        public final int count;

        public Buy(int revision, int index, int count) {
            this.revision = revision;
            this.index = index;
            this.count = count;
        }

        public static void encode(Buy p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.revision);
            buf.writeVarInt(p.index);
            buf.writeVarInt(p.count);
        }

        public static Buy decode(FriendlyByteBuf buf) {
            return new Buy(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Buy p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof MarketMenu)) return;

                // The client addresses offers by index, so a catalogue that changed under it
                // means the index no longer points at what was clicked. Refuse and resend.
                if (p.revision != MarketCatalog.revision()) {
                    MarketNetwork.sendCatalogue(player);
                    player.displayClientMessage(
                            Component.translatable("message.dayzhud.market.stale"), true);
                    return;
                }
                List<MarketOffer> offers = MarketCatalog.offers();
                if (p.index < 0 || p.index >= offers.size()) return;

                MarketOffer offer = offers.get(p.index);
                int batches = Math.max(1, Math.min(64, p.count));
                long cost = offer.price() * batches;

                Wallet wallet = WalletCapability.of(player);
                if (wallet == null) return;
                if (!wallet.spend(cost)) {
                    player.displayClientMessage(
                            Component.translatable("message.dayzhud.market.poor"), true);
                    return;
                }

                for (int i = 0; i < batches; i++) {
                    ItemStack give = offer.prototype().copy();
                    if (!player.getInventory().add(give)) {
                        // Full inventory: drop at the player's feet rather than voiding
                        // something they have already paid for.
                        player.drop(give, false);
                    }
                }
                MarketNetwork.syncWallet(player);
            });
            context.setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C -> S sell

    public static class Sell {
        public static void encode(Sell p, FriendlyByteBuf buf) {}

        public static Sell decode(FriendlyByteBuf buf) {
            return new Sell();
        }

        public static void handle(Sell p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof MarketMenu menu)) return;
                long paid = menu.sellTrayContents();
                if (paid <= 0) return;
                Wallet wallet = WalletCapability.of(player);
                if (wallet == null) return;
                wallet.add(paid);
                menu.broadcastChanges();
                MarketNetwork.syncWallet(player);
                player.displayClientMessage(Component.translatable(
                        "message.dayzhud.market.sold", Money.format(paid)), true);
            });
            context.setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C -> S withdraw

    public static class Withdraw {
        public final long amount;

        public Withdraw(long amount) {
            this.amount = amount;
        }

        public static void encode(Withdraw p, FriendlyByteBuf buf) {
            buf.writeVarLong(p.amount);
        }

        public static Withdraw decode(FriendlyByteBuf buf) {
            return new Withdraw(buf.readVarLong());
        }

        public static void handle(Withdraw p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.containerMenu instanceof MarketMenu)) return;
                Wallet wallet = WalletCapability.of(player);
                if (wallet == null) return;

                long want = Math.max(0, p.amount);
                List<Map.Entry<Item, Long>> notes = CurrencyItems.denominations();
                if (notes.isEmpty() || want <= 0) return;

                // Pay out largest-note-first, and only what the balance covers. Anything the
                // smallest note cannot express stays in the wallet rather than rounding away.
                long remaining = Math.min(want, wallet.getBalance());
                long paid = 0;
                for (Map.Entry<Item, Long> note : notes) {
                    long each = note.getValue();
                    int n = (int) Math.min(remaining / each, 64L * 36);
                    while (n > 0) {
                        int give = Math.min(n, note.getKey().getMaxStackSize());
                        ItemStack stack = new ItemStack(note.getKey(), give);
                        if (!player.getInventory().add(stack)) player.drop(stack, false);
                        n -= give;
                        remaining -= (long) give * each;
                        paid += (long) give * each;
                    }
                }
                if (paid > 0) {
                    wallet.spend(paid);
                    MarketNetwork.syncWallet(player);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
