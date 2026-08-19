package com.dayzhud.mod;

import com.dayzhud.mod.client.ClientEvents;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.TarkovMenuTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DayzHudMod.MOD_ID)
public class DayzHudMod {

    public static final String MOD_ID = "dayzhud";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public DayzHudMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        TarkovMenuTypes.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);

        // HUD rendering, vanilla overlay suppression, and the stamina/temperature
        // trackers are all purely visual for the local player, so they live client-only.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEvents::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkHandler.register();
    }
}
