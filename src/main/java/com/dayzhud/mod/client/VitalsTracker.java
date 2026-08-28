package com.dayzhud.mod.client;

import com.dayzhud.mod.skill.ClientSkillState;
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
 * The HUD's view of stamina and temperature.
 *
 * This class used to SIMULATE both. It no longer simulates anything: stamina lives in
 * StaminaSystem and temperature in TemperatureSystem, both server-side, both pushed down via
 * SkillStatePacket. What's left here is presentation.
 *
 * The one job it still does is smoothing. Syncs land every few ticks, not every tick, so
 * feeding the raw value straight to the bar makes it advance in visible steps; this eases the
 * drawn value toward the last synced one so the bar moves continuously. That's cosmetic only -
 * the smoothed number is never sent anywhere or used for a decision.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public class VitalsTracker {

    /** How much of the gap to the synced value to close each tick. */
    private static final float DISPLAY_SMOOTHING = 0.35f;

    private static float displayedStamina01 = 1f;

    /** Fallback until the first sync arrives - see updateLocalTemperatureFallback. */
    private static float localTemperature01 = 0.5f;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            displayedStamina01 = 1f;
            return;
        }

        float target = ClientSkillState.isSynced() ? ClientSkillState.stamina01() : 1f;
        displayedStamina01 += (target - displayedStamina01) * DISPLAY_SMOOTHING;

        updateLocalTemperatureFallback(player);
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
        // Otherwise the last world's readings linger into the next one you join.
        ClientSkillState.reset();
        displayedStamina01 = 1f;
        localTemperature01 = 0.5f;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Smoothed stamina for the bar. Authoritative value lives on the server. */
    public static float getStamina01() {
        return clamp01(displayedStamina01);
    }

    /** 0 = freezing, 1 = scorching. The server's value once it has arrived. */
    public static float getTemperature01() {
        return ClientSkillState.isSynced() ? ClientSkillState.temperature01() : localTemperature01;
    }
}
