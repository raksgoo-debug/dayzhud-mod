package com.dayzhud.mod.market;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Common config for the market. Everything that decides prices or access lives here so a
 * pack can retune the economy without touching the price data.
 *
 * Forge only writes defaults into a config file that does NOT already exist, so an existing
 * dayzhud-common.toml keeps whatever it already says - delete it or hand-edit to pick up
 * new defaults.
 */
public final class MarketConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_DEPOSIT;
    public static final ForgeConfigSpec.DoubleValue SELL_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BUY_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CURRENCY_ITEMS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TERMINAL_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TERMINAL_ITEMS;
    public static final ForgeConfigSpec.BooleanValue BLOCK_REQUIRES_SAFE_ZONE;
    public static final ForgeConfigSpec.BooleanValue ITEM_REQUIRES_SAFE_ZONE;

    public static final ForgeConfigSpec.BooleanValue TACZ_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TACZ_SELL_GUNS;
    public static final ForgeConfigSpec.BooleanValue TACZ_STOCK_GUNS;
    public static final ForgeConfigSpec.BooleanValue TACZ_STOCK_AMMO;
    public static final ForgeConfigSpec.DoubleValue TACZ_PRICE_SCALE;
    public static final ForgeConfigSpec.IntValue TACZ_AMMO_PRICE;
    public static final ForgeConfigSpec.IntValue TACZ_AMMO_BATCH;

    public static final ForgeConfigSpec.BooleanValue DERIVE_ARMOR;
    public static final ForgeConfigSpec.BooleanValue DERIVE_FOOD;
    public static final ForgeConfigSpec.DoubleValue DERIVE_SCALE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("market");
        ENABLED = b.comment("Master switch for the trader market and the rouble wallet.")
                .define("enabled", true);
        AUTO_DEPOSIT = b.comment(
                        "Absorb rouble notes into the wallet the moment they are picked up,",
                        "so cash never takes up inventory space. Turn this off to make notes",
                        "ordinary items that must be deposited at a terminal.")
                .define("autoDeposit", true);
        SELL_MULTIPLIER = b.comment(
                        "Fraction of an item's listed value a trader pays for it. Traders buying",
                        "below list is what gives the barter loop its margin - at 1.0 there is no",
                        "cost to buying something back after selling it.")
                .defineInRange("sellMultiplier", 0.55D, 0.0D, 1.0D);
        BUY_MULTIPLIER = b.comment("Multiplier applied to the listed price when buying.")
                .defineInRange("buyMultiplier", 1.0D, 0.1D, 20.0D);
        CURRENCY_ITEMS = b.comment(
                        "Items that count as physical cash, as 'itemid=value'. Order does not",
                        "matter; withdrawals always pay out in the largest note that fits.")
                .defineList("currencyItems", List.of(
                        "tarkovdayz:rubble_5000=5000",
                        "tarkovdayz:rubble_1000=1000",
                        "tarkovdayz:rubble_100=100"
                ), o -> o instanceof String);
        b.pop();

        b.push("access");
        TERMINAL_BLOCKS = b.comment(
                        "Blocks that open the market when right-clicked. Defaults to the desktop",
                        "PC from tarkovdayz - no new block is registered, so this works with",
                        "whatever decoration mods a pack already has.")
                .defineList("terminalBlocks", List.of(
                        "tarkovdayz:pc",
                        "tarkovdayz:safe",
                        "tarkovdayz:safe_2"
                ), o -> o instanceof String);
        TERMINAL_ITEMS = b.comment(
                        "Items that open the market when right-clicked while held.",
                        "The laptop is portable, so by default it only works inside a safe zone.")
                .defineList("terminalItems", List.of(
                        "tarkovdayz:laptop"
                ), o -> o instanceof String);
        BLOCK_REQUIRES_SAFE_ZONE = b.comment(
                        "Require a terminal BLOCK to also stand inside a registered safe zone.",
                        "Off by default: a terminal you had to place and build around already is",
                        "your hideout, so demanding a zone as well just blocks it out of the box.")
                .define("blockRequiresSafeZone", false);
        ITEM_REQUIRES_SAFE_ZONE = b.comment(
                        "Require a terminal ITEM to be used inside a registered safe zone.",
                        "On by default - otherwise a laptop in your pack is a trader you can carry",
                        "into a raid, which removes the reason to extract with your loot.")
                .define("itemRequiresSafeZone", true);
        b.pop();

        b.push("tacz");
        TACZ_ENABLED = b.comment(
                        "Price TACZ guns, ammo and attachments. Everything here is reflective -",
                        "there is no compile-time dependency on TACZ, so the mod loads fine",
                        "without it and does not break when TACZ changes version.")
                .define("enabled", true);
        TACZ_SELL_GUNS = b.comment("Let players sell TACZ guns and ammo to traders.")
                .define("sellGuns", true);
        TACZ_STOCK_GUNS = b.comment("List every installed gun in the market's WEAPONS tab.")
                .define("stockGuns", true);
        TACZ_STOCK_AMMO = b.comment("List every installed ammo type in the market's AMMO tab.")
                .define("stockAmmo", true);
        TACZ_PRICE_SCALE = b.comment(
                        "Scales every automatically-derived gun price. Raise it to make weapons",
                        "the long-term goal rather than an early purchase.")
                .defineInRange("priceScale", 1.0D, 0.05D, 50.0D);
        TACZ_AMMO_PRICE = b.comment(
                        "Price of one round, for ammo types with no explicit entry in the price",
                        "data. TACZ's ammo index carries no ballistics - damage lives on the gun's",
                        "bullet data - so there is nothing to derive a per-type price from.")
                .defineInRange("ammoUnitPrice", 45, 1, 100000);
        TACZ_AMMO_BATCH = b.comment("How many rounds one ammo listing sells at a time.")
                .defineInRange("ammoBatchSize", 30, 1, 64);
        b.pop();

        b.push("derived");
        b.comment(
                "Prices for items with no entry in the price data, worked out from the stats",
                "the item already declares. This is what makes the trader work with a gear mod",
                "that ships hundreds of armour pieces - hand-authoring a row each does not",
                "survive contact with a real modpack. An explicit entry always wins.");
        DERIVE_ARMOR = b.comment("Price unlisted armour from defence, toughness and durability.")
                .define("armor", true);
        DERIVE_FOOD = b.comment("Price unlisted food from nutrition and saturation.")
                .define("food", true);
        DERIVE_SCALE = b.comment("Multiplier on every derived price.")
                .defineInRange("scale", 1.0D, 0.05D, 50.0D);
        b.pop();

        SPEC = b.build();
    }

    private MarketConfig() {}
}
