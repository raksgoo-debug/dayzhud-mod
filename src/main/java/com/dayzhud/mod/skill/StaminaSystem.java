package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stamina, owned by the server.
 *
 * WHY IT MOVED: this used to be a static float on the client, ticked in VitalsTracker. That
 * made it trivially forgeable - a modified client could simply never run out - and it meant
 * the server had no idea how tired anyone was, so nothing server-side could ever depend on
 * it. It now lives here and is pushed to the client for display only.
 *
 * MOVEMENT DETECTION: the client's own deltaMovement isn't reliable server-side (the server
 * sets player position from movement packets rather than simulating it), so "are you actually
 * running" is measured from the distance the player's position moved since last tick. That
 * also closes the obvious cheat of holding sprint while standing still to avoid the drain
 * without paying for it.
 *
 * The one real consequence is unchanged: at zero stamina your sprint is cut. Doing that here
 * via setSprinting(false) propagates to the client through the normal entity-data sync, so
 * the player genuinely stops rather than being asked nicely to.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class StaminaSystem {

    public static final float BASE_MAX_STAMINA = 100f;

    private static final float DRAIN_PER_TICK_SPRINT = 0.45f;
    private static final float DRAIN_PER_JUMP = 6f;
    private static final float REGEN_PER_TICK = 0.25f;

    /** Fraction of sprint drain removed per Endurance level (0.04 = 40% cheaper at cap). */
    private static final float ENDURANCE_DRAIN_RELIEF = 0.04f;
    /** Drain can never fall below this multiple of the base, however deep the skill goes. */
    private static final float MIN_DRAIN_MULTIPLIER = 0.2f;

    /** Regeneration multiplier while below the cold threshold. */
    private static final float COLD_REGEN_PENALTY = 0.5f;

    /** Squared blocks/tick above which the player counts as genuinely moving. */
    private static final double MOVEMENT_EPSILON_SQR = 1.0E-4;

    private static final Map<UUID, Float> STAMINA = new HashMap<>();
    private static final Map<UUID, double[]> LAST_POSITION = new HashMap<>();

    private StaminaSystem() {}

    public static float maxStaminaFor(Player player) {
        return BASE_MAX_STAMINA
                + Skill.ENDURANCE.magnitudeAt(SkillCapability.levelOf(player, Skill.ENDURANCE));
    }

    /** Current stamina as 0..1. Full when the player isn't tracked yet. */
    public static float stamina01(Player player) {
        float max = maxStaminaFor(player);
        if (max <= 0f) return 0f;
        float value = STAMINA.getOrDefault(player.getUUID(), max);
        return Math.max(0f, Math.min(1f, value / max));
    }

    /** Used by the reset command so a respec doesn't leave you gasping at an old cap. */
    public static void refill(Player player) {
        STAMINA.put(player.getUUID(), maxStaminaFor(player));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        float max = maxStaminaFor(player);
        float stamina = Math.min(STAMINA.getOrDefault(id, max), max);

        boolean moving = consumeMovement(player, id);

        if (player.isSprinting() && moving) {
            stamina = Math.max(0f, stamina - DRAIN_PER_TICK_SPRINT * drainMultiplier(player));
            if (stamina <= 0f) {
                player.setSprinting(false);
            }
        } else if (!player.isSprinting()) {
            stamina = Math.min(max, stamina + REGEN_PER_TICK * regenMultiplier(player));
        }

        STAMINA.put(id, stamina);
    }

    /** Jump cost. LivingJumpEvent fires server-side for a ServerPlayer, so this is authoritative. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        float max = maxStaminaFor(player);
        float stamina = Math.min(STAMINA.getOrDefault(id, max), max);
        STAMINA.put(id, Math.max(0f, stamina - DRAIN_PER_JUMP * drainMultiplier(player)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        STAMINA.remove(id);
        LAST_POSITION.remove(id);
    }

    /**
     * True when the player's position actually changed this tick. Records the new position as
     * a side effect, so it must be called exactly once per player per tick.
     */
    private static boolean consumeMovement(ServerPlayer player, UUID id) {
        double[] last = LAST_POSITION.put(id, new double[]{player.getX(), player.getZ()});
        if (last == null) return false;
        double dx = player.getX() - last[0];
        double dz = player.getZ() - last[1];
        return dx * dx + dz * dz > MOVEMENT_EPSILON_SQR;
    }

    private static float drainMultiplier(Player player) {
        int endurance = SkillCapability.levelOf(player, Skill.ENDURANCE);
        return Math.max(MIN_DRAIN_MULTIPLIER, 1f - endurance * ENDURANCE_DRAIN_RELIEF);
    }

    /**
     * Cold slows recovery - the first thing you notice as your temperature drops, well before
     * it starts doing damage. Acclimation moves the threshold, so a trained player stops
     * feeling it at all.
     */
    private static float regenMultiplier(Player player) {
        float temperature = TemperatureSystem.temperatureOf(player);
        float coldEdge = TemperatureSystem.coldEdgeFor(
                SkillCapability.levelOf(player, Skill.ACCLIMATION));
        return temperature < coldEdge ? COLD_REGEN_PENALTY : 1f;
    }
}
