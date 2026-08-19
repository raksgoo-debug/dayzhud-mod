package com.dayzhud.mod.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Call ClientEvents::register from the mod constructor (client side only). Registers the
 * custom overlay; OverlayCanceller (auto-subscribed) takes care of hiding vanilla bars.
 */
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents {

    public static void register() {
        // No-op: presence of this call just ensures the class (and therefore the
        // @SubscribeEvent-annotated onRegisterOverlays below) is loaded on the client.
        // Forge discovers @Mod.EventBusSubscriber classes automatically, but calling this
        // from the constructor keeps class-loading order explicit and easy to follow.
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("dayz_hotbar", new DayzHotbarOverlay());
        event.registerAboveAll("dayz_status_stack", new DayzHudOverlay());
    }
}
