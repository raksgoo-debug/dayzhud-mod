package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
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
 * now lives here, server-side, because it drives real effects and those have to be
 * authoritative. The client is sent the value for its HUD (see {@link SkillStatePacket}); it
 * no longer computes its own, so gauge and effects can never disagree.
 *
 * THE MODEL. Your body eases toward a target at {@link #SMOOTHING} per tick - about 87% of
 * the way in five seconds - so you have time to react rather than being cooked on contact.
 * The target is built from:
 *
 *   biome base temperature, normalised to 0..1
 *     + sun          if it is day, the sky is visible and it is not raining
 *     - night chill  if it is night and the sky is visible
 *     - rain chill   if rain is falling on you
 *     - wetness      up to a full {@link #WET_CHILL} while soaked, fading as you dry
 *     + insulation   per equipped armor piece
 *     = 1.0 flat     if you are in lava or on fire
 *
 * SHELTER FALLS OUT OF THIS FOR FREE: with no sky above you, none of the sun/night/rain terms
 * apply, so being indoors or underground parks you at the biome's baseline. Getting out of
 * the weather is the answer to both extremes, which is exactly the behaviour we want.
 *
 * WETNESS is why swimming works as a heat dump. Water sets it to full and it decays over
 * {@link #WETNESS_DRY_TICKS}, so a swim keeps you cool for a minute afterwards - and being
 * caught in the rain at night is genuinely miserable, as it should be.
 *
 * NOTHING HERE DEALS DAMAGE. Temperature costs you stamina and burns through food and water
 * faster; it never takes a heart. Deliberate: an ambient stat that can kill you while you're
 * reading a chest is a bad experience, and the pressure works fine without it.
 *
 * TUNING: every number that matters is a constant at the top of this class.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class TemperatureSystem {

    /** How fast body temperature eases toward the environment. Higher = twitchier. */
    private static final float SMOOTHING = 0.02f;

    /** Comfortable band at Acclimation 0. Outside this the penalties start. */
    private static final float COLD_EDGE = 0.20f;
    private static final float HOT_EDGE = 0.80f;

    /** How far past an edge before the penalties get heavier. */
    private static final float SEVERE_MARGIN = 0.12f;

    /** Ticks between penalty applications. 40 = every 2 seconds. */
    private static final int EFFECT_INTERVAL = 40;

    /** Ticks between state syncs. 3 ticks is ~7/sec - the stamina bar needs the resolution. */
    private static final int SYNC_INTERVAL = 3;

    private static final float DISCOMFORT_EXHAUSTION = 0.08f;
    /** Multiplier applied to the penalties once past SEVERE_MARGIN. */
    private static final float SEVERE_MULTIPLIER = 2.5f;

    // --- Environment terms -------------------------------------------------------------
    private static final float SUN_WARMTH = 0.08f;
    private static final float NIGHT_CHILL = 0.10f;
    private static final float RAIN_CHILL = 0.10f;
    private static final float WET_CHILL = 0.25f;
    private static final float ARMOR_INSULATION_PER_PIECE = 0.03f;

    /** Ticks to dry off completely from soaked. 1200 = one minute. */
    private static final int WETNESS_DRY_TICKS = 1200;
    /** Ticks of standing in rain to become fully soaked. 600 = thirty seconds. */
    private static final int WETNESS_RAIN_TICKS = 600;

    /** How much one drink pulls you toward cool. Applies to any DRINK-animation item. */
    private static final float DRINK_COOLING = 0.06f;

    private static final Map<UUID, Float> TEMPERATURE = new HashMap<>();
    private static final Map<UUID, Float> WETNESS = new HashMap<>();
    private static final Map<UUID, Integer> TICK_COUNTER = new HashMap<>();

    private TemperatureSystem() {}

    /** Current body temperature, 0 (freezing) to 1 (scorching). Neutral if not yet tracked. */
    public static float temperatureOf(Player player) {
        return TEMPERATURE.getOrDefault(player.getUUID(), 0.5f);
    }

    /** How soaked this player is, 0..1. Drives the wetness term and could drive a HUD icon. */
    public static float wetnessOf(Player player) {
        return WETNESS.getOrDefault(player.getUUID(), 0f);
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

    /**
     * 0 comfortable, 1 uncomfortable, 2 severe - in EITHER direction.
     *
     * Shared so the stamina penalty and the exhaustion penalty can never disagree about
     * whether you're suffering, which they would if each re-derived the thresholds.
     */
    public static int discomfortLevel(Player player) {
        float temperature = temperatureOf(player);
        int acclimation = SkillCapability.levelOf(player, Skill.ACCLIMATION);
        float coldEdge = coldEdgeFor(acclimation);
        float hotEdge = hotEdgeFor(acclimation);

        if (temperature < coldEdge - SEVERE_MARGIN || temperature > hotEdge + SEVERE_MARGIN) {
            return 2;
        }
        if (temperature < coldEdge || temperature > hotEdge) {
            return 1;
        }
        return 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();

        float wetness = updateWetness(player, id);
        float target = targetFor(player, wetness);
        // First tick for this player starts AT the target rather than at neutral, so someone
        // logging into a snow biome isn't given a free grace period of drifting down into it.
        float current = TEMPERATURE.getOrDefault(id, target);
        float updated = current + (target - current) * SMOOTHING;
        TEMPERATURE.put(id, updated);

        int tick = TICK_COUNTER.merge(id, 1, Integer::sum);

        if (tick % EFFECT_INTERVAL == 0) {
            applyEffects(player);
        }
        if (tick % SYNC_INTERVAL == 0) {
            SkillEffects.sync(player);
        }
    }

    /**
     * Drinking cools you. Matched on the DRINK use animation rather than on specific items,
     * so a thirst mod's canteens and vanilla bottles both work without naming either.
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItem();
        if (stack.getUseAnimation() != UseAnim.DRINK) return;

        UUID id = player.getUUID();
        Float current = TEMPERATURE.get(id);
        if (current == null) return;
        TEMPERATURE.put(id, clamp01(current - DRINK_COOLING));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        TEMPERATURE.remove(id);
        WETNESS.remove(id);
        TICK_COUNTER.remove(id);
    }

    /**
     * Advances how soaked the player is and returns the new value.
     *
     * Water soaks you instantly; rain takes {@link #WETNESS_RAIN_TICKS}; drying takes
     * {@link #WETNESS_DRY_TICKS}. Called exactly once per player per tick.
     */
    private static float updateWetness(ServerPlayer player, UUID id) {
        float wetness = WETNESS.getOrDefault(id, 0f);

        if (player.isInWater()) {
            wetness = 1f;
        } else if (player.level().isRainingAt(player.blockPosition())) {
            wetness = Math.min(1f, wetness + 1f / WETNESS_RAIN_TICKS);
        } else {
            wetness = Math.max(0f, wetness - 1f / WETNESS_DRY_TICKS);
        }

        WETNESS.put(id, wetness);
        return wetness;
    }

    /**
     * Where this player's body temperature is heading right now, given weather, shelter,
     * how wet they are and what they're wearing.
     */
    private static float targetFor(Player player, float wetness) {
        if (player.isInLava() || player.isOnFire()) return 1.0f;

        Level level = player.level();
        BlockPos pos = player.blockPosition();

        Biome biome = level.getBiome(pos).value();
        // Biome base temperature runs roughly -0.5 (frozen peaks) to 2.0 (nether/desert);
        // this maps that onto 0..1.
        float target = clamp01((biome.getBaseTemperature() + 0.5f) / 2.5f);

        // Sky terms. With a roof over you NONE of these apply - which is what makes shelter
        // the answer to both extremes rather than a separate mechanic bolted on.
        if (level.canSeeSky(pos)) {
            if (level.isRainingAt(pos)) {
                target -= RAIN_CHILL;
            } else if (level.isDay()) {
                target += SUN_WARMTH;
            } else {
                target -= NIGHT_CHILL;
            }
        }

        // Being wet is the big one, and it lingers - see updateWetness.
        target -= WET_CHILL * wetness;

        // Armor insulates: each worn piece nudges you warmer. This is what makes gearing up
        // a genuine answer to a cold biome rather than something you just endure.
        int pieces = 0;
        for (ItemStack piece : player.getInventory().armor) {
            if (!piece.isEmpty()) pieces++;
        }
        target += pieces * ARMOR_INSULATION_PER_PIECE;

        return clamp01(target);
    }

    /**
     * The cost of being too hot or too cold: food and water burn faster, and (via
     * StaminaSystem, which reads {@link #discomfortLevel}) stamina recovers more slowly.
     *
     * NO DAMAGE, by design. Heat and cold squeeze your resources and your endurance, which
     * is pressure you can feel and act on; they never quietly kill you while you're stood
     * in a menu.
     */
    private static void applyEffects(ServerPlayer player) {
        // Creative and spectator players are exempt - burning a builder's hunger bar in a
        // snow biome would be pure annoyance.
        if (player.isCreative() || player.isSpectator()) return;

        int discomfort = discomfortLevel(player);
        if (discomfort == 0) return;

        float severity = discomfort >= 2 ? SEVERE_MULTIPLIER : 1f;

        // Heat costs double: sweating drives thirst, and exhaustion is the lever that reaches
        // both vanilla hunger and any thirst mod that drains on it.
        boolean hot = temperatureOf(player)
                > hotEdgeFor(SkillCapability.levelOf(player, Skill.ACCLIMATION));
        float base = hot ? DISCOMFORT_EXHAUSTION * 2f : DISCOMFORT_EXHAUSTION;

        player.causeFoodExhaustion(base * severity);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
