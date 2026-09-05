package com.dayzhud.mod.search;

import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.TarkovInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Tells the client which menu slots are still unsearched, so it can draw them as covered. */
public final class SearchNetwork {

    private SearchNetwork() {}

    /** The mask this menu should currently show, as menu slot indices. */
    public static int[] maskFor(ServerPlayer player, TarkovInventoryMenu menu) {
        Container searched = menu.searchedContainer();
        if (searched == null) return new int[0];
        java.util.BitSet revealed = SearchProgress.forPlayer(searched, player);
        int[] body = menu.maskedBodyMenuSlots(revealed::get);
        int[] bag = menu.maskedBagMenuSlots();
        int[] all = new int[body.length + bag.length];
        System.arraycopy(body, 0, all, 0, body.length);
        System.arraycopy(bag, 0, all, body.length, bag.length);
        return all;
    }

    public static void sendMask(ServerPlayer player, int[] mask) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(mask));
    }

    public static void sendMask(ServerPlayer player, TarkovInventoryMenu menu) {
        sendMask(player, maskFor(player, menu));
    }

    public static class Packet {
        public final int[] slots;

        public Packet(int[] slots) {
            this.slots = slots;
        }

        public static void encode(Packet p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.slots.length);
            for (int i : p.slots) buf.writeVarInt(i);
        }

        public static Packet decode(FriendlyByteBuf buf) {
            int[] out = new int[buf.readVarInt()];
            for (int i = 0; i < out.length; i++) out[i] = buf.readVarInt();
            return new Packet(out);
        }

        public static void handle(Packet p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientSearchState.accept(p.slots));
            ctx.get().setPacketHandled(true);
        }
    }
}
