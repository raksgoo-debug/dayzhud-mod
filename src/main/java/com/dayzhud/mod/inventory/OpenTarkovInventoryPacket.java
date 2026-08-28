package com.dayzhud.mod.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * Client sends this (in place of vanilla opening its own InventoryScreen) when the
 * inventory key is pressed. The server responds by opening our real, synced
 * TarkovInventoryMenu via the normal NetworkHooks flow, which sends the client the open-
 * screen packet that triggers our registered MenuScreens factory automatically.
 */
public class OpenTarkovInventoryPacket {

    public static void encode(OpenTarkovInventoryPacket packet, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenTarkovInventoryPacket decode(FriendlyByteBuf buf) {
        return new OpenTarkovInventoryPacket();
    }

    public static void handle(OpenTarkovInventoryPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Player player = ctx.getSender();
            if (player == null) return;
            MenuProvider provider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("Inventory");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int windowId, net.minecraft.world.entity.player.Inventory inv, Player p) {
                    return new TarkovInventoryMenu(windowId, inv);
                }
            };
            // MUST write the full payload, in the order TarkovMenuTypes reads it - see its
            // class notes. The client factory reads all three fields unconditionally; write
            // fewer and it throws, and the screen silently never opens.
            NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, provider,
                    buf -> {
                        buf.writeVarInt(0);       // no container - just the inventory screen
                        buf.writeBoolean(false);  // not a corpse
                        buf.writeVarInt(0);       // no curio slots
                    });
        });
        ctx.setPacketHandled(true);
    }
}
