package com.dayzhud.mod.client;

import com.dayzhud.mod.skill.ClientSkillState;
import com.dayzhud.mod.skill.Skill;
import com.dayzhud.mod.skill.TemperatureSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side stamina model, and the client's view of temperature.
 *
 * STAMINA is still simulated locally: it drains on sprinting and jumping, regenerates when
 * idle, and cuts your sprint off at zero. Endurance deepens the pool and slows the burn.
 * Being local means it's trivially cheatable on a server - acceptable while nothing but the
 * HUD and your own sprinting depends on it, but worth knowing before this ever gates
 * anything that matters.
 *
 * TEMPERATURE is NOT computed here any more. It's owned by TemperatureSystem on the server
 * (because it now does real damage, which has to be authoritative) and arrives via
 * SkillStatePacket. The local biome estimate below survives only as a stand-in for the few
 * ticks between joining a world and the first sync landing, so the gauge never opens on a
 * visibly wrong reading.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public class VitalsTracker {

    private static final float BASE_MAX_STAMINA = 100f;

    private static float stamina = BASE_MAX_STAMINA;
    /** Fallback only - see the class note. Overwritten by the server's value once synced. */
    private static float localTemperature01 = 0.5f;

    private static final float DRAIN_PER_TICK_SPRINT = 0.45f;
    private static final float DRAIN_PER_JUMP = 6f;
    private static final float REGEN_PER_TICK = 0.25f;

    /** Fraction of sprint drain removed per Endurance level (0.04 = 40% cheaper at cap). */
    private static final float ENDURANCE_DRAIN_RELIEF = 0.04f;

    /** Stamina regenerates this much slower while you're below the cold threshold. */
    private static final float COLD_REGEN_PENALTY = 0.5f;

    private static boolean wasOnGroundLastTick = true;

    /** Max stamina including Endurance. Never below the base value. */
    public static float getMaxStamina() {
        return BASE_MAX_STAMINA + Skill.ENDURANCE.magnitudeAt(ClientSkillState.level(Skill.ENDURANCE));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            stamina = getMaxStamina();
            return;
        }

        float maxStamina = getMaxStamina();
        // Buying Endurance mid-session raises the ceiling; without this the bar would sit
        // at its old value and look permanently part-full.
        if (stamina > maxStamina) stamina = maxStamina;

        // --- Stamina ---
        boolean sprinting = player.isSprinting();
        boolean justJumped = !player.onGround() && wasOnGroundLastTick
                && player.getDeltaMovement().y > 0.1;
        wasOnGroundLastTick = player.onGround();

        int endurance = ClientSkillState.level(Skill.ENDURANCE);
        float drainMultiplier = Math.max(0.2f, 1f - endurance * ENDURANCE_DRAIN_RELIEF);

        if (justJumped) {
            stamina = Math.max(0f, stamina - DRAIN_PER_JUMP * drainMultiplier);
        }

        boolean actuallyMoving = player.isSwimming() || player.horizontalCollision
                || player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;

        if (sprinting && actuallyMoving) {
            stamina = Math.max(0f, stamina - DRAIN_PER_TICK_SPRINT * drainMultiplier);
            if (stamina <= 0f) {
                // The one real consequence stamina has: out of breath means you stop running.
                player.setSprinting(false);
            }
        } else if (!sprinting) {
            stamina = Math.min(maxStamina, stamina + REGEN_PER_TICK * regenMultiplier());
        }

        updateLocalTemperatureFallback(player);
    }

    /**
     * Cold makes you recover more slowly - the first thing you notice when your temperature
     * drops, well before it starts doing damage. Acclimation moves the threshold, so a
     * trained player stops feeling it.
     */
    private static float regenMultiplier() {
        float temperature = getTemperature01();
        float coldEdge = TemperatureSystem.coldEdgeFor(ClientSkillState.level(Skill.ACCLIMATION));
        return temperature < coldEdge ? COLD_REGEN_PENALTY : 1f;
    }

    /**
     * Keeps a rough local estimate ticking purely so getTemperature01() has something sane to
     * return before the first server sync. Once synced this value is never read again.
     */
    private static void updateLocalTemperatureFallback(LocalPlayer player) {
        if (ClientSkillState.isSynced()) return;

        Biome biome = player.level().getBiome(player.blockPosition()).value();
        float target = clamp01((biome.getBaseTemperature() + 0.5f) / 2.5f);
        if (player.isInWater()) target = Math.max(0f, target - 0.25f);
        if (player.isInLava() || player.isOnFire()) target = 1f;
        localTemperature01 += (target - localTemperature01) * 0.02f;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Otherwise the last world's skills would linger and mis-size the stamina bar on the
        // next world you join.
        ClientSkillState.reset();
        stamina = BASE_MAX_STAMINA;
        localTemperature01 = 0.5f;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float getStamina01() {
        float max = getMaxStamina();
        return max <= 0f ? 0f : clamp01(stamina / max);
    }

    /** 0 = freezing, 1 = scorching. The server's value once it has arrived. */
    public static float getTemperature01() {
        return ClientSkillState.isSynced() ? ClientSkillState.temperature01() : localTemperature01;
    }
}
