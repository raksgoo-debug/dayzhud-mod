package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * TaCZ: Magazines - empty magazines in the shop.
 *
 * Same shape as every other compat here: reflective, cached, degrading to empty, no
 * compile-time dependency.
 *
 * Magazines are one registered item (taczmagazines:magazine) carrying a FAMILY string, so
 * this is the same "many items on one registry name" problem as TACZ guns and lrtactical
 * consumables. It is handled here rather than through NbtVariants because the family lives
 * behind {@code MagazineItem.getMagazineFamilyId} rather than in a plain NBT string this mod
 * can name, and because the list of families comes from the mod's own registry
 * ({@code getAllMagazineFamilies}) rather than from a config line.
 */
public final class MagazineCompat {

    public static final String MOD_ID = "taczmagazines";
    public static final String KEY_PREFIX = "taczmagazines:magazine/";

    private static Boolean loaded;
    private static boolean resolved;

    private static Item magazineItem;
    private static Method getAllFamilies;
    private static Method getFamilyId;
    private static Method getMaxCapacity;
    private static Method createByFamily;

    private MagazineCompat() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MOD_ID);
        return loaded;
    }

    public static boolean isActive() {
        return isModLoaded() && resolve();
    }

    private static synchronized boolean resolve() {
        if (resolved) return magazineItem != null;
        resolved = true;
        try {
            ClassLoader cl = MagazineCompat.class.getClassLoader();
            Class<?> registrar = Class.forName(
                    "com.raiiiden.taczmagazines.item.MagazineRegistrar", false, cl);
            getAllFamilies = registrar.getMethod("getAllMagazineFamilies");
            Field magField = registrar.getField("MAGAZINE");
            Object registryObject = magField.get(null);
            Object item = registryObject.getClass().getMethod("get").invoke(registryObject);
            magazineItem = item instanceof Item i ? i : null;

            Class<?> magItem = Class.forName(
                    "com.raiiiden.taczmagazines.item.MagazineItem", false, cl);
            getFamilyId = magItem.getMethod("getMagazineFamilyId", ItemStack.class);
            getMaxCapacity = magItem.getMethod("getMaxCapacity", ItemStack.class);
            createByFamily = magItem.getMethod("createMagazineByFamily",
                    Item.class, String.class, int.class);
            return magazineItem != null;
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("TaCZ Magazines is installed but its API did not resolve - "
                    + "magazines will not be stocked: {}", t.toString());
            magazineItem = null;
            return false;
        }
    }

    /** The price key for a magazine stack, or null when it is not one. */
    public static String keyOf(ItemStack stack) {
        if (stack.isEmpty() || !isActive() || stack.getItem() != magazineItem) return null;
        try {
            Object family = getFamilyId.invoke(null, stack);
            String id = family == null ? null : family.toString();
            return id == null || id.isEmpty() ? null : KEY_PREFIX + id;
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<String> families() {
        List<String> out = new ArrayList<>();
        if (!isActive()) return out;
        try {
            Object result = getAllFamilies.invoke(null);
            if (result instanceof List<?> list) {
                for (Object o : list) if (o != null) out.add(o.toString());
            }
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("Could not enumerate magazine families: {}", t.toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    /** An EMPTY magazine of this family - the shop sells the tin, not the rounds. */
    public static ItemStack makeEmpty(String family) {
        if (!isActive()) return ItemStack.EMPTY;
        try {
            Object stack = createByFamily.invoke(null, magazineItem, family, 0);
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack makeFor(String key) {
        if (!key.startsWith(KEY_PREFIX)) return ItemStack.EMPTY;
        return makeEmpty(key.substring(KEY_PREFIX.length()));
    }

    public static int capacityOf(ItemStack stack) {
        if (stack.isEmpty() || !isActive()) return 0;
        try {
            Object cap = getMaxCapacity.invoke(null, stack);
            return cap == null ? 0 : ((Number) cap).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Price from capacity. A magazine is a container, so what it is worth is how much it
     * holds - a 100-round drum should not cost what a 7-round pistol mag does.
     */
    public static int priceOf(ItemStack stack) {
        int capacity = capacityOf(stack);
        // Capacity comes out of the stack's own NBT, written by the mod from its family data.
        // If that data has not loaded, or a family declares none, capacity is 0 - and the
        // first version treated that as "worthless" and dropped the magazine from the
        // catalogue without a word. A magazine with an unknown size is still a magazine:
        // fall back to the base price rather than making it disappear.
        double base = MarketConfig.MAGAZINE_BASE_PRICE.get()
                + Math.max(0, capacity) * MarketConfig.MAGAZINE_PER_ROUND.get();
        return (int) Math.max(50, Math.round(base / 50.0) * 50);
    }
}
