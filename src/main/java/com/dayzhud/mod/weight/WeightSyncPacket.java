package com.dayzhud.mod.weight;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: carried weight and the three limits, in kg. Display and sprint block only;
 *  the server applies the real penalties. */
public class WeightSyncPacket {

    private final float[] values;   // kg, overweight, heavy, critical

    public WeightSyncPacket(float[] values) {
        this.values = values;
    }

    public static void encode(WeightSyncPacket p, FriendlyByteBuf buf) {
        for (float v : p.values) buf.writeFloat(v);
    }

    public static WeightSyncPacket decode(FriendlyByteBuf buf) {
        return new WeightSyncPacket(new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()});
    }

    public static void handle(WeightSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientWeight.accept(p.values));
        ctx.get().setPacketHandled(true);
    }
}
