package com.dayzhud.mod.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Switches the corpse loot panel between its INVENTORY and BACKPACK tabs.
 *
 * Like the scroll position, the selected tab decides which underlying index each on-screen
 * slot maps to, so it must be identical on both sides or clicks would move the wrong item.
 * The server also resends the full container state afterwards - see the handler.
 */
public class CorpseTabPacket {

    public static final int TAB_INVENTORY = 0;
    public static final int TAB_BACKPACK = 1;

    private final int tab;

    public CorpseTabPacket(int tab) {
        this.tab = tab;
    }

    public static void encode(CorpseTabPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.tab);
    }

    public static CorpseTabPacket decode(FriendlyByteBuf buf) {
        return new CorpseTabPacket(buf.readVarInt());
    }

    public static void handle(CorpseTabPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (sender.containerMenu instanceof TarkovInventoryMenu menu) {
                menu.setCorpseTab(packet.tab);
                // Remapping every slot at once means the usual "only send what changed"
                // sync isn't enough - push the whole state so nothing is left showing a
                // stale item.
                menu.broadcastFullState();
            }
        });
        ctx.setPacketHandled(true);
    }
}
