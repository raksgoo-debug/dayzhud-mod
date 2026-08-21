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
    /** false = the player's own backpack grid, true = the corpse loot list. */
    private final boolean corpse;

    public BackpackScrollPacket(int scrollRow, boolean corpse) {
        this.scrollRow = scrollRow;
        this.corpse = corpse;
    }

    public static void encode(BackpackScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.scrollRow);
        buf.writeBoolean(packet.corpse);
    }

    public static BackpackScrollPacket decode(FriendlyByteBuf buf) {
        return new BackpackScrollPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(BackpackScrollPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (sender.containerMenu instanceof TarkovInventoryMenu menu) {
                if (packet.corpse) {
                    if (menu.corpseLootView != null) menu.corpseLootView.setScrollRow(packet.scrollRow);
                } else {
                    menu.setBackpackScroll(packet.scrollRow);
                }
                // Scrolling remaps EVERY slot to a different underlying index at once.
                // broadcastChanges() only sends slots whose contents it believes changed,
                // which leaves stale items rendered in rows that were remapped - the
                // "duplicated items that vanish when touched" effect. A full resend is the
                // correct response to a wholesale remap.
                menu.broadcastFullState();

                // Scrolling changes WHICH underlying item each slot points at, but normal
                // sync only sends slots whose contents changed - so the client keeps
                // drawing its stale cached stacks and items appear duplicated in the rows
                // above until you touch them. Forcing a full resend fixes that.
                menu.sendAllDataToRemote();
            }
        });
        ctx.setPacketHandled(true);
    }
}
