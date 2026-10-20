package com.dayzhud.mod.noise;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: a noise this player heard (2.19.0).
 *
 * Carries a compass bearing from the listener and a 0-1 closeness, never the source's
 * position - so a client can't be modded into a wallhack from these. {@code self} marks a
 * noise the player made themselves (it only feeds their own noise meter).
 */
public class NoisePacket {

    final int kind;
    /** World yaw from the listener towards the noise, in Minecraft's convention (degrees). */
    final float bearing;
    /** 1 = right next to you, 0 = at the edge of hearing. */
    final float closeness;
    final boolean self;

    public NoisePacket(NoiseKind kind, float bearing, float closeness, boolean self) {
        this.kind = kind.ordinal();
        this.bearing = bearing;
        this.closeness = closeness;
        this.self = self;
    }

    private NoisePacket(int kind, float bearing, float closeness, boolean self) {
        this.kind = kind;
        this.bearing = bearing;
        this.closeness = closeness;
        this.self = self;
    }

    public static void encode(NoisePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.kind);
        buf.writeFloat(p.bearing);
        buf.writeFloat(p.closeness);
        buf.writeBoolean(p.self);
    }

    public static NoisePacket decode(FriendlyByteBuf buf) {
        return new NoisePacket(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(NoisePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientNoise.accept(NoiseKind.byOrdinal(p.kind), p.bearing, p.closeness, p.self));
        ctx.get().setPacketHandled(true);
    }
}
