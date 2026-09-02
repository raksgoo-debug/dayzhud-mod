package com.dayzhud.mod.market;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side copy of the catalogue the open trader screen is showing.
 *
 * The revision travels with it and goes back out on every buy, so a datapack reload while a
 * screen is open cannot turn a click on one row into a purchase of another.
 */
public final class ClientMarketState {

    private static List<MarketOffer> offers = new ArrayList<>();
    private static int revision;

    private ClientMarketState() {}

    public static void accept(int revision, List<MarketOffer> offers) {
        ClientMarketState.revision = revision;
        ClientMarketState.offers = List.copyOf(offers);
    }

    public static List<MarketOffer> offers() {
        return offers;
    }

    public static int revision() {
        return revision;
    }

    public static void clear() {
        offers = new ArrayList<>();
    }
}
