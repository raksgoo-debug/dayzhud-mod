package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds the list of things a trader will sell you.
 *
 * Rebuilt lazily and cached, because it depends on the price table (datapack-reloadable) and
 * on whatever gun and gear mods are installed, neither of which changes during play. Anything
 * that can change it calls {@link #invalidate()}.
 */
public final class MarketCatalog {

    public static final String CAT_FIREARMS = "firearms";
    public static final String CAT_AMMO = "ammo";
    public static final String CAT_ARMOR = "armor";
    public static final String CAT_ATTACHMENTS = "attachments";
    public static final String CAT_MAGAZINES = "magazines";

    /**
     * Display order for category tabs. Shared with the screen so the sidebar puts what a
     * player actually shops for at the top; anything a pack invents falls to the end
     * alphabetically rather than disappearing.
     */
    public static final List<String> CATEGORY_ORDER = List.of(
            CAT_FIREARMS, CAT_AMMO, CAT_MAGAZINES, CAT_ATTACHMENTS, CAT_ARMOR, "gear", "tactical", "meds", "provisions",
            "supplies", "materials", "weapons", "electronics", "valuables", "misc");

    /**
     * Display order for the sections inside a category. Armour reads head-down because that
     * is how a loadout is put together, not alphabetically - "body armor, boots, helmets,
     * legs" is the order a sort gives you and it is the wrong one.
     */
    public static final List<String> SUB_ORDER = List.of(
            "helmets", "body armor", "legs", "boots",
            "pistols", "smgs", "rifles", "marksman", "shotguns", "machine guns", "launchers",
            "optics", "muzzle", "grips", "stocks", "extended",
            "small", "standard", "extended mags", "drums",
            "backpacks", "rigs", "masks", "eyewear", "headwear", "uniforms", "gloves",
            "grenades", "explosives", "shields",
            "kits", "pills", "injectors", "bandages");

    private static List<MarketOffer> cached;

    /**
     * Bumped every time the catalogue changes. Buy packets carry the revision the client was
     * looking at, because offers are addressed by index: a datapack reload between the screen
     * opening and the click would otherwise silently sell the player a different item at the
     * price of the one they clicked.
     */
    private static int revision;

    private MarketCatalog() {}

    public static void invalidate() {
        cached = null;
        revision++;
    }

    public static int revision() {
        return revision;
    }

    public static synchronized List<MarketOffer> offers() {
        if (cached == null) cached = build();
        return cached;
    }

    /** Categories present in the current catalogue, in {@link #CATEGORY_ORDER}. */
    public static List<String> categories() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MarketOffer o : offers()) seen.add(o.category());
        return sortCategories(seen);
    }

    /** Sections present in one category, in {@link #SUB_ORDER}. */
    public static List<String> subcategories(List<MarketOffer> offers, String category) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MarketOffer o : offers) {
            if (o.category().equals(category) && !o.sub().isEmpty()) seen.add(o.sub());
        }
        return sortBy(seen, SUB_ORDER);
    }

    public static List<String> sortCategories(Iterable<String> input) {
        return sortBy(input, CATEGORY_ORDER);
    }

    private static List<String> sortBy(Iterable<String> input, List<String> order) {
        List<String> ordered = new ArrayList<>();
        for (String s : input) ordered.add(s);
        ordered.sort((a, b) -> {
            int ia = order.indexOf(a);
            int ib = order.indexOf(b);
            if (ia < 0 && ib < 0) return a.compareTo(b);
            if (ia < 0) return 1;
            if (ib < 0) return -1;
            return Integer.compare(ia, ib);
        });
        return ordered;
    }

    private static List<MarketOffer> build() {
        List<MarketOffer> out = new ArrayList<>();
        int listed = 0;
        int guns = 0;
        int ammo = 0;
        int armour = 0;

        for (Map.Entry<String, MarketPrices.Entry> e : MarketPrices.all().entrySet()) {
            MarketPrices.Entry entry = e.getValue();
            if (!entry.buy()) continue;
            ItemStack stack = stackFor(e.getKey(), entry.count());
            if (stack.isEmpty()) continue;   // item belongs to a mod that isn't installed
            // An ammo box that cannot resolve its rounds is worse than an absent one: a
            // player buys it, right-clicks, and nothing happens. Either it works or it is
            // not for sale.
            if (AmmoBoxes.of(stack) != null && !AmmoBoxes.isUsable(stack)) continue;
            out.add(new MarketOffer(stack, MarketPrices.buyPrice(entry.value(), entry.count()),
                    entry.category(), entry.sub()));
            listed++;
        }

        if (TaczMarketCompat.isActive()) {
            if (MarketConfig.TACZ_STOCK_GUNS.get()) {
                for (TaczMarketCompat.GunEntry gun : TaczMarketCompat.listGuns()) {
                    ItemStack stack = TaczMarketCompat.makeGun(gun.id());
                    if (stack.isEmpty()) continue;
                    MarketPrices.Entry override = MarketPrices.all().get("tacz:gun/" + gun.id());
                    int unit = override != null ? override.value() : gun.price();
                    out.add(new MarketOffer(stack, MarketPrices.buyPrice(unit, 1), CAT_FIREARMS,
                            gunSection(gun.type())));
                    guns++;
                }
            }
            if (MarketConfig.TACZ_STOCK_AMMO.get()) {
                int batch = MarketConfig.TACZ_AMMO_BATCH.get();
                for (ResourceLocation id : TaczMarketCompat.listAmmo()) {
                    ItemStack stack = TaczMarketCompat.makeAmmo(id, batch);
                    if (stack.isEmpty()) continue;
                    MarketPrices.Entry override = MarketPrices.all().get("tacz:ammo/" + id);
                    Integer derived = TaczMarketCompat.priceOfAmmo(id);
                    int unit = override != null ? override.value()
                            : (derived != null ? derived : MarketConfig.TACZ_AMMO_PRICE.get());
                    out.add(new MarketOffer(stack, MarketPrices.buyPrice(unit, batch), CAT_AMMO));
                    ammo++;
                }
            }
        }

        int attachments = 0;
        if (TaczMarketCompat.isActive() && MarketConfig.TACZ_STOCK_ATTACHMENTS.get()) {
            for (TaczMarketCompat.AttachmentEntry a : TaczMarketCompat.listAttachments()) {
                ItemStack stack = TaczMarketCompat.makeAttachment(a.id());
                if (stack.isEmpty()) continue;
                MarketPrices.Entry override = MarketPrices.all().get("tacz:attachment/" + a.id());
                int unit = override != null ? override.value() : a.price();
                out.add(new MarketOffer(stack, MarketPrices.buyPrice(unit, 1), CAT_ATTACHMENTS,
                        attachmentSection(a.type())));
                attachments++;
            }
        }

        int magazines = 0;
        if (MagazineCompat.isActive() && MarketConfig.MAGAZINES_STOCK.get()) {
            for (String family : MagazineCompat.families()) {
                ItemStack stack = MagazineCompat.makeEmpty(family);
                if (stack.isEmpty()) continue;
                MarketPrices.Entry override =
                        MarketPrices.all().get(MagazineCompat.KEY_PREFIX + family);
                int unit = override != null ? override.value() : MagazineCompat.priceOf(stack);
                if (unit <= 0) continue;
                out.add(new MarketOffer(stack, MarketPrices.buyPrice(unit, 1), CAT_MAGAZINES,
                        magazineSection(MagazineCompat.capacityOf(stack))));
                magazines++;
            }
        }

        armour = addDerivedArmour(out);

        out.sort((a, b) -> {
            int ca = CATEGORY_ORDER.indexOf(a.category());
            int cb = CATEGORY_ORDER.indexOf(b.category());
            if (ca < 0) ca = Integer.MAX_VALUE;
            if (cb < 0) cb = Integer.MAX_VALUE;
            if (ca != cb) return Integer.compare(ca, cb);
            return Long.compare(a.price(), b.price());
        });

        // Logged because "the shop is missing X" is otherwise impossible to tell apart from
        // "the shop has X and the UI is not showing it", and that ambiguity has already cost
        // one round of guessing.
        DayzHudMod.LOGGER.info("Market catalogue rebuilt: {} offers ({} listed, {} guns, "
                        + "{} ammo, {} attachments, {} magazines, {} derived armour) in "
                        + "categories {}",
                out.size(), listed, guns, ammo, attachments, magazines, armour, categoriesOf(out));
        return Collections.unmodifiableList(out);
    }

    private static List<String> categoriesOf(List<MarketOffer> offers) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MarketOffer o : offers) seen.add(o.category());
        return sortCategories(seen);
    }

    /**
     * Stocks every armour piece that has no explicit price row, valued from its own stats.
     *
     * Walking the item registry rather than a list is the point: a gear mod shipping 238
     * pieces cannot be enumerated by hand, and the next one will ship a different number.
     */
    private static int addDerivedArmour(List<MarketOffer> out) {
        if (!MarketConfig.STOCK_ARMOR.get() || !MarketConfig.DERIVE_ARMOR.get()) return 0;
        int limit = MarketConfig.MAX_DERIVED_LISTINGS.get();
        int added = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            if (added >= limit) break;
            if (!(item instanceof ArmorItem)) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) continue;
            // An explicit row wins, and it has already been handled above.
            if (MarketPrices.all().containsKey(id.toString())) continue;
            ItemStack stack = new ItemStack(item);
            int value = DerivedPrices.valueOf(stack);
            if (value <= 0) continue;
            out.add(new MarketOffer(stack, MarketPrices.buyPrice(value, 1), CAT_ARMOR,
                    armourSection((ArmorItem) item)));
            added++;
        }
        return added;
    }

    /** TACZ's gun categories, renamed to shop sections. */
    private static String gunSection(String type) {
        return switch (type == null ? "" : type.toLowerCase(java.util.Locale.ROOT)) {
            case "pistol" -> "pistols";
            case "smg" -> "smgs";
            case "shotgun" -> "shotguns";
            case "sniper" -> "marksman";
            case "mg" -> "machine guns";
            case "rpg" -> "launchers";
            default -> "rifles";
        };
    }

    /** TACZ attachment types, mapped to shop sections. */
    private static String attachmentSection(String type) {
        return switch (type == null ? "" : type.toLowerCase(java.util.Locale.ROOT)) {
            case "scope" -> "optics";
            case "muzzle" -> "muzzle";
            case "grip" -> "grips";
            case "stock" -> "stocks";
            case "extended_mag" -> "extended";
            default -> "";
        };
    }

    /** Magazine sections by capacity, so a drum is not filed next to a pistol mag. */
    private static String magazineSection(int capacity) {
        if (capacity <= 0) return "";
        if (capacity <= 12) return "small";
        if (capacity <= 35) return "standard";
        if (capacity <= 60) return "extended mags";
        return "drums";
    }

    private static String armourSection(ArmorItem item) {
        return switch (item.getEquipmentSlot()) {
            case HEAD -> "helmets";
            case CHEST -> "body armor";
            case LEGS -> "legs";
            case FEET -> "boots";
            default -> "";
        };
    }

    static ItemStack stackFor(String key, int count) {
        if (key.startsWith(MagazineCompat.KEY_PREFIX)) return MagazineCompat.makeFor(key);
        if (key.startsWith("tacz:gun/")) {
            ResourceLocation id = ResourceLocation.tryParse(key.substring("tacz:gun/".length()));
            return id == null ? ItemStack.EMPTY : TaczMarketCompat.makeGun(id);
        }
        if (key.startsWith("tacz:ammo/")) {
            ResourceLocation id = ResourceLocation.tryParse(key.substring("tacz:ammo/".length()));
            return id == null ? ItemStack.EMPTY : TaczMarketCompat.makeAmmo(id, count);
        }
        if (NbtVariants.isVariantKey(key)) return NbtVariants.stackFor(key, count);

        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        // getValue falls back to AIR for an unknown id, which is how an entry for an item from
        // a mod the pack does not have quietly drops out of the shop instead of listing
        // a stack of nothing.
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }
}
