package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads data/&lt;namespace&gt;/market/*.json into {@link MarketPrices}.
 *
 * Being a datapack resource rather than a config file is what lets a pack override prices
 * per world and per modpack: a datapack shipping the same path (data/dayzhud/market/
 * prices.json) simply wins, the way any resource override does. Additional files under the
 * same folder merge in, which is the route for an addon that adds its own items.
 */
public class MarketPriceLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public MarketPriceLoader() {
        super(GSON, "market");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, MarketPrices.Entry> merged = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            merged.putAll(MarketPrices.parse(file.getValue()));
        }
        MarketPrices.set(merged);
        MarketCatalog.invalidate();
        DayzHudMod.LOGGER.info("Loaded {} market price entries from {} file(s)",
                merged.size(), files.size());
    }
}
