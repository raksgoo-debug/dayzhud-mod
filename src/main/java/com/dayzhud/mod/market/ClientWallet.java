package com.dayzhud.mod.market;

/**
 * Client-side display cache of the player's balance. The server holds the real number;
 * this exists so the HUD and the market screen have something to draw between syncs.
 */
public final class ClientWallet {

    private static long balance;

    private ClientWallet() {}

    public static void accept(long value) {
        balance = value;
    }

    public static long get() {
        return balance;
    }

    public static String formatted() {
        return Money.format(balance);
    }
}
