package com.dayzhud.mod.inventory;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;

/**
 * Registers the three DayZ-style Curios slot types this mod's inventory screen needs:
 * Face Cover, Headset, and Chest Rig. Each holds a single item. Curios merges slot type
 * registrations by identifier, so if another installed mod already registers a slot with
 * the same name, they share it rather than conflicting.
 *
 * Registration happens via InterModComms, which is the standard way Curios expects slot
 * types to be declared - see https://github.com/TheIllusiveC4/Curios/wiki for reference.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCurios {

    public static final String FACE_COVER = "face_cover";
    public static final String HEADSET = "headset";
    public static final String CHEST_RIG = "chest_rig";

    @SubscribeEvent
    public static void enqueueIMC(final InterModEnqueueEvent event) {
        registerSlot(FACE_COVER);
        registerSlot(HEADSET);
        registerSlot(CHEST_RIG);
    }

    private static void registerSlot(String identifier) {
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder(identifier).size(1).build());
    }
}
