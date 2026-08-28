package com.dayzhud.mod.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cancels the vanilla bars/icons this HUD replaces (hunger, hearts, armor, air, XP), plus
 * Thirst Was Taken's own thirst bar overlay (id "thirst:thirst_level", confirmed from that
 * mod's jar) so there's no duplicate thirst display once our HUD is showing it instead.
 *
 * The vanilla HOTBAR is cancelled too - DayzHotbarOverlay draws a restyled one that
 * matches the inventory screen. Note this also removes vanilla's attack-strength
 * indicator, which rides along with that same overlay.
 */
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public class OverlayCanceller {

    private static final ResourceLocation THIRST_WAS_TAKEN_OVERLAY_ID =
            new ResourceLocation("thirst", "thirst_level");

    private static final ResourceLocation VANILLA_XP_LEVEL_ID =
            new ResourceLocation("minecraft", "experience_level");

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.AIR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                // The XP bar and its level number are redrawn by DayzHudOverlay in the same
                // style as the stamina bar, so vanilla's green bar has to go with the rest.
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                // Matched by ID rather than enum constant: depending on the Forge build the
                // level number is either part of the bar overlay above or its own overlay.
                // An ID that doesn't exist simply never matches, so this is free either way.
                || VANILLA_XP_LEVEL_ID.equals(event.getOverlay().id())
                || THIRST_WAS_TAKEN_OVERLAY_ID.equals(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
