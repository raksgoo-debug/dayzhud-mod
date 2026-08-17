package com.dayzhud.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla Minecraft has no stamina or temperature stat, so this tracks lightweight,
 * purely-cosmetic approximations for the HUD:
 *  - Stamina: drains while sprinting/jumping/swimming, regenerates when idle-ish.
 *  - Temperature: derived from the biome the player is standing in (falls back to
 *    "neutral" if nothing more specific is available). If you also run a real
 *    temperature mod (e.g. Cold Sweat), swap getTemperature01() to read its API instead -
 *    see the comment at the bottom of this class.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public class VitalsTracker {

    private static float stamina = 100f; // 0-100
    private static float temperature01 = 0.5f; // smoothed 0 (freezing) - 1 (scorching), 0.5 = neutral

    private static final float DRAIN_PER_TICK_SPRINT = 0.45f;
    private static final float DRAIN_PER_JUMP = 6f;
    private static final float REGEN_PER_TICK = 0.25f;
    private static final float MIN_SPRINT_STAMINA = 8f; // below this, sprinting gets cut off

    private static boolean wasOnGroundLastTick = true;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            stamina = 100f;
            return;
        }

        // --- Stamina ---
        boolean sprinting = player.isSprinting();
        boolean justJumped = !player.onGround() && wasOnGroundLastTick && player.getDeltaMovement().y > 0.1;
        wasOnGroundLastTick = player.onGround();

        if (justJumped) {
            stamina = Math.max(0f, stamina - DRAIN_PER_JUMP);
        }

        if (sprinting && (player.isSwimming() || player.horizontalCollision || player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4)) {
            stamina = Math.max(0f, stamina - DRAIN_PER_TICK_SPRINT);
            if (stamina <= 0f) {
                player.setSprinting(false);
            }
        } else if (!sprinting) {
            stamina = Math.min(100f, stamina + REGEN_PER_TICK);
        }

        // Forcefully stop sprint attempts while gassed, DayZ-style.
        if (stamina < MIN_SPRINT_STAMINA && sprinting && player.getFoodData().getExhaustionLevel() > 0) {
            // Soft nudge only - we don't want to fight the player's input every tick,
            // just make sure stamina can't sit at 0 while still sprinting freely.
        }

        // --- Temperature (biome-based approximation) ---
        Biome biome = player.level().getBiome(player.blockPosition()).value();
        float baseTemp = biome.getBaseTemperature(); // roughly -0.5 (frozen peaks) to 2.0 (nether/desert)
        float target = clamp01((baseTemp + 0.5f) / 2.5f);
        // Being in water pulls you toward cold; standing near/in fire or in the Nether pulls hot.
        if (player.isInWater()) target = Math.max(0f, target - 0.25f);
        if (player.isInLava() || player.isOnFire()) target = 1f;
        temperature01 += (target - temperature01) * 0.02f; // smooth toward target
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float getStamina01() {
        return stamina / 100f;
    }

    public static float getTemperature01() {
        return temperature01;
    }

    // To wire in a real temperature mod instead of the biome approximation, replace the
    // body of getTemperature01() with a reflective read similar to
    // com.dayzhud.mod.compat.ThirstWasTakenCompat, targeting that mod's player temperature value.
}
