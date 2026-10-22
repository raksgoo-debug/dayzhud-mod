package com.dayzhud.mod.recap;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.injury.InjuryCapability;
import com.dayzhud.mod.injury.InjuryConfig;
import com.dayzhud.mod.injury.InjurySystem;
import com.dayzhud.mod.injury.Injuries;
import com.dayzhud.mod.inventory.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Death recap (2.19.0), server side.
 *
 * Every hit a player takes is logged for damageWindowSeconds (LivingHurtEvent at LOWEST, so
 * after every other mod - this mod's Toughness skill included - has adjusted the amount).
 * When the player dies the log is folded into one line per attacker-and-weapon (or per cause,
 * for bleeding, falls and so on) and sent with the killer, weapon, distance and headshot.
 *
 * <b>Who killed you.</b> The killing blow's attacker, or - when the blow had none, like
 * bleeding out or a fall after being shot - vanilla's kill credit (whoever hurt you last,
 * recently). The cause line then says how you actually died.
 *
 * <b>Kills this life</b> are counted under Forge's PlayerPersisted tag, so they survive a
 * relog and the End's exit portal, and are reset here when you die.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class DeathRecapTracker {

    private static final int MAX_LOGGED_HITS = 128;
    private static final int MAX_LINES = 6;
    private static final String PLAYER_KILLS = "dayzhud_life_player_kills";
    private static final String MOB_KILLS = "dayzhud_life_mob_kills";

    private record Hit(long time, String key, @Nullable UUID attacker, Component who,
                       @Nullable Component weapon, float amount) {}

    private static final Map<UUID, Deque<Hit>> LOG = new HashMap<>();
    /** Game time of the last TACZ headshot each player took. */
    private static final Map<UUID, Long> HEADSHOT_AT = new HashMap<>();

    private DeathRecapTracker() {}

    // ---- logging hits --------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!RecapConfig.ENABLED.get() || !(event.getEntity() instanceof ServerPlayer player)) return;
        float amount = event.getAmount();
        if (amount <= 0f) return;
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        long now = player.serverLevel().getGameTime();

        Hit hit;
        if (attacker != null && attacker != player) {
            Component weapon = weaponOf(attacker, source);
            String key = attacker.getUUID() + "|" + (weapon == null ? "" : weapon.getString());
            hit = new Hit(now, key, attacker.getUUID(), attacker.getDisplayName(), weapon, amount);
        } else {
            hit = new Hit(now, "cause|" + source.getMsgId(), null, causeOf(source), null, amount);
        }
        Deque<Hit> log = LOG.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        log.addLast(hit);
        trim(log, now);
    }

    private static void trim(Deque<Hit> log, long now) {
        long oldest = now - RecapConfig.WINDOW_SECONDS.get() * 20L;
        while (!log.isEmpty() && (log.peekFirst().time < oldest || log.size() > MAX_LOGGED_HITS)) {
            log.pollFirst();
        }
    }

    /**
     * What {@code attacker} hit with: the item in its main hand (a TACZ gun, a sword), or else
     * the projectile itself (a thrown trident, an arrow from a mob holding nothing). Null for
     * bare hands and mobs that just bite.
     */
    @Nullable
    private static Component weaponOf(Entity attacker, DamageSource source) {
        if (attacker instanceof LivingEntity living) {
            ItemStack held = living.getMainHandItem();
            if (!held.isEmpty()) return held.getHoverName();
        }
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != attacker) return direct.getDisplayName();
        return null;
    }

    /** A short name for a death with no attacker. */
    private static Component causeOf(DamageSource source) {
        String key;
        if (source.is(InjurySystem.BLEEDING)) key = "bleeding";
        else if (source.is(DamageTypeTags.IS_FALL)) key = "fall";
        else if (source.is(DamageTypeTags.IS_FIRE)) key = "fire";
        else if (source.is(DamageTypeTags.IS_DROWNING)) key = "drowning";
        else if (source.is(DamageTypeTags.IS_FREEZING)) key = "freezing";
        else if (source.is(DamageTypeTags.IS_EXPLOSION)) key = "explosion";
        else if (source.is(DamageTypeTags.IS_PROJECTILE)) key = "projectile";
        else if (source.is(DamageTypes.STARVE)) key = "starvation";
        else return Component.translatable("recap.dayzhud.cause.other", source.getMsgId());
        return Component.translatable("recap.dayzhud.cause." + key);
    }

    // ---- the death -----------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!RecapConfig.ENABLED.get()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource source = event.getSource();

        // Count the kill for whoever made it, before anything else (the victim may be them).
        Entity creditEntity = source.getEntity() != null ? source.getEntity() : victim.getKillCredit();
        if (creditEntity instanceof ServerPlayer killer && killer != victim) {
            if (victim instanceof Player) {
                bump(killer, PLAYER_KILLS);
                if (RecapConfig.NOTIFY_KILLER.get()) {
                    killer.displayClientMessage(Component.translatable("message.dayzhud.recap.kill",
                                    victim.getDisplayName(), Math.round(killer.distanceTo(victim)))
                            .withStyle(ChatFormatting.GRAY), true);
                }
            } else if (victim instanceof Enemy) {
                bump(killer, MOB_KILLS);
            }
        }

        if (victim instanceof ServerPlayer player) {
            try {
                sendRecap(player, source, creditEntity == player ? null : creditEntity);
            } catch (RuntimeException e) {
                // Never let a recap problem get in the way of dying properly.
                DayzHudMod.LOGGER.error("[dayzhud] Couldn't build a death recap for {}", player.getName().getString(), e);
            }
            CompoundTag life = lifeTag(player);
            life.remove(PLAYER_KILLS);
            life.remove(MOB_KILLS);
            LOG.remove(player.getUUID());
            HEADSHOT_AT.remove(player.getUUID());
        }
    }

    private static void sendRecap(ServerPlayer player, DamageSource source, @Nullable Entity killer) {
        long now = player.serverLevel().getGameTime();
        Deque<Hit> log = LOG.getOrDefault(player.getUUID(), new ArrayDeque<>());
        trim(log, now);

        // One line per attacker+weapon / cause, biggest first.
        Map<String, float[]> totals = new LinkedHashMap<>();
        Map<String, Hit> firstOf = new HashMap<>();
        Component lastWeaponOfKiller = null;
        float total = 0f;
        for (Hit hit : log) {
            float[] t = totals.computeIfAbsent(hit.key, k -> new float[2]);
            t[0] += 1;
            t[1] += hit.amount;
            firstOf.putIfAbsent(hit.key, hit);
            total += hit.amount;
            if (killer != null && killer.getUUID().equals(hit.attacker) && hit.weapon != null) {
                lastWeaponOfKiller = hit.weapon;
            }
        }
        List<DeathRecapPacket.Line> lines = new ArrayList<>();
        totals.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue()[1], a.getValue()[1]))
                .limit(MAX_LINES)
                .forEach(e -> {
                    Hit h = firstOf.get(e.getKey());
                    lines.add(new DeathRecapPacket.Line(h.who, h.weapon, (int) e.getValue()[0], e.getValue()[1]));
                });

        boolean blowHadAttacker = source.getEntity() != null && source.getEntity() != player;
        Component weapon = null;
        if (killer != null) {
            // The weapon as it was when the hit landed (they may have swapped since), falling
            // back to what they're holding now.
            weapon = lastWeaponOfKiller != null ? lastWeaponOfKiller
                    : blowHadAttacker ? weaponOf(killer, source) : null;
        }
        Long headshotAt = HEADSHOT_AT.get(player.getUUID());
        boolean headshot = blowHadAttacker && headshotAt != null && now - headshotAt <= 1;

        int light = 0, heavy = 0;
        if (InjuryConfig.ENABLED.get()) {
            Injuries inj = InjuryCapability.of(player);
            if (inj != null) {
                light = inj.light();
                heavy = inj.heavy();
            }
        }
        int survivedTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        CompoundTag life = lifeTag(player);

        DeathRecapPacket packet = new DeathRecapPacket(
                player.getCombatTracker().getDeathMessage(),
                killerModel(killer),
                killer == null ? null : killer.getDisplayName(),
                killer instanceof Player,
                blowHadAttacker ? null : causeOf(source),
                weapon,
                killer == null ? -1f : player.distanceTo(killer),
                headshot,
                lines,
                total,
                RecapConfig.WINDOW_SECONDS.get(),
                survivedTicks / 20,
                life.getInt(PLAYER_KILLS),
                life.getInt(MOB_KILLS),
                light,
                heavy);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** What the death screen needs to draw the killer; null when there's no living killer. */
    @Nullable
    private static DeathRecapPacket.KillerModel killerModel(@Nullable Entity killer) {
        if (!(killer instanceof LivingEntity living)) return null;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
        if (type == null) return null;
        List<ItemStack> gear = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) gear.add(living.getItemBySlot(slot).copy());
        UUID profileId = null;
        String profileName = "";
        // Only a real player gets rebuilt from their profile. An NPC that happens to extend
        // Player has its own entity type and is rebuilt like any other mob.
        boolean realPlayer = living instanceof Player && living.getType() == EntityType.PLAYER;
        if (realPlayer) {
            profileId = ((Player) living).getGameProfile().getId();
            profileName = ((Player) living).getGameProfile().getName();
        }
        return new DeathRecapPacket.KillerModel(living.getId(), type, profileId, profileName, gear,
                realPlayer ? new byte[0] : spawnDataOf(living), syncedDataOf(living),
                realPlayer ? null : nbtOf(living));
    }

    /** NBT bigger than this is left out of the snapshot (an NPC with a full inventory, say). */
    private static final int MAX_SNAPSHOT_NBT_BYTES = 256 * 1024;

    /** Forge's additional spawn data, exactly as the entity sends it when it comes into view. */
    private static byte[] spawnDataOf(LivingEntity living) {
        if (!(living instanceof IEntityAdditionalSpawnData spawn)) return new byte[0];
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            spawn.writeSpawnData(buf);
            return bytesOf(buf);
        } catch (RuntimeException e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Killer's spawn data unreadable", e);
            return new byte[0];
        }
    }

    /**
     * The entity's synced data (everything not at its default), in the same wire format as
     * vanilla's entity data packet - which is what the client reads it back with.
     */
    private static byte[] syncedDataOf(LivingEntity living) {
        try {
            List<SynchedEntityData.DataValue<?>> values = living.getEntityData().getNonDefaultValues();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            new ClientboundSetEntityDataPacket(living.getId(), values == null ? List.of() : values).write(buf);
            return bytesOf(buf);
        } catch (RuntimeException e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Killer's entity data unreadable", e);
            return new byte[0];
        }
    }

    /** Saved NBT, minus what a picture doesn't need; null if it's too big to send. */
    @Nullable
    private static CompoundTag nbtOf(LivingEntity living) {
        try {
            CompoundTag tag = living.saveWithoutId(new CompoundTag());
            tag.remove("Passengers");
            tag.remove("Brain");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeNbt(tag);
            return buf.readableBytes() <= MAX_SNAPSHOT_NBT_BYTES ? tag : null;
        } catch (RuntimeException e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Killer's NBT unreadable", e);
            return null;
        }
    }

    private static byte[] bytesOf(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    // ---- kills this life -----------------------------------------------------------------

    /** Forge's PlayerPersisted compound, created and attached if it isn't there yet. */
    private static CompoundTag lifeTag(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void bump(Player player, String key) {
        CompoundTag life = lifeTag(player);
        life.putInt(key, life.getInt(key) + 1);
    }

    // ---- TACZ headshots, by reflection ---------------------------------------------------

    /**
     * Listens for TACZ's EntityHurtByGunEvent.Pre, TACZ being optional, to note when a player
     * takes a headshot. Pre rather than Post: a lethal hit kills inside the hurt call, so Post
     * only arrives after the death has been recapped.
     *
     * Risk area: names are TACZ 1.0.x/1.1.x's. If they change this logs a warning and recaps
     * just never say "headshot".
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerCompat() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class eventClass = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Method getHurt = eventClass.getMethod("getHurtEntity");
            Method isHeadShot = eventClass.getMethod("isHeadShot");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, eventClass, (Consumer) event -> {
                try {
                    if (getHurt.invoke(event) instanceof ServerPlayer hurt
                            && Boolean.TRUE.equals(isHeadShot.invoke(event))) {
                        HEADSHOT_AT.put(hurt.getUUID(), hurt.serverLevel().getGameTime());
                    }
                } catch (ReflectiveOperationException e) {
                    DayzHudMod.LOGGER.debug("[dayzhud] TACZ hurt event unreadable", e);
                }
            });
            DayzHudMod.LOGGER.info("[dayzhud] Listening for TACZ headshots (death recap).");
        } catch (ReflectiveOperationException | LinkageError e) {
            DayzHudMod.LOGGER.warn("[dayzhud] TACZ is installed but EntityHurtByGunEvent.Pre couldn't be read - "
                    + "death recaps won't show headshots.", e);
        }
    }

    // ---- cleanup -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOG.remove(event.getEntity().getUUID());
        HEADSHOT_AT.remove(event.getEntity().getUUID());
    }
}
