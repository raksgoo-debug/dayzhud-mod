package com.dayzhud.mod.weight;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The local player's carried weight, as last sent by the server (WeightSyncPacket), plus
 * the client half of the "too heavy to sprint" rule: the server cuts sprinting, but holding
 * the key (or toggle-sprint) would start it again every tick, so here the key is released
 * and sprinting cancelled while heavy.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWeight {

    private static float kg, overweight = Float.MAX_VALUE, heavy = Float.MAX_VALUE, critical = Float.MAX_VALUE;
    private static boolean known;

    private ClientWeight() {}

    static void accept(float[] v) {
        kg = v[0];
        overweight = v[1];
        heavy = v[2];
        critical = v[3];
        known = true;
    }

    public static boolean known() {
        return known;
    }

    public static float kg() {
        return kg;
    }

    public static float overweight() {
        return overweight;
    }

    public static float heavy() {
        return heavy;
    }

    public static float critical() {
        return critical;
    }

    /** 0 = fine, 1 = overweight, 2 = heavy (no sprint), 3 = critical. */
    public static int level() {
        if (!known || kg <= overweight) return 0;
        if (kg <= heavy) return 1;
        if (kg <= critical) return 2;
        return 3;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!known || level() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (event.phase == TickEvent.Phase.START) {
            mc.options.keySprint.setDown(false);
        } else if (player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}
