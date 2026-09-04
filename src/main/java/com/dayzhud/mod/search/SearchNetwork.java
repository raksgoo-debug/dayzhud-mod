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

    public static void sendMask(ServerPlayer player, TarkovInventoryMenu menu) {
        Container searched = menu.searchedContainer();
        if (searched == null) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new Packet(new int[0]));
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Packet(SearchProgress.maskedSlots(searched, player,
                        menu.searchedMenuOffset(), searched.getContainerSize())));
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
