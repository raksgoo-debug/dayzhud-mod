package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Safe zones are actually safe: no damage to a player standing in one.
 *
 * Cancelled at {@link LivingAttackEvent} rather than LivingHurtEvent, because attack fires
 * first and cancelling there also suppresses the hurt animation, the sound and the knockback.
 * Cancelling only the damage leaves a player being visibly shot and flinching while taking
 * nothing, which reads as a bug.
 *
 * Priority is HIGHEST so this wins over anything that would otherwise modify the damage; the
 * point of a safe zone is that nothing else gets a say.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class SafeZoneProtection {

    /** Who was inside last tick, so entering and leaving can be announced once each. */
    private static final Set<UUID> INSIDE = new HashSet<>();

    private static int tickCounter;

    private SafeZoneProtection() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!MarketConfig.ENABLED.get() || !MarketConfig.SAFE_ZONE_PROTECTION.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!SafeZoneData.get(level).isInside(player.blockPosition())) return;
        event.setCanceled(true);
    }

    /**
     * Entering and leaving messages, checked twice a second rather than every tick.
     *
     * A zone boundary is invisible, and a player who does not know they have left it finds out
     * by dying. Twenty checks a second for something that changes at walking pace is waste.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!MarketConfig.ENABLED.get() || !MarketConfig.SAFE_ZONE_FEEDBACK.get()) return;
        if (++tickCounter % 10 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            boolean inside = SafeZoneData.get(level).isInside(player.blockPosition());
            boolean was = INSIDE.contains(player.getUUID());
            if (inside == was) continue;
            if (inside) {
                INSIDE.add(player.getUUID());
                player.displayClientMessage(
                        Component.translatable("message.dayzhud.market.zone_enter"), true);
            } else {
                INSIDE.remove(player.getUUID());
                player.displayClientMessage(
                        Component.translatable("message.dayzhud.market.zone_leave"), true);
            }
        }
    }
}
