package com.dayzhud.mod.search;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has searched which slots of which container.
 *
 * Keyed on the container instance in a WeakHashMap rather than on a position or an entity id.
 * A corpse, a chest and a mod's virtual inventory have nothing in common except that the menu
 * was handed one Container, and this is the only identity all three share. The weak key means
 * a corpse that despawns takes its progress with it instead of leaking.
 *
 * Progress does not survive a restart. That is a deliberate limit rather than an oversight:
 * persisting it needs a stable id per container, which does not exist for the entity-backed
 * ones, and a corpse rarely outlives a session anyway.
 */
public final class SearchProgress {

    private static final Map<Container, Map<UUID, BitSet>> STATE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private SearchProgress() {}

    public static BitSet forPlayer(Container container, Player player) {
        return STATE.computeIfAbsent(container, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(player.getUUID(), u -> new BitSet());
    }

    public static boolean isRevealed(Container container, Player player, int slot) {
        return forPlayer(container, player).get(slot);
    }

    public static void reveal(Container container, Player player, int slot) {
        forPlayer(container, player).set(slot);
    }

    public static void forget(Container container, Player player) {
        Map<UUID, BitSet> byPlayer = STATE.get(container);
        if (byPlayer != null) byPlayer.remove(player.getUUID());
    }

    /**
     * The next slot that still needs searching, or -1 when the container is done.
     *
     * Empty slots are marked revealed as they are passed rather than being skipped silently,
     * so an almost-empty container finishes quickly instead of appearing to hang on nothing.
     */
    public static int nextUnsearched(Container container, Player player) {
        BitSet revealed = forPlayer(container, player);
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (revealed.get(i)) continue;
            if (container.getItem(i).isEmpty()) {
                revealed.set(i);
                continue;
            }
            return i;
        }
        return -1;
    }

    /** Menu-slot indices to draw as unsearched, given where this container starts in the menu. */
    public static int[] maskedSlots(Container container, Player player, int menuOffset, int count) {
        BitSet revealed = forPlayer(container, player);
        int[] tmp = new int[count];
        int n = 0;
        for (int i = 0; i < count && i < container.getContainerSize(); i++) {
            if (!revealed.get(i)) tmp[n++] = menuOffset + i;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }
}
