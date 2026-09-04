package com.dayzhud.mod.search;

import java.util.BitSet;

/**
 * Client-side view of which menu slots are still unsearched, so the screen can draw them as
 * hidden.
 *
 * The server sends MENU indices computed against the menu the client actually has open, which
 * is the whole point: there is no second numbering for the two sides to disagree about.
 */
public final class ClientSearchState {

    private static final BitSet MASKED = new BitSet();

    private ClientSearchState() {}

    public static void accept(int[] slots) {
        MASKED.clear();
        for (int slot : slots) MASKED.set(slot);
    }

    public static void clear() {
        MASKED.clear();
    }

    public static boolean isMasked(int menuSlot) {
        return MASKED.get(menuSlot);
    }

    public static boolean any() {
        return !MASKED.isEmpty();
    }
}
