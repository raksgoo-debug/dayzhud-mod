package com.dayzhud.mod.client;

import com.dayzhud.mod.recap.ClientDeathRecap;
import com.dayzhud.mod.recap.RecapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Swaps vanilla's death screen for DayzDeathScreen (2.19.0) as it opens, and forgets the
 * last death's recap on respawn.
 *
 * Only vanilla's exact class is swapped - another mod's own death screen (a subclass) is left
 * alone, and so is ours, so this can't loop. Vanilla reopens a plain DeathScreen whenever the
 * screen is closed while you're dead (after chat, say), and that gets swapped too.
 */
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public final class DeathScreenEvents {

    private DeathScreenEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() == null || event.getNewScreen().getClass() != DeathScreen.class) return;
        if (!RecapConfig.ENABLED.get()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean hardcore = mc.level != null && mc.level.getLevelData().isHardcore();
        event.setNewScreen(new DayzDeathScreen(hardcore));
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        ClientDeathRecap.clear();
    }
}
