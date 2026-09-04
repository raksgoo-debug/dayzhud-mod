package com.dayzhud.mod;

import com.dayzhud.mod.client.ClientEvents;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.TarkovMenuTypes;
import com.dayzhud.mod.market.MarketConfig;
import com.dayzhud.mod.search.SearchConfig;
import com.dayzhud.mod.search.SearchConfig;
import com.dayzhud.mod.registry.ModCreativeTabs;
import com.dayzhud.mod.registry.ModItems;
import com.dayzhud.mod.sound.ModSounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
        ModSounds.SOUNDS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // Market economy settings. COMMON rather than SERVER so single-player and a
        // dedicated server read the same file, and so the client can price sell quotes
        // locally instead of asking the server for every tooltip.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MarketConfig.SPEC,
                "dayzhud-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SearchConfig.SPEC,
                "dayzhud-search.toml");
        modEventBus.addListener(this::commonSetup);

        // HUD rendering, vanilla overlay suppression, and the stamina/temperature
        // trackers are all purely visual for the local player, so they live client-only.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEvents::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkHandler.register();
    }
}
