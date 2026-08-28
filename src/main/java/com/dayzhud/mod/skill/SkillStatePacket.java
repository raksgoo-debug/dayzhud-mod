package com.dayzhud.mod.skill;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client. Carries everything the client needs to draw the skills screen and to
 * apply the client-side half of the effects: the player's skill levels, and their current
 * body temperature.
 *
 * WHY THE TWO TRAVEL TOGETHER: temperature is recomputed server-side constantly and skills
 * change rarely, so bundling costs five extra varints on a packet that's already being sent.
 * In exchange the client can never be holding a fresh temperature alongside stale skills -
 * which matters, because the client uses BOTH together (Acclimation decides at what
 * temperature the cold stamina penalty starts biting).
 */
public class SkillStatePacket {

    private final PlayerSkills skills;
    private final float temperature01;

    public SkillStatePacket(PlayerSkills skills, float temperature01) {
        this.skills = skills;
        this.temperature01 = temperature01;
    }

    public static void encode(SkillStatePacket packet, FriendlyByteBuf buf) {
        packet.skills.writeTo(buf);
        buf.writeFloat(packet.temperature01);
    }

    public static SkillStatePacket decode(FriendlyByteBuf buf) {
        PlayerSkills skills = new PlayerSkills();
        skills.readFrom(buf);
        return new SkillStatePacket(skills, buf.readFloat());
    }

    public static void handle(SkillStatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // enqueueWork hops onto the client thread - touching ClientSkillState from the
        // network thread would be a data race against the render thread reading it.
        ctx.enqueueWork(() -> ClientSkillState.accept(packet.skills, packet.temperature01));
        ctx.setPacketHandled(true);
    }
}
