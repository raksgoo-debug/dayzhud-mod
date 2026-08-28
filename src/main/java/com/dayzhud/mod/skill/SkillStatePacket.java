package com.dayzhud.mod.skill;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client. Everything the client needs to draw the skills screen and the HUD: the
 * player's skill levels, their body temperature, and their stamina.
 *
 * WHY THEY TRAVEL TOGETHER: temperature and stamina are recomputed server-side every tick and
 * skills change rarely, so bundling costs five extra varints on a packet that is already
 * being sent. In exchange the client can never hold a fresh gauge reading alongside stale
 * skills - which would show, since Endurance sizes the stamina bar itself.
 *
 * All three values are display-only on the client. The server keeps the real ones.
 */
public class SkillStatePacket {

    private final PlayerSkills skills;
    private final float temperature01;
    private final float stamina01;

    public SkillStatePacket(PlayerSkills skills, float temperature01, float stamina01) {
        this.skills = skills;
        this.temperature01 = temperature01;
        this.stamina01 = stamina01;
    }

    public static void encode(SkillStatePacket packet, FriendlyByteBuf buf) {
        packet.skills.writeTo(buf);
        buf.writeFloat(packet.temperature01);
        buf.writeFloat(packet.stamina01);
    }

    public static SkillStatePacket decode(FriendlyByteBuf buf) {
        PlayerSkills skills = new PlayerSkills();
        skills.readFrom(buf);
        // Read order must match encode exactly - the fields are indistinguishable on the wire.
        float temperature = buf.readFloat();
        float stamina = buf.readFloat();
        return new SkillStatePacket(skills, temperature, stamina);
    }

    public static void handle(SkillStatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // enqueueWork hops onto the client thread - touching ClientSkillState from the
        // network thread would be a data race against the render thread reading it.
        ctx.enqueueWork(() ->
                ClientSkillState.accept(packet.skills, packet.temperature01, packet.stamina01));
        ctx.setPacketHandled(true);
    }
}
