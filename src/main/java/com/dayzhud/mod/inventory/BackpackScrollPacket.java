package com.dayzhud.mod.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the server which row of a large backpack the client has scrolled to.
 *
 * This has to be synced rather than kept client-side: the scroll offset decides which
 * underlying inventory index each on-screen slot maps to, so if the two sides disagreed,
 * clicking a slot would move the wrong item.
 */
public class BackpackScrollPacket {

    private final int scrollRow;

    public BackpackScrollPacket(int scrollRow) {
        this.scrollRow = scrollRow;
    }

    public static void encode(BackpackScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.scrollRow);
    }

    public static BackpackScrollPacket decode(FriendlyByteBuf buf) {
        return new BackpackScrollPacket(buf.readVarInt());
    }

    public static void handle(BackpackScrollPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (sender.containerMenu instanceof TarkovInventoryMenu menu) {
                menu.setBackpackScroll(packet.scrollRow);
            }
        });
        ctx.setPacketHandled(true);
    }
}
