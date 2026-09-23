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

    private ItemFootprints() {}

    private static synchronized void ensureLoaded() {
        if (gunTypeMap != null) return;
        gunTypeMap = parseTypeMap(GridConfig.GUN_TYPE_FOOTPRINTS.get());
        gunIdMap = parseIdMap(GridConfig.GUN_ID_FOOTPRINTS.get());
        itemIdMap = parseIdMap(GridConfig.ITEM_FOOTPRINTS.get());
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

    /** The item's base footprint, unrotated. {@link ItemGrid#footprintOf} applies rotation. */
    public static Footprint baseFootprintOf(ItemStack stack) {
        if (stack.isEmpty() || !GridConfig.ENABLED.get()) return Footprint.SINGLE;
        ensureLoaded();

        Optional<ResourceLocation> gunId = TaczMarketCompat.gunIdOf(stack);
        if (gunId.isPresent()) {
            Footprint byId = gunIdMap.get(gunId.get());
            if (byId != null) return byId;
            Optional<String> type = TaczMarketCompat.gunTypeOf(stack);
            if (type.isPresent()) {
                Footprint byType = gunTypeMap.get(type.get());
                if (byType != null) return byType;
            }
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            Footprint byItem = itemIdMap.get(itemId);
            if (byItem != null) return byItem;
        }
        return Footprint.SINGLE;
    }
}
