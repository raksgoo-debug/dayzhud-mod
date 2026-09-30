package com.dayzhud.mod.inventory;

import com.dayzhud.mod.compat.FirstAidCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The inventory's HEALTH tab (2.16.0): the First Aid item on the cursor was held on a limb
 * for its apply time - put its healer on that limb. The server reads the stack off the menu
 * itself; the limb is First Aid's EnumPlayerPart name. Like First Aid's own
 * MessageApplyHealingItem, the hold time is the client's to enforce.
 */
public record ApplyHealingPacket(String limb) {

    public static void encode(ApplyHealingPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.limb, 32);
    }

    public static ApplyHealingPacket decode(FriendlyByteBuf buf) {
        return new ApplyHealingPacket(buf.readUtf(32));
    }

    public static void handle(ApplyHealingPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !(sender.containerMenu instanceof TarkovInventoryMenu menu)) return;
            ItemStack carried = menu.getCarried();
            if (FirstAidCompat.applyHealing(sender, carried, packet.limb)) {
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                menu.broadcastChanges();
            }
        });
        ctx.setPacketHandled(true);
    }
}
