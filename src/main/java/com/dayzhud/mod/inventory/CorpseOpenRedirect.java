package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns Ragdollified's corpse looting screen into the merged view: your own full loadout
 * panel on the left, the corpse's gear and inventory laid out to the right.
 *
 * LAYOUT OF THE CORPSE CONTAINER (verified by disassembling CorpseMenu's constructor in
 * ragdollified-1.20.1-0.9.0-BETA, NOT guessed - getting this wrong would misplace or lose
 * items):
 *
 *   corpseSlots = 41 + curioCount
 *     0..35   main inventory + hotbar  (0-8 hotbar, 9-35 main, i.e. vanilla Inventory order)
 *     36..39  armor, vanilla order     (36 feet, 37 legs, 38 chest, 39 head)
 *     40      offhand
 *     41..    one slot per entry in the menu's curioIds list
 *
 * If Ragdollified changes that layout in a future version, the size check below stops the
 * redirect rather than silently laying slots out wrongly.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public class CorpseOpenRedirect {

    private static final String RAGDOLL_MODID = "ragdollified";
    private static final String CORPSE_MENU_CLASS = "com.raiiiden.ragdollified.menu.CorpseMenu";

    private static final Set<ServerPlayer> REDIRECTING = new LinkedHashSet<>();

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!ModList.get().isLoaded(RAGDOLL_MODID)) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        AbstractContainerMenu menu = event.getContainer();
        if (menu instanceof TarkovInventoryMenu) return;
        if (REDIRECTING.contains(serverPlayer)) return;
        if (!menu.getClass().getName().equals(CORPSE_MENU_CLASS)) return;

        Container corpse = readField(menu, "corpse", Container.class);
        List<String> curioIds = readCurioIds(menu);
        if (corpse == null) return;

        int expected = 41 + curioIds.size();
        if (corpse.getContainerSize() != expected) {
            DayzHudMod.LOGGER.warn("[dayzhud] Corpse container is {} slots but the known layout "
                    + "expects {} - leaving Ragdollified's own screen in place rather than "
                    + "risking misplaced items.", corpse.getContainerSize(), expected);
            return;
        }

        Component title = menu instanceof MenuProvider provider
                ? provider.getDisplayName()
                : Component.literal("Corpse");

        REDIRECTING.add(serverPlayer);
        try {
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return title;
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                    return new TarkovInventoryMenu(windowId, inv, corpse, curioIds);
                }
            }, buf -> {
                buf.writeVarInt(corpse.getContainerSize());
                buf.writeVarInt(curioIds.size());
            });
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Failed to open merged corpse view; "
                    + "leaving Ragdollified's screen in place.", e);
        } finally {
            REDIRECTING.remove(serverPlayer);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readCurioIds(AbstractContainerMenu menu) {
        List<String> ids = readField(menu, "curioIds", List.class);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    private static <T> T readField(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Couldn't read '{}' from the corpse menu.", name, e);
            return null;
        }
    }
}
