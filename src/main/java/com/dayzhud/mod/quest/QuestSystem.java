package com.dayzhud.mod.quest;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.grid.GridPickup;
import com.dayzhud.mod.market.MarketNetwork;
import com.dayzhud.mod.market.TaczMarketCompat;
import com.dayzhud.mod.market.Wallet;
import com.dayzhud.mod.market.WalletCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trader quests (2.15.0) - server side. Quests are taken and handed in at the trader (the
 * Quests tab, which is why actions require the market screen to be open); kill and
 * extraction progress is counted wherever it happens.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class QuestSystem {

    public static final int AVAILABLE = 0, ACTIVE = 1, DONE = 2;

    private QuestSystem() {}

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new QuestLoader());
    }

    // ---- actions --------------------------------------------------------------------------

    public static void accept(ServerPlayer player, ResourceLocation id) {
        QuestDef quest = QuestLoader.quests().get(id);
        QuestLog log = QuestLog.of(player);
        if (quest == null || log == null || log.active.containsKey(id)) return;
        if (!unlocked(log, quest) || (log.done.contains(id) && !quest.repeatable())) return;
        log.active.put(id, new int[quest.objectives().size()]);
        player.displayClientMessage(Component.translatable("message.dayzhud.quest.accepted", quest.title())
                .withStyle(ChatFormatting.GOLD), true);
        sync(player);
    }

    public static void abandon(ServerPlayer player, ResourceLocation id) {
        QuestLog log = QuestLog.of(player);
        if (log != null && log.active.remove(id) != null) sync(player);
    }

    public static void turnIn(ServerPlayer player, ResourceLocation id) {
        QuestDef quest = QuestLoader.quests().get(id);
        QuestLog log = QuestLog.of(player);
        if (quest == null || log == null) return;
        int[] progress = log.active.get(id);
        if (progress == null || !ready(player, quest, progress)) return;

        for (QuestDef.Objective o : quest.objectives()) {
            if (o.type() == QuestDef.Type.DELIVER) QuestItems.take(player, o.target(), o.count());
        }
        if (quest.roubles() > 0) {
            Wallet wallet = WalletCapability.of(player);
            if (wallet != null) {
                wallet.add(quest.roubles());
                MarketNetwork.syncWallet(player);
            }
        }
        for (QuestDef.Reward r : quest.items()) {
            ItemStack stack = QuestItems.icon(r.item(), r.count());
            if (stack.isEmpty()) {
                DayzHudMod.LOGGER.warn("dayzhud quest {}: reward '{}' isn't an item here - skipped", id, r.item());
                continue;
            }
            GridPickup.give(player, stack);
        }
        log.active.remove(id);
        log.done.add(id);
        player.displayClientMessage(Component.translatable("message.dayzhud.quest.completed", quest.title())
                .withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.6f, 1.2f);
        sync(player);
    }

    static boolean unlocked(QuestLog log, QuestDef quest) {
        for (ResourceLocation r : quest.requires()) if (!log.done.contains(r)) return false;
        return true;
    }

    static boolean ready(Player player, QuestDef quest, int[] progress) {
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDef.Objective o = quest.objectives().get(i);
            int have = o.type() == QuestDef.Type.DELIVER ? QuestItems.countIn(player, o.target())
                    : i < progress.length ? progress[i] : 0;
            if (have < o.count()) return false;
        }
        return true;
    }

    // ---- progress ------------------------------------------------------------------------

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        if (victim == player) return;
        boolean gun = TaczMarketCompat.gunIdOf(player.getMainHandItem()).isPresent();
        progress(player, QuestDef.Type.KILL, o -> killMatches(o, victim) && (!"gun".equals(o.weapon()) || gun));
    }

    /** An extraction completed (Extraction), at the point with this name. */
    public static void onExtract(ServerPlayer player, String point) {
        progress(player, QuestDef.Type.EXTRACT, o -> o.target().isEmpty() || o.target().equalsIgnoreCase(point));
    }

    private static void progress(ServerPlayer player, QuestDef.Type type,
                                 java.util.function.Predicate<QuestDef.Objective> counts) {
        QuestLog log = QuestLog.of(player);
        if (log == null || log.active.isEmpty()) return;
        boolean changed = false;
        for (Map.Entry<ResourceLocation, int[]> e : log.active.entrySet()) {
            QuestDef quest = QuestLoader.quests().get(e.getKey());
            if (quest == null) continue;
            int[] p = e.getValue();
            for (int i = 0; i < quest.objectives().size() && i < p.length; i++) {
                QuestDef.Objective o = quest.objectives().get(i);
                if (o.type() != type || p[i] >= o.count() || !counts.test(o)) continue;
                p[i]++;
                changed = true;
                player.displayClientMessage(Component.translatable("message.dayzhud.quest.progress",
                        quest.title(), labelOf(o), p[i], o.count()).withStyle(ChatFormatting.YELLOW), true);
            }
        }
        if (changed) sync(player);
    }

    static boolean killMatches(QuestDef.Objective o, LivingEntity victim) {
        String t = o.target().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "any" -> true;
            case "undead" -> victim.getMobType() == MobType.UNDEAD;
            case "hostile" -> victim instanceof Enemy;
            case "player" -> victim instanceof Player;
            default -> {
                if (t.startsWith("#")) {
                    ResourceLocation tag = ResourceLocation.tryParse(t.substring(1));
                    yield tag != null && victim.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag));
                }
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
                yield id != null && id.toString().equals(t);
            }
        };
    }

    /** What an objective asks for, in words. */
    static Component labelOf(QuestDef.Objective o) {
        return switch (o.type()) {
            case DELIVER -> {
                ItemStack icon = QuestItems.icon(o.target(), 1);
                yield Component.translatable("quest.dayzhud.objective.deliver",
                        icon.isEmpty() ? Component.literal(o.target()) : icon.getHoverName());
            }
            case KILL -> {
                Component what = switch (o.target().toLowerCase(Locale.ROOT)) {
                    case "any" -> Component.translatable("quest.dayzhud.target.any");
                    case "undead" -> Component.translatable("quest.dayzhud.target.undead");
                    case "hostile" -> Component.translatable("quest.dayzhud.target.hostile");
                    case "player" -> Component.translatable("quest.dayzhud.target.player");
                    default -> {
                        if (o.target().startsWith("#")) {
                            String path = o.target().substring(o.target().indexOf(':') + 1).replace('_', ' ');
                            yield Component.literal(path);
                        }
                        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(o.target()));
                        yield type == null ? Component.literal(o.target()) : type.getDescription();
                    }
                };
                yield Component.translatable("gun".equals(o.weapon())
                        ? "quest.dayzhud.objective.kill_gun" : "quest.dayzhud.objective.kill", what);
            }
            case EXTRACT -> o.target().isEmpty() ? Component.translatable("quest.dayzhud.objective.extract")
                    : Component.translatable("quest.dayzhud.objective.extract_at", o.target());
        };
    }

    // ---- sync ------------------------------------------------------------------------------

    /** Sends every quest this player can see - available, active, done - with progress. */
    public static void sync(ServerPlayer player) {
        QuestLog log = QuestLog.of(player);
        if (log == null) return;
        List<QuestPackets.View> views = new ArrayList<>();
        for (QuestDef q : QuestLoader.quests().values()) {
            int status;
            int[] progress = log.active.get(q.id());
            if (progress != null) status = ACTIVE;
            else if (log.done.contains(q.id()) && !q.repeatable()) status = DONE;
            else if (unlocked(log, q)) status = AVAILABLE;
            else continue;                                   // locked: not shown yet
            List<QuestPackets.ObjectiveView> objectives = new ArrayList<>();
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestDef.Objective o = q.objectives().get(i);
                ItemStack icon = o.type() == QuestDef.Type.DELIVER ? QuestItems.icon(o.target(), o.count()) : ItemStack.EMPTY;
                int done = status == DONE ? o.count() : progress != null && i < progress.length ? progress[i] : 0;
                objectives.add(new QuestPackets.ObjectiveView(o.type().ordinal(), o.target(), labelOf(o), icon, done, o.count()));
            }
            List<ItemStack> rewards = new ArrayList<>();
            for (QuestDef.Reward r : q.items()) {
                ItemStack s = QuestItems.icon(r.item(), r.count());
                if (!s.isEmpty()) rewards.add(s);
            }
            views.add(new QuestPackets.View(q.id(), q.title(), q.description(), status, objectives, q.roubles(), rewards));
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuestPackets.Sync(views));
    }
}
