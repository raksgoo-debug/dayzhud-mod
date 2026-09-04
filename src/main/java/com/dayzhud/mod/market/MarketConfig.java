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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NBT_VARIANT_ITEMS;
    public static final ForgeConfigSpec.BooleanValue SHOW_BALANCE_ON_HUD;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TERMINAL_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TERMINAL_ITEMS;
    public static final ForgeConfigSpec.BooleanValue SAFE_ZONE_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue SAFE_ZONE_FEEDBACK;
    public static final ForgeConfigSpec.BooleanValue BLOCK_REQUIRES_SAFE_ZONE;
    public static final ForgeConfigSpec.BooleanValue ITEM_REQUIRES_SAFE_ZONE;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> AMMO_BOXES;

    public static final ForgeConfigSpec.BooleanValue MAGAZINES_STOCK;
    public static final ForgeConfigSpec.IntValue MAGAZINE_BASE_PRICE;
    public static final ForgeConfigSpec.IntValue MAGAZINE_PER_ROUND;
    public static final ForgeConfigSpec.BooleanValue RESPECT_RUMMAGE;

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
    public static final ForgeConfigSpec.BooleanValue STOCK_ARMOR;
    public static final ForgeConfigSpec.IntValue MAX_DERIVED_LISTINGS;

    public static final ForgeConfigSpec.BooleanValue TACZ_STOCK_ATTACHMENTS;
    public static final ForgeConfigSpec.IntValue TACZ_ATTACHMENT_PRICE;


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
        NBT_VARIANT_ITEMS = b.comment(
                        "Items that are really many items sharing one registry name, told apart",
                        "by a single NBT string, as 'itemid=NbtTag'. LesRaisins Tactical works",
                        "this way: every medkit is lrtactical:consumable with a different",
                        "ConsumableId. Without an entry here they all share one price and any",
                        "price row naming the variant matches nothing, because no such item is",
                        "registered. TACZ is handled separately and does not belong in this list.")
                .defineList("nbtVariantItems", List.of(
                        "lrtactical:consumable=ConsumableId",
                        "lrtactical:throwable=ThrowableId",
                        "lrtactical:melee=MeleeWeaponId"
                ), o -> o instanceof String);
        SHOW_BALANCE_ON_HUD = b.comment(
                        "Draw the rouble balance on the HUD above the status row.",
                        "Off by default: the terminal already shows it, and a number parked over",
                        "the gauges is one more thing competing for the corner of the screen that",
                        "health, water and food already own.")
                .define("showBalanceOnHud", false);
        b.pop();

        b.push("access");
        TERMINAL_BLOCKS = b.comment(
                        "Blocks that open the market when right-clicked. Defaults to the desktop",
                        "PC from tarkovdayz - no new block is registered, so this works with",
                        "whatever decoration mods a pack already has.")
                .defineList("terminalBlocks", List.of(
                        "tarkovdayz:pc"
                ), o -> o instanceof String);
        TERMINAL_ITEMS = b.comment(
                        "Items that open the market when right-clicked while held.",
                        "The laptop is portable, so by default it only works inside a safe zone.")
                .defineList("terminalItems", List.of(
                        "tarkovdayz:laptop"
                ), o -> o instanceof String);
        SAFE_ZONE_PROTECTION = b.comment(
                        "Players take no damage inside a registered safe zone.",
                        "A hideout you can be shot in is not a hideout, and the market is the one",
                        "place a player stands still with their inventory open.")
                .define("safeZoneProtection", true);
        SAFE_ZONE_FEEDBACK = b.comment(
                        "Tell a player when they enter or leave a safe zone.")
                .define("safeZoneMessages", true);
        RESPECT_RUMMAGE = b.comment(
                        "With Rummage installed, leave a container's own screen alone until the",
                        "player has searched it, instead of merging it into the inventory view.",
                        "OFF by default: searching works inside the merged view, because a plain",
                        "Slot on a rummageable container resolves a Rummage target whatever menu",
                        "it sits in. This is only a fallback if some container misbehaves.")
                .define("respectRummage", false);
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

        b.push("ammoBoxes");
        b.comment(
                "Ammo boxes that can be opened for TACZ rounds, as",
                "'itemid=<tacz ammo id>,<count>'. Boxes whose ammo id does not resolve are",
                "dropped from the shop AND cannot be opened, so a box is never something you",
                "can buy and then find does nothing - which is what these were before.",
                "Change the ammo ids to match whatever gun pack you run.");
        AMMO_BOXES = b.defineList("boxes", List.of(
                "tarkovdayz:ammobox_919=tacz:9mm,30",
                "tarkovdayz:amobox_12ga=tacz:12g,20",
                "tarkovdayz:ammobox_545=tacz:545x39,30",
                "tarkovdayz:ammobox_556=tacz:556x45,30",
                "tarkovdayz:ammobox_762=tacz:762x39,30",
                "tarkovdayz:ammobox_308=tacz:308,20"
        ), o -> o instanceof String);
        b.pop();

        b.push("magazines");
        b.comment("Empty magazines from TaCZ: Magazines. Priced by capacity - a magazine is",
                "a container, so what it holds is what it is worth.");
        MAGAZINES_STOCK = b.define("stock", true);
        MAGAZINE_BASE_PRICE = b.defineInRange("basePrice", 1200, 0, 1000000);
        MAGAZINE_PER_ROUND = b.defineInRange("pricePerRound", 90, 0, 100000);
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
        TACZ_STOCK_ATTACHMENTS = b.comment("List every installed attachment in its own tab.")
                .define("stockAttachments", true);
        TACZ_ATTACHMENT_PRICE = b.comment(
                        "Base price of an attachment. Scoped by type and extended-mag level,",
                        "since TACZ's attachment data carries no ballistics to derive from.")
                .defineInRange("attachmentBasePrice", 9000, 1, 10000000);
        b.pop();

        b.push("lrtactical");
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
        STOCK_ARMOR = b.comment(
                        "Also STOCK derived armour, not just buy it back. One gear mod in this",
                        "pack ships 238 pieces; listing them from their own stats is the only way",
                        "the trader carries armour at all without a price row each. Off means a",
                        "gear mod's armour can be sold to a trader but never bought from one.")
                .define("stockArmor", true);
        MAX_DERIVED_LISTINGS = b.comment(
                        "Ceiling on automatically stocked items, as a safety net against a pack",
                        "with an enormous item registry.")
                .defineInRange("maxListings", 600, 0, 5000);
        b.pop();

        SPEC = b.build();
    }

    private MarketConfig() {}
}
