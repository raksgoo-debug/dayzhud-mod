package com.dayzhud.mod.quest;

import java.util.List;

/** The quests the server last sent for the local player (QuestPackets.Sync). */
public final class ClientQuestState {

    private static List<QuestPackets.View> views = List.of();

    private ClientQuestState() {}

    static void accept(List<QuestPackets.View> v) {
        views = List.copyOf(v);
    }

    public static List<QuestPackets.View> views() {
        return views;
    }
}
