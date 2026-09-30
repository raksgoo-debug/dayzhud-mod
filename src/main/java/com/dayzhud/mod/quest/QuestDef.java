package com.dayzhud.mod.quest;

import com.dayzhud.mod.DayzHudMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One trader quest (2.15.0), loaded from data/&lt;namespace&gt;/dayzhud_quests/&lt;name&gt;.json:
 * <pre>
 * {
 *   "title": "First Aid",
 *   "description": "The clinic is out of dressings.",
 *   "order": 10,                          sort order in the list (low first)
 *   "requires": ["dayzhud:supply_run"],   quests that must be completed first
 *   "repeatable": false,
 *   "objectives": [
 *     {"type": "deliver", "item": "tarkovdayz:bandgecivil", "count": 2},
 *     {"type": "kill", "target": "undead", "count": 10, "weapon": "gun"},
 *     {"type": "extract", "count": 1}
 *   ],
 *   "rewards": {"roubles": 8000, "items": [{"item": "tarkovdayz:bandge", "count": 2}]}
 * }
 * </pre>
 * <b>deliver</b> items are handed over when the quest is turned in. "item" takes any market
 * key (plain id, tacz:gun/&lt;id&gt;, lrtactical:consumable/&lt;variant&gt;) or #tag.
 * <b>kill</b> "target": an entity id, #entity_tag, "undead", "hostile", "player" or "any";
 * "weapon": "gun" to count only kills made holding a TACZ gun. <b>extract</b>: extractions,
 * optionally at one "point" by name.
 */
public record QuestDef(ResourceLocation id, String title, String description, int order,
                       List<ResourceLocation> requires, boolean repeatable,
                       List<Objective> objectives, long roubles, List<Reward> items) {

    public enum Type { DELIVER, KILL, EXTRACT }

    public record Objective(Type type, String target, int count, String weapon) {}

    public record Reward(String item, int count) {}

    static QuestDef parse(ResourceLocation id, JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        List<ResourceLocation> requires = new ArrayList<>();
        if (o.has("requires")) {
            for (JsonElement r : o.getAsJsonArray("requires")) {
                ResourceLocation rid = ResourceLocation.tryParse(r.getAsString());
                if (rid != null) requires.add(rid);
            }
        }
        List<Objective> objectives = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("objectives")) {
            JsonObject ob = e.getAsJsonObject();
            Type type = Type.valueOf(ob.get("type").getAsString().toUpperCase(Locale.ROOT));
            String target = switch (type) {
                case DELIVER -> ob.get("item").getAsString();
                case KILL -> ob.has("target") ? ob.get("target").getAsString() : "hostile";
                case EXTRACT -> ob.has("point") ? ob.get("point").getAsString() : "";
            };
            int count = ob.has("count") ? Math.max(1, ob.get("count").getAsInt()) : 1;
            String weapon = ob.has("weapon") ? ob.get("weapon").getAsString() : "any";
            objectives.add(new Objective(type, target, count, weapon));
        }
        long roubles = 0;
        List<Reward> items = new ArrayList<>();
        if (o.has("rewards")) {
            JsonObject r = o.getAsJsonObject("rewards");
            if (r.has("roubles")) roubles = r.get("roubles").getAsLong();
            if (r.has("items")) {
                JsonArray arr = r.getAsJsonArray("items");
                for (JsonElement e : arr) {
                    JsonObject it = e.getAsJsonObject();
                    items.add(new Reward(it.get("item").getAsString(),
                            it.has("count") ? Math.max(1, it.get("count").getAsInt()) : 1));
                }
            }
        }
        if (objectives.isEmpty()) {
            DayzHudMod.LOGGER.warn("dayzhud quest {} has no objectives", id);
        }
        return new QuestDef(id,
                o.has("title") ? o.get("title").getAsString() : id.getPath(),
                o.has("description") ? o.get("description").getAsString() : "",
                o.has("order") ? o.get("order").getAsInt() : 1000,
                List.copyOf(requires),
                o.has("repeatable") && o.get("repeatable").getAsBoolean(),
                List.copyOf(objectives), roubles, List.copyOf(items));
    }
}
