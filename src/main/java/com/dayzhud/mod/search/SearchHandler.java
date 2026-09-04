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

    private static final java.util.Map<java.util.UUID, int[]> LAST_MASK =
            new java.util.concurrent.ConcurrentHashMap<>();

    private SearchHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !SearchConfig.ENABLED.get()) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof TarkovInventoryMenu menu)) {
                TIMERS.remove(player.getUUID());
                LAST_MASK.remove(player.getUUID());
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

            // Body first, then the corpse's worn bag - bag slots live in the same BitSet at
            // an offset past the container's size, so "next" walks straight from one into the
            // other and the ordering needs no special case.
            int bagSlots = menu.corpseBagSlotCount();
            int slot = SearchProgress.nextUnsearchedWithBag(searched, player,
                    menu::corpseBagSlotOccupied, bagSlots);
            if (slot < 0) continue;

            SearchProgress.forPlayer(searched, player).set(slot);
            // The menu reads through SearchedContainer, so revealing a slot is all it takes -
            // the next broadcast sends the item for the first time.
            menu.broadcastChanges();

            if (SearchConfig.SOUNDS.get()) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                        SearchConfig.SOUND_VOLUME.get().floatValue(),
                        0.8f + player.level().random.nextFloat() * 0.4f);
            }
            pushMaskIfChanged(player, menu);
        }
    }

    /**
     * Resends the mask only when it differs from the last one this player was sent.
     *
     * Needed beyond the reveal path because scrolling the corpse loot list remaps which
     * visible slot is which bag slot - the set of hidden MENU indices changes without anything
     * being revealed. Comparing first keeps that to one packet per actual change rather than
     * one per tick.
     */
    private static void pushMaskIfChanged(ServerPlayer player, TarkovInventoryMenu menu) {
        int[] mask = SearchNetwork.maskFor(player, menu);
        int[] last = LAST_MASK.get(player.getUUID());
        if (java.util.Arrays.equals(mask, last)) return;
        LAST_MASK.put(player.getUUID(), mask);
        SearchNetwork.sendMask(player, mask);
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
