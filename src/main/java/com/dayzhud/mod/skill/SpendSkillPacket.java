package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "buy the next level of this skill".
 *
 * The packet carries ONLY which skill. It deliberately does not carry the level being bought
 * or the price - the server reads the player's current level, recomputes the cost from
 * {@link Skill#costFor}, and charges that. A client that lies can therefore only ask for a
 * skill it isn't allowed to have, which the checks below refuse; it can't name its own price
 * or skip to level 10.
 */
public class SpendSkillPacket {

    private final Skill skill;

    public SpendSkillPacket(Skill skill) {
        this.skill = skill;
    }

    public static void encode(SpendSkillPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.skill.id());
    }

    public static SpendSkillPacket decode(FriendlyByteBuf buf) {
        // Bounded read: an oversized string from a hostile client can't allocate unbounded.
        return new SpendSkillPacket(Skill.byId(buf.readUtf(32)));
    }

    public static void handle(SpendSkillPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || packet.skill == null) return;

            PlayerSkills skills = SkillCapability.of(player);
            if (skills == null) return;

            int current = skills.getLevel(packet.skill);
            if (current >= packet.skill.maxLevel()) return;

            int cost = packet.skill.costFor(current + 1);
            if (player.experienceLevel < cost) return;

            // Charge first, then grant - so an exception between the two can't hand out a
            // free level. giveExperienceLevels with a negative amount is how vanilla itself
            // deducts levels (enchanting, anvils).
            player.giveExperienceLevels(-cost);
            skills.increment(packet.skill);

            SkillEffects.reapply(player);
            SkillEffects.sync(player);

            DayzHudMod.LOGGER.debug("[dayzhud] {} bought {} level {} for {} XP levels.",
                    player.getGameProfile().getName(), packet.skill.id(), current + 1, cost);
        });
        ctx.setPacketHandled(true);
    }
}
