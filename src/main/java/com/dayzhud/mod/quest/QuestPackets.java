package com.dayzhud.mod.quest;

import com.dayzhud.mod.market.MarketMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Trader quest packets (2.15.0). */
public final class QuestPackets {

    private QuestPackets() {}

    public record ObjectiveView(int type, String target, Component label, ItemStack icon, int progress, int count) {}

    public record View(ResourceLocation id, String title, String description, int status,
                       List<ObjectiveView> objectives, long roubles, List<ItemStack> rewards) {}

    // ------------------------------------------------------------------ S -> C sync

    public static class Sync {
        final List<View> views;

        public Sync(List<View> views) {
            this.views = views;
        }

        public static void encode(Sync p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.views.size());
            for (View v : p.views) {
                buf.writeResourceLocation(v.id());
                buf.writeUtf(v.title());
                buf.writeUtf(v.description());
                buf.writeVarInt(v.status());
                buf.writeVarInt(v.objectives().size());
                for (ObjectiveView o : v.objectives()) {
                    buf.writeVarInt(o.type());
                    buf.writeUtf(o.target());
                    buf.writeComponent(o.label());
                    buf.writeItem(o.icon());
                    buf.writeVarInt(o.progress());
                    buf.writeVarInt(o.count());
                }
                buf.writeVarLong(v.roubles());
                buf.writeVarInt(v.rewards().size());
                for (ItemStack s : v.rewards()) buf.writeItem(s);
            }
        }

        public static Sync decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<View> views = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ResourceLocation id = buf.readResourceLocation();
                String title = buf.readUtf();
                String description = buf.readUtf();
                int status = buf.readVarInt();
                int on = buf.readVarInt();
                List<ObjectiveView> objectives = new ArrayList<>(on);
                for (int j = 0; j < on; j++) {
                    objectives.add(new ObjectiveView(buf.readVarInt(), buf.readUtf(), buf.readComponent(),
                            buf.readItem(), buf.readVarInt(), buf.readVarInt()));
                }
                long roubles = buf.readVarLong();
                int rn = buf.readVarInt();
                List<ItemStack> rewards = new ArrayList<>(rn);
                for (int j = 0; j < rn; j++) rewards.add(buf.readItem());
                views.add(new View(id, title, description, status, objectives, roubles, rewards));
            }
            return new Sync(views);
        }

        public static void handle(Sync p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientQuestState.accept(p.views));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C -> S action

    public static class Action {
        public static final int ACCEPT = 0, TURN_IN = 1, ABANDON = 2;
        final int action;
        final ResourceLocation id;

        public Action(int action, ResourceLocation id) {
            this.action = action;
            this.id = id;
        }

        public static void encode(Action p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.action);
            buf.writeResourceLocation(p.id);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readResourceLocation());
        }

        public static void handle(Action p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                // Quests are taken and handed in at the trader.
                if (player == null || !(player.containerMenu instanceof MarketMenu)) return;
                switch (p.action) {
                    case ACCEPT -> QuestSystem.accept(player, p.id);
                    case TURN_IN -> QuestSystem.turnIn(player, p.id);
                    case ABANDON -> QuestSystem.abandon(player, p.id);
                    default -> { }
                }
            });
            context.setPacketHandled(true);
        }
    }
}
