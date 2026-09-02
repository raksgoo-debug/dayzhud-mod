package com.dayzhud.mod.client;

import com.dayzhud.mod.inventory.TarkovMenuTypes;
import com.dayzhud.mod.market.MarketScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
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

    /**
     * Screen binding for this mod's OWN menu types.
     *
     * Not the same thing as the container restyling elsewhere in the mod, which has to go
     * through ScreenEvent.Opening because MenuScreens.register throws when a menu type
     * already has a screen. MARKET is registered here and nowhere else, so a plain register
     * is correct - and it must be inside enqueueWork, since MenuScreens is not thread-safe
     * during parallel mod loading.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(TarkovMenuTypes.MARKET.get(), MarketScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("dayz_hotbar", new DayzHotbarOverlay());
        event.registerAboveAll("dayz_status_stack", new DayzHudOverlay());
    }
}
