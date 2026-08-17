package com.dayzhud.mod;

import com.dayzhud.mod.client.ClientEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DayzHudMod.MOD_ID)
public class DayzHudMod {

    public static final String MOD_ID = "dayzhud";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public DayzHudMod() {
        // Everything this mod does is purely visual for the local player, so it all
        // lives on the client: HUD rendering, vanilla overlay suppression, and the
        // stamina/temperature trackers.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEvents::register);
    }
}
