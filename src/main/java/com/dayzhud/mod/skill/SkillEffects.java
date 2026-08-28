package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
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
import java.util.UUID;

/**
 * Applies the server-side half of the skills, and keeps the client's copy in sync.
 *
 * SPLIT OF RESPONSIBILITY: everything with real consequences lives here, on the server,
 * driven by the authoritative capability - max health, damage taken, hunger. The client is
 * told the numbers ({@link SkillStatePacket}) only so it can draw the screen and run the
 * stamina model. Temperature's own effects are in {@link TemperatureSystem}.
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

    /** Per-player food bookkeeping for Metabolism. Keyed by player UUID; cleared on logout. */
    private static final Map<UUID, Integer> LAST_FOOD_LEVEL = new HashMap<>();
    private static final Map<UUID, Float> SAVED_FOOD_FRACTION = new HashMap<>();

    private SkillEffects() {}

    // ---------------------------------------------------------------- attributes / sync

    /**
     * Re-derives every attribute-backed skill from the capability. Safe to call repeatedly -
     * each modifier is removed before being re-added, so this converges rather than stacking.
     *
     * The modifier is TRANSIENT (not saved to the player's NBT) on purpose: the capability is
     * the single source of truth, and a saved modifier would be re-added on top of a freshly
     * computed one every load, doubling the bonus a bit more each time.
     */
    public static void reapply(Player player) {
        int vitality = SkillCapability.levelOf(player, Skill.VITALITY);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        maxHealth.removeModifier(VITALITY_MODIFIER);
        float bonus = Skill.VITALITY.magnitudeAt(vitality);
        if (bonus > 0f) {
            maxHealth.addTransientModifier(new AttributeModifier(
                    VITALITY_MODIFIER, "dayzhud.vitality", bonus,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** Pushes skills + current temperature to one player's client. */
    public static void sync(ServerPlayer player) {
        PlayerSkills skills = SkillCapability.of(player);
        if (skills == null) return;
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SkillStatePacket(skills, TemperatureSystem.temperatureOf(player)));
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

    /** Respawn builds a new entity, so transient modifiers must be re-added. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
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
        LAST_FOOD_LEVEL.remove(id);
        SAVED_FOOD_FRACTION.remove(id);
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
     * Slows hunger by refunding a fraction of every point of food you lose.
     *
     * WHY IT'S DONE THIS WAY: vanilla drains food through exhaustion accumulated in dozens of
     * places, with no event to intercept, and no subtract-exhaustion API. Watching the food
     * LEVEL and handing part of it back is the one lever that works without touching vanilla
     * internals. The fractional remainder is banked in SAVED_FOOD_FRACTION so a 30% reduction
     * really does return roughly three points in ten rather than rounding away to nothing.
     *
     * Thirst rides along for free wherever a thirst mod drains on the same activity, but it
     * is NOT directly reduced - see the note in the README about needing Thirst Was Taken's
     * write API to do that properly.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        FoodData food = player.getFoodData();
        int now = food.getFoodLevel();
        Integer previous = LAST_FOOD_LEVEL.put(id, now);

        int level = SkillCapability.levelOf(player, Skill.METABOLISM);
        if (level <= 0 || previous == null || now >= previous) return;

        int lost = previous - now;
        float banked = SAVED_FOOD_FRACTION.getOrDefault(id, 0f)
                + lost * Skill.METABOLISM.fractionAt(level);

        int refund = (int) banked;
        if (refund > 0) {
            // Never push above where the player was a tick ago - this slows the drain, it
            // must not become a food source.
            food.setFoodLevel(Math.min(previous, now + refund));
            LAST_FOOD_LEVEL.put(id, food.getFoodLevel());
            banked -= refund;
        }
        SAVED_FOOD_FRACTION.put(id, banked);
    }
}
