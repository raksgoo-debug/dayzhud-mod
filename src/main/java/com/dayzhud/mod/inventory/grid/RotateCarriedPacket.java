package com.dayzhud.mod.inventory.grid;

import com.dayzhud.mod.inventory.TarkovInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "R while carrying a multi-cell item" - toggles the rotated flag on whatever's on the
 * cursor. No payload: the server reads the carried stack off the menu itself.
 *
 * The client also flips its own local copy immediately on keypress, for instant visual
 * feedback rather than waiting on a round trip - see the screen's key handler. Both sides
 * need to agree by the time a placement click arrives, since that's when the rotated flag
 * actually gets read to decide what fits where.
 */
public class RotateCarriedPacket {

    public static void encode(RotateCarriedPacket packet, FriendlyByteBuf buf) {
    }

    public static RotateCarriedPacket decode(FriendlyByteBuf buf) {
        return new RotateCarriedPacket();
    }

    public static void handle(RotateCarriedPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (!(sender.containerMenu instanceof TarkovInventoryMenu menu)) return;
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty() || !ItemGrid.isMultiCell(carried)) return;
            ItemGrid.setRotated(carried, !ItemGrid.isRotated(carried));
        });
        ctx.setPacketHandled(true);
    }
}
