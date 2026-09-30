package com.dayzhud.mod.injury;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: light wounds, heavy wounds, pain (0-100), painkiller seconds left.
 *  Display only - the HUD icons and the pain vignette. */
public class InjurySyncPacket {

    private final int[] values;

    public InjurySyncPacket(int[] values) {
        this.values = values;
    }

    public static void encode(InjurySyncPacket p, FriendlyByteBuf buf) {
        for (int v : p.values) buf.writeVarInt(v);
    }

    public static InjurySyncPacket decode(FriendlyByteBuf buf) {
        return new InjurySyncPacket(new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()});
    }

    public static void handle(InjurySyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientInjuries.accept(p.values));
        ctx.get().setPacketHandled(true);
    }
}
