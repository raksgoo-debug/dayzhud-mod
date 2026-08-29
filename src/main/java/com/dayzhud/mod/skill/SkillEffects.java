package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.compat.ThirstWasTakenCompat;
import com.dayzhud.mod.inventory.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Applies the server-side half of the skills, and keeps the client's copy in sync.
 *
 * SPLIT OF RESPONSIBILITY: everything with real consequences lives here, on the server,
 * driven by the authoritative capability - max health, damage taken, hunger, thirst. The
 * client is told the numbers ({@link SkillStatePacket}) purely so it can draw them. Stamina
 * lives in {@link StaminaSystem} and temperature in {@link TemperatureSystem}, both also
 * server-side.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class SkillEffects {

    /**
     * Fixed UUID for the Vitality health modifier. It MUST be a constant: it's the handle
     * used to remove the previous modifier before adding the new one, and a fresh UUID each
     * time would stack modifiers and inflate health without bound.
     */
    private static final UUID VITALITY_MODIFIER =
            UUID.fromString("6b1a5f2c-3d4e-4a7b-9c8d-0e1f2a3b4c5d");

    /**
     * Last-seen exhaustion counters, so Metabolism can tell a RISE from the sharp drop that
     * happens when a point of food or thirst is actually spent. Keyed by player UUID; cleared
     * on logout so a long-running server doesn't accumulate entries for people who left.
     */
    private static final Map<UUID, Float> LAST_FOOD_EXHAUSTION = new HashMap<>();
    private static final Map<UUID, Float> LAST_THIRST_EXHAUSTION = new HashMap<>();

    private SkillEffects() {}

    // ---------------------------------------------------------------- attributes / sync

    /**
     * Re-derives every attribute-backed skill from the capability. Safe to call repeatedly -
     * the modifier is removed by its fixed UUID before being re-added, so this converges
     * rather than stacking however many times it runs.
     *
     * THE MODIFIER IS PERMANENT (saved into the player's attribute NBT), NOT TRANSIENT, and
     * that difference is a bug fix, not a style choice. Vanilla loads a player like this:
     *
     *     LivingEntity.readAdditionalSaveData:
     *         ... getAttributes().load(...)      <- attributes first
     *         ... setHealth(nbt "Health")        <- THEN health, clamped to current max
     *
     * With a transient modifier there was nothing in the attribute NBT, so at the moment
     * health was read the player's maximum was still the base 20 - and setHealth clamped a
     * saved 40 down to 20. Our login handler then raised the maximum back to 40, leaving
     * every single login at 20/40. That is exactly the "I always join with 50% HP" symptom.
     * A permanent modifier is restored WITH the attributes, before health is read, so the
     * saved value survives.
     *
     * KNOWN TRADE-OFF: a permanent modifier persists in the player's NBT. If this mod is
     * ever removed, affected players keep the bonus maximum health until something strips
     * the modifier. That is the accepted cost of loading in the right order.
     */
    public static void reapply(Player player) {
        int vitality = SkillCapability.levelOf(player, Skill.VITALITY);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        maxHealth.removeModifier(VITALITY_MODIFIER);
        float bonus = Skill.VITALITY.magnitudeAt(vitality);
        if (bonus > 0f) {
            maxHealth.addPermanentModifier(new AttributeModifier(
                    VITALITY_MODIFIER, "dayzhud.vitality", bonus,
                    AttributeModifier.Operation.ADDITION));
        }

        // Lowering the cap - a reset or respec - has to pull current health down with it.
        // Nothing in vanilla does that on its own, so without this a player who reset from
        // Vitality 10 would sit at 40 health with a maximum of 20.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Pushes skills, temperature and stamina to one player's client. */
    public static void sync(ServerPlayer player) {
        PlayerSkills skills = SkillCapability.of(player);
        if (skills == null) return;
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SkillStatePacket(skills,
                        TemperatureSystem.temperatureOf(player),
                        StaminaSystem.stamina01(player)));
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Carries skills across death and across the End-portal return.
     *
     * reviveCaps() is required: on death the old player's capabilities have already been
     * invalidated, and reading them without reviving returns empty - which is the classic
     * way capability data silently vanishes on death. We invalidate again afterwards so the
     * old entity doesn't linger as if it were still live.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        try {
            PlayerSkills old = SkillCapability.of(event.getOriginal());
            PlayerSkills fresh = SkillCapability.of(event.getEntity());
            if (old != null && fresh != null) fresh.copyFrom(old);
        } finally {
            event.getOriginal().invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
            sync(player);
        }
    }

    /**
     * Respawn builds a fresh entity whose maximum is the BASE maximum, and vanilla sets the
     * respawning player to that base value before we get here. Re-applying the modifier then
     * raises the ceiling but not the health, so without the explicit top-up you would respawn
     * at 20/40 - the same off-by-a-modifier problem as the login path, from the other end.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
            player.setHealth(player.getMaxHealth());
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_FOOD_EXHAUSTION.remove(id);
        LAST_THIRST_EXHAUSTION.remove(id);
    }

    // ---------------------------------------------------------------- Toughness

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int level = SkillCapability.levelOf(player, Skill.TOUGHNESS);
        if (level <= 0) return;

        float reduction = Skill.TOUGHNESS.fractionAt(level);   // 0.02 per level, 0.20 at cap
        event.setAmount(event.getAmount() * (1f - reduction));
    }

    // ---------------------------------------------------------------- Metabolism

    /**
     * Slows hunger and thirst by scaling down every INCREASE in their exhaustion counters.
     *
     * Both vanilla food and Thirst Was Taken work the same way: activity piles up an
     * "exhaustion" float, and when it crosses a threshold one point of food/thirst is spent
     * and the counter drops back. Intercepting the rise is therefore the correct place to slow
     * the drain - it happens before anything is spent, it leaves saturation semantics intact,
     * and it scales every source of exhaustion at once without knowing what any of them are.
     *
     * (An earlier version watched the food LEVEL and refunded points after the fact. That
     * worked, but it bypassed saturation and refunded food lost to any cause at all. This is
     * the real thing; it needed IThirst.getExhaustion/setExhaustion, which the TWT jar was
     * checked for rather than assumed.)
     *
     * Both counters are read back AFTER writing, so the stored baseline is whatever actually
     * landed - if a write silently fails, the next tick compares against reality instead of
     * against a value we only wished for.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        int level = SkillCapability.levelOf(player, Skill.METABOLISM);
        float keep = 1f - Skill.METABOLISM.fractionAt(level);   // 1.0 at level 0, 0.4 at cap

        dampenVanillaHunger(player, id, level, keep);
        dampenThirst(player, id, level, keep);
    }

    private static void dampenVanillaHunger(ServerPlayer player, UUID id, int level, float keep) {
        FoodData food = player.getFoodData();
        float now = food.getExhaustionLevel();
        Float previous = LAST_FOOD_EXHAUSTION.get(id);

        if (level > 0 && previous != null && now > previous) {
            food.setExhaustion(previous + (now - previous) * keep);
            now = food.getExhaustionLevel();
        }
        LAST_FOOD_EXHAUSTION.put(id, now);
    }

    /**
     * The same treatment for Thirst Was Taken. Silently does nothing when the mod is absent or
     * its API can't be resolved, which is exactly the degradation we want - no hard dependency.
     */
    private static void dampenThirst(ServerPlayer player, UUID id, int level, float keep) {
        OptionalDouble reading = ThirstWasTakenCompat.getExhaustion(player);
        if (reading.isEmpty()) {
            LAST_THIRST_EXHAUSTION.remove(id);
            return;
        }

        float now = (float) reading.getAsDouble();
        Float previous = LAST_THIRST_EXHAUSTION.get(id);

        if (level > 0 && previous != null && now > previous
                && ThirstWasTakenCompat.setExhaustion(player, previous + (now - previous) * keep)) {
            now = (float) ThirstWasTakenCompat.getExhaustion(player).orElse(now);
        }
        LAST_THIRST_EXHAUSTION.put(id, now);
    }
}
