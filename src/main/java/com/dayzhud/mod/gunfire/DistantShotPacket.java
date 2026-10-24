package com.dayzhud.mod.gunfire;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: a TACZ gun was fired out of earshot of TACZ's own sound (2.19.0).
 *
 * Carries the gun itself so the client can ask TACZ which sound that gun makes - the same
 * lookup TACZ does, so a resource pack's replacement sounds are honoured.
 */
public class DistantShotPacket {

    final double x, y, z;
    final ItemStack gun;
    final boolean suppressed;
    final int shooterId;
    /** How far this gun carries, so the client can fade it out at the right distance. */
    final float range;

    public DistantShotPacket(double x, double y, double z, ItemStack gun, boolean suppressed, int shooterId,
                             float range) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.gun = gun;
        this.suppressed = suppressed;
        this.shooterId = shooterId;
        this.range = range;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public ItemStack gun() { return gun; }
    public boolean suppressed() { return suppressed; }
    public int shooterId() { return shooterId; }
    public float range() { return range; }

    public static void encode(DistantShotPacket p, FriendlyByteBuf buf) {
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeItem(p.gun);
        buf.writeBoolean(p.suppressed);
        buf.writeVarInt(p.shooterId);
        buf.writeFloat(p.range);
    }

    public static DistantShotPacket decode(FriendlyByteBuf buf) {
        return new DistantShotPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readItem(),
                buf.readBoolean(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(DistantShotPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.dayzhud.mod.client.DistantGunfireClient.play(p)));
        ctx.get().setPacketHandled(true);
    }
}
