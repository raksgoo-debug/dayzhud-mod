package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Swaps vanilla's own inventory screen (survival/adventure only - creative keeps its
 * normal layout since this screen doesn't have a creative item-browser area) for our
 * Tarkov-style one, and registers the screen factory.
 *
 * NOTE: swapping screens client-side alone isn't enough for item interactions to work
 * safely - our menu needs a matching server-side instance with the same container id, or
 * clicking slots will desync. That's why this cancels the vanilla screen and sends
 * OpenTarkovInventoryPacket instead of just calling Minecraft.setScreen directly; the
 * server's NetworkHooks.openScreen response is what actually shows our screen.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, value = Dist.CLIENT)
public class TarkovInventoryClientEvents {

    // Registered here (rather than the newer RegisterMenuScreensEvent, which not every
    // 1.20.1 Forge build has) since FMLClientSetupEvent + MenuScreens.register is the
    // longest-standing, most universally-supported way to do this.
    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(TarkovMenuTypes.TARKOV_INVENTORY.get(), TarkovInventoryScreen::new));
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // If this doesn't compile, ScreenEvent.Opening's field/method names have likely
        // shifted slightly between Forge versions - check the actual class for the
        // current screen (probably getScreen() or getCurrentScreen()) and new screen
        // (probably getNewScreen()) accessor names, and adjust below.
        if (!(event.getNewScreen() instanceof InventoryScreen)) return;

        var player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().instabuild || player.isSpectator()) {
            return; // leave creative/spectator using the vanilla screen
        }

        event.setCanceled(true);
        NetworkHandler.CHANNEL.sendToServer(new OpenTarkovInventoryPacket());
    }
}
