package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Body temperature, and the consequences of letting it get away from you.
 *
 * WHAT CHANGED AND WHY: temperature used to be computed client-side in VitalsTracker purely
 * to draw a gauge - nothing read it, so being frozen or baking had no effect whatsoever. It
 * now lives here, server-side, because anything that damages a player or drains their hunger
 * has to be authoritative. The client is sent the value for its HUD and its stamina model
 * (see {@link SkillStatePacket}); it no longer computes its own, so gauge and effects can
 * never disagree.
 *
 * THE MODEL, deliberately simple and readable over realistic:
 *
 *   target = biome base temperature, normalised to 0..1
 *          - 0.25 if you're in water
 *          + 0.03 per equipped armor piece      (insulation)
 *          = 1.0 flat if you're in lava or on fire
 *
 * Actual body temperature eases toward that target at {@link #SMOOTHING} per tick, so
 * stepping into a desert doesn't cook you instantly - you have time to react, which is the
 * whole point of a survival gauge.
 *
 * THE BANDS: outside 0.20..0.80 you're uncomfortable and hunger burns faster; past a further
 * {@link #SEVERE_MARGIN} you take damage every {@link #EFFECT_INTERVAL} ticks. ACCLIMATION
 * widens both edges by 2% per level, so at cap you tolerate 0.00..1.00 - a fully acclimated
 * player is effectively immune, which is a deliberate reward for a 130-XP-level investment.
 *
 * TUNING: every number that matters is a constant at the top of this class.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class TemperatureSystem {

    /** How fast body temperature eases toward the environment. Higher = twitchier. */
    private static final float SMOOTHING = 0.02f;

    /** Comfortable band at Acclimation 0. Outside this, hunger burns faster. */
    private static final float COLD_EDGE = 0.20f;
    private static final float HOT_EDGE = 0.80f;

    /** How far past an edge before it actually hurts you. */
    private static final float SEVERE_MARGIN = 0.12f;

    /** Ticks between damage/exhaustion applications. 40 = every 2 seconds. */
    private static final int EFFECT_INTERVAL = 40;

    /** Ticks between temperature syncs to the client. 10 = 4x/second, plenty for a gauge. */
    private static final int SYNC_INTERVAL = 10;

    private static final float SEVERE_DAMAGE = 1.0f;
    private static final float DISCOMFORT_EXHAUSTION = 0.08f;

    private static final float WATER_CHILL = 0.25f;
    private static final float ARMOR_INSULATION_PER_PIECE = 0.03f;

    private static final Map<UUID, Float> TEMPERATURE = new HashMap<>();
    private static final Map<UUID, Integer> TICK_COUNTER = new HashMap<>();

    private TemperatureSystem() {}

    /** Current body temperature, 0 (freezing) to 1 (scorching). Neutral if not yet tracked. */
    public static float temperatureOf(Player player) {
        return TEMPERATURE.getOrDefault(player.getUUID(), 0.5f);
    }

    /**
     * Where the cold edge sits for this player, after Acclimation. The hot edge is the
     * mirror image, so both are derived from one number.
     */
    public static float coldEdgeFor(int acclimationLevel) {
        return COLD_EDGE - Skill.ACCLIMATION.fractionAt(acclimationLevel);
    }

    public static float hotEdgeFor(int acclimationLevel) {
        return HOT_EDGE + Skill.ACCLIMATION.fractionAt(acclimationLevel);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        float target = targetFor(player);
        // First tick for this player starts AT the target rather than at neutral, so someone
        // logging into a snow biome isn't given a free grace period of drifting down into it.
        float current = TEMPERATURE.getOrDefault(id, target);
        float updated = current + (target - current) * SMOOTHING;
        TEMPERATURE.put(id, updated);

        int tick = TICK_COUNTER.merge(id, 1, Integer::sum);

        if (tick % EFFECT_INTERVAL == 0) {
            applyEffects(player, updated);
        }
        if (tick % SYNC_INTERVAL == 0) {
            SkillEffects.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        TEMPERATURE.remove(id);
        TICK_COUNTER.remove(id);
    }

    /**
     * Where this player's body temperature is heading right now, given surroundings and gear.
     */
    private static float targetFor(Player player) {
        if (player.isInLava() || player.isOnFire()) return 1.0f;

        Biome biome = player.level().getBiome(player.blockPosition()).value();
        // Biome base temperature runs roughly -0.5 (frozen peaks) to 2.0 (nether/desert);
        // this maps that onto 0..1.
        float target = clamp01((biome.getBaseTemperature() + 0.5f) / 2.5f);

        if (player.isInWater()) target = Math.max(0f, target - WATER_CHILL);

        // Armor insulates: each worn piece nudges you warmer. This is what makes gearing up
        // a genuine answer to a cold biome rather than something you just endure.
        int pieces = 0;
        for (ItemStack piece : player.getInventory().armor) {
            if (!piece.isEmpty()) pieces++;
        }
        target += pieces * ARMOR_INSULATION_PER_PIECE;

        return clamp01(target);
    }

    private static void applyEffects(ServerPlayer player, float temperature) {
        // Creative and spectator players are exempt - being unable to build in a snow biome
        // without taking chip damage would be miserable.
        if (player.isCreative() || player.isSpectator()) return;

        int acclimation = SkillCapability.levelOf(player, Skill.ACCLIMATION);
        float coldEdge = coldEdgeFor(acclimation);
        float hotEdge = hotEdgeFor(acclimation);

        if (temperature < coldEdge) {
            // Shivering burns energy even before it's dangerous.
            player.causeFoodExhaustion(DISCOMFORT_EXHAUSTION);
            if (temperature < coldEdge - SEVERE_MARGIN) {
                player.hurt(player.damageSources().freeze(), SEVERE_DAMAGE);
            }
        } else if (temperature > hotEdge) {
            // Heat drives thirst. Exhaustion is the lever that reaches BOTH vanilla hunger
            // and any thirst mod that drains on exhaustion, without touching their internals.
            player.causeFoodExhaustion(DISCOMFORT_EXHAUSTION * 2f);
            if (temperature > hotEdge + SEVERE_MARGIN) {
                player.hurt(player.damageSources().onFire(), SEVERE_DAMAGE);
            }
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
