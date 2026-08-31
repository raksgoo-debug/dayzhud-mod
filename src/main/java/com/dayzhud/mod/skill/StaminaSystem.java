package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    /** Regeneration multipliers while uncomfortable, then severely so - hot OR cold. */
    private static final float MILD_REGEN_PENALTY = 0.6f;
    private static final float SEVERE_REGEN_PENALTY = 0.35f;

    /** Squared blocks/tick above which the player counts as genuinely moving. */
    private static final double MOVEMENT_EPSILON_SQR = 1.0E-4;

    /**
     * Stamina that must be recovered before the exhaustion sound can fire again. At the normal
     * regen rate half a bar takes roughly as long as the breathing clip runs, so the cue can't
     * stack on top of itself however hard you push.
     */
    private static final float EXHAUSTION_RESET_FRACTION = 0.5f;

    private static final Map<UUID, Float> STAMINA = new HashMap<>();
    private static final Map<UUID, double[]> LAST_POSITION = new HashMap<>();
    /** Players currently bottomed out, so the gasp plays once per exhaustion rather than per tick. */
    private static final Set<UUID> EXHAUSTED = new HashSet<>();

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
        updateExhaustionCue(player, id, stamina / max);
    }

    /**
     * Fires the out-of-breath sound on the transition to empty, and re-arms it only once the
     * player has genuinely recovered. Sent with playNotifySound, which reaches that player and
     * nobody else - your own lungs, not a position in the world.
     */
    private static void updateExhaustionCue(ServerPlayer player, UUID id, float fraction) {
        if (fraction <= 0f) {
            if (EXHAUSTED.add(id)) {
                player.playNotifySound(ModSounds.HEAVY_BREATHING.get(), SoundSource.PLAYERS, 1f, 1f);
            }
        } else if (fraction >= EXHAUSTION_RESET_FRACTION) {
            EXHAUSTED.remove(id);
        }
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
        EXHAUSTED.remove(id);
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
     * Being too hot OR too cold slows recovery. Heat was previously ignored here, which made
     * overheating cost nothing but hunger - now both ends tire you, which is the point of
     * having a comfortable band at all.
     *
     * The threshold decision is delegated to TemperatureSystem.discomfortLevel rather than
     * re-derived from the edges here: two copies of that arithmetic would eventually disagree
     * about whether the player is suffering, and the stamina bar would contradict the gauge.
     * Acclimation widens the band, so a trained player stops feeling this at all.
     */
    private static float regenMultiplier(Player player) {
        return switch (TemperatureSystem.discomfortLevel(player)) {
            case 2 -> SEVERE_REGEN_PENALTY;
            case 1 -> MILD_REGEN_PENALTY;
            default -> 1f;
        };
    }
}
