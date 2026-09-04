package com.dayzhud.mod.search;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.TarkovInventoryMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the search while a merged screen is open: one slot revealed at a time, with a sound,
 * until the container is done.
 *
 * Server-side only. The client is told which menu slots are still unsearched purely so it can
 * draw them differently - it has no items to reveal, because it was never sent any.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class SearchHandler {

    private static final Map<UUID, Integer> TIMERS = new ConcurrentHashMap<>();

    private SearchHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !SearchConfig.ENABLED.get()) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof TarkovInventoryMenu menu)) {
                TIMERS.remove(player.getUUID());
                continue;
            }
            Container searched = menu.searchedContainer();
            if (searched == null) {
                TIMERS.remove(player.getUUID());
                continue;
            }

            int timer = TIMERS.getOrDefault(player.getUUID(),
                    SearchConfig.INITIAL_DELAY_TICKS.get() + SearchConfig.TICKS_PER_SLOT.get());
            if (--timer > 0) {
                TIMERS.put(player.getUUID(), timer);
                continue;
            }
            TIMERS.put(player.getUUID(), SearchConfig.TICKS_PER_SLOT.get());

            int slot = SearchProgress.nextUnsearched(searched, player);
            if (slot < 0) continue;

            SearchProgress.reveal(searched, player, slot);
            // The menu reads through SearchedContainer, so revealing a slot is all it takes -
            // the next broadcast sends the item for the first time.
            menu.broadcastChanges();
            SearchNetwork.sendMask(player, menu);

            if (SearchConfig.SOUNDS.get()) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                        SearchConfig.SOUND_VOLUME.get().floatValue(),
                        0.8f + player.level().random.nextFloat() * 0.4f);
            }
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerEvent.PlayerLoggedOutEvent event) {
        TIMERS.remove(event.getEntity().getUUID());
    }

    /** Resets the timer so a freshly opened container waits the initial delay again. */
    public static void beginSearch(ServerPlayer player) {
        TIMERS.put(player.getUUID(),
                SearchConfig.INITIAL_DELAY_TICKS.get() + SearchConfig.TICKS_PER_SLOT.get());
    }
}
