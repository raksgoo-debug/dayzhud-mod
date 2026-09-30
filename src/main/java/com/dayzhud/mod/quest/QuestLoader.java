package com.dayzhud.mod.quest;

import com.dayzhud.mod.DayzHudMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads data/&lt;namespace&gt;/dayzhud_quests/*.json (see QuestDef). A datapack adds quests by
 * adding files, and replaces or removes one of ours by shipping the same path.
 */
public class QuestLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static volatile Map<ResourceLocation, QuestDef> quests = Map.of();

    public QuestLoader() {
        super(GSON, "dayzhud_quests");
    }

    /** All quests, in display order. */
    public static Map<ResourceLocation, QuestDef> quests() {
        return quests;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<QuestDef> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                loaded.add(QuestDef.parse(file.getKey(), file.getValue()));
            } catch (Exception e) {
                DayzHudMod.LOGGER.warn("dayzhud: skipping quest {} - {}", file.getKey(), e.toString());
            }
        }
        loaded.sort(Comparator.comparingInt(QuestDef::order).thenComparing(q -> q.id().toString()));
        Map<ResourceLocation, QuestDef> map = new LinkedHashMap<>();
        for (QuestDef q : loaded) map.put(q.id(), q);
        quests = Collections.unmodifiableMap(map);
        DayzHudMod.LOGGER.info("Loaded {} trader quest(s)", map.size());
    }
}
