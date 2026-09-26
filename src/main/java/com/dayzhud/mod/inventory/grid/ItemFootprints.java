package com.dayzhud.mod.inventory.grid;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves an item's footprint from {@link GridConfig}, in priority order: a specific gun's
 * own id, then its TACZ type, then a plain item-id override, then 1x1.
 */
public final class ItemFootprints {

    private static final Pattern ENTRY = Pattern.compile("^(.+)=(\\d+)x(\\d+)$");

    private static Map<String, Footprint> gunTypeMap;
    private static Map<ResourceLocation, Footprint> gunIdMap;
    private static Map<ResourceLocation, Footprint> itemIdMap;
    private static Map<String, Footprint> armorTypeMap;
    private static java.util.Set<String> armorMods;
    private static final String[] LR_TAGS = {"MeleeWeaponId", "ConsumableId", "ThrowableId"};

    private ItemFootprints() {}

    private static synchronized void ensureLoaded() {
        if (gunTypeMap != null) return;
        gunTypeMap = parseTypeMap(GridConfig.GUN_TYPE_FOOTPRINTS.get());
        gunIdMap = parseIdMap(GridConfig.GUN_ID_FOOTPRINTS.get());
        itemIdMap = parseIdMap(GridConfig.ITEM_FOOTPRINTS.get());
        armorTypeMap = parseTypeMap(GridConfig.ARMOR_TYPE_FOOTPRINTS.get());
        armorMods = new java.util.HashSet<>(GridConfig.ARMOR_FOOTPRINT_MODS.get());
    }

    private static Map<String, Footprint> parseTypeMap(List<? extends String> raw) {
        Map<String, Footprint> out = new HashMap<>();
        for (String line : raw) {
            Footprint fp = parseFootprint(line);
            if (fp == null) continue;
            out.put(keyOf(line).toLowerCase(Locale.ROOT), fp);
        }
        return out;
    }

    private static Map<ResourceLocation, Footprint> parseIdMap(List<? extends String> raw) {
        Map<ResourceLocation, Footprint> out = new HashMap<>();
        for (String line : raw) {
            Footprint fp = parseFootprint(line);
            if (fp == null) continue;
            ResourceLocation id = ResourceLocation.tryParse(keyOf(line));
            if (id == null) {
                warn(line);
                continue;
            }
            out.put(id, fp);
        }
        return out;
    }

    private static String keyOf(String line) {
        return line.substring(0, line.indexOf('='));
    }

    private static Footprint parseFootprint(String line) {
        Matcher m = ENTRY.matcher(line.trim());
        if (!m.matches()) {
            warn(line);
            return null;
        }
        return new Footprint(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private static void warn(String line) {
        // Every audio/config bug elsewhere in this project turned out to have failed
        // silently - this one announces itself instead of just falling back to 1x1.
        DayzHudMod.LOGGER.warn("dayzhud grid config: ignoring malformed footprint entry '{}' "
                + "(expected key=WxH)", line);
    }

    /**
     * The item's base footprint, unrotated. {@link ItemGrid#footprintOf} applies rotation.
     * For a TACZ gun this includes room for its fitted attachments (GunSizes).
     */
    public static Footprint baseFootprintOf(ItemStack stack) {
        Footprint plain = plainFootprintOf(stack);
        if (stack.isEmpty() || !GridConfig.ENABLED.get()) return plain;
        Optional<ResourceLocation> gunId = TaczMarketCompat.gunIdOf(stack);
        if (gunId.isEmpty()) return plain;
        return GunSizes.withAttachments(gunId.get(), TaczMarketCompat.attachmentIdsOf(stack), plain);
    }

    /** {@link #baseFootprintOf} without the room for a gun's attachments - the footprint a gun
     *  is drawn to fill, whatever is fitted to it. */
    public static Footprint plainFootprintOf(ItemStack stack) {
        if (stack.isEmpty() || !GridConfig.ENABLED.get()) return Footprint.SINGLE;
        ensureLoaded();

        Optional<ResourceLocation> gunId = TaczMarketCompat.gunIdOf(stack);
        if (gunId.isPresent()) {
            Footprint byId = gunIdMap.get(gunId.get());
            if (byId != null) return byId;
            // Built-in per-gun sizes from each model's real proportions (DefaultGunFootprints).
            Footprint builtin = DefaultGunFootprints.TABLE.get(gunId.get().toString());
            if (builtin != null) return builtin;
            Optional<String> type = TaczMarketCompat.gunTypeOf(stack);
            if (type.isPresent()) {
                Footprint byType = gunTypeMap.get(type.get());
                if (byType != null) return byType;
            }
        }

        // LR Tactical variants: the variant id lives in NBT (see DefaultItemFootprints).
        if (stack.hasTag()) {
            for (String tag : LR_TAGS) {
                if (!stack.getTag().contains(tag)) continue;
                ResourceLocation variant = ResourceLocation.tryParse(stack.getTag().getString(tag));
                if (variant == null) break;
                Footprint byConfig = gunIdMap.get(variant);          // gunIdFootprints also covers these ids
                if (byConfig != null) return byConfig;
                Footprint builtin = DefaultItemFootprints.LR_VARIANTS.get(variant.toString());
                if (builtin != null) return builtin;
                break;
            }
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            Footprint byItem = itemIdMap.get(itemId);
            if (byItem != null) return byItem;
            // One item for every gun's magazine - sized by the gun it belongs to.
            if (MagazineFootprints.MAGAZINE.equals(itemId.toString())) return MagazineFootprints.of(stack);
            Footprint builtin = DefaultItemFootprints.ITEMS.get(itemId.toString());
            if (builtin != null) return builtin;
            // Armor by type, for the listed mods (CAPS: 61 of its items are real ArmorItems).
            if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor
                    && armorMods.contains(itemId.getNamespace())) {
                // getType().getSlot() - both proven by CAPS's own bytecode; mapped to the config
                // names here rather than trusting Type.getName(), which nothing verifies.
                String typeName = switch (armor.getType().getSlot()) {
                    case HEAD -> "helmet";
                    case CHEST -> "chestplate";
                    case LEGS -> "leggings";
                    case FEET -> "boots";
                    default -> "";
                };
                Footprint byType = armorTypeMap.get(typeName);
                if (byType != null) return byType;
                // A type the config doesn't list: the built-in size. Keeps existing configs
                // (written when only helmet/chestplate were sized) getting leggings/boots too.
                Footprint builtinType = DefaultItemFootprints.ARMOR_TYPES.get(typeName);
                if (builtinType != null) return builtinType;
            }
        }
        return Footprint.SINGLE;
    }
}
