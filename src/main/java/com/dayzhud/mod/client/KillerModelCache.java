package com.dayzhud.mod.client;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.recap.ClientDeathRecap;
import com.dayzhud.mod.recap.DeathRecapPacket;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * The entity the death screen draws as your killer (2.19.0).
 *
 * <b>Why this exists.</b> About a second after you die the server takes your body out of the
 * world, and your client unloads every entity around you - your killer included. Looking the
 * killer up by id each frame therefore worked for a second and then fell back to a rebuilt
 * copy, which for an NPC meant its default skin (Steve). So the killer is captured here on
 * the first client tick after the recap arrives, while it's certainly still loaded, and that
 * same object is kept and drawn until you respawn - unloaded or not. It lives here rather
 * than in the screen because vanilla makes a new death screen every time one is reopened
 * (after chat, for instance).
 *
 * When the killer was never loaded on this client (out of view, or you joined mid-death),
 * a copy is rebuilt from the snapshot the server sent: the same NBT, Forge spawn data,
 * synced entity data and equipment a client gets when an entity comes into view, so modded
 * NPCs keep their skin and gear.
 */
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public final class KillerModelCache {

    @Nullable private static DeathRecapPacket capturedFor;
    @Nullable private static LivingEntity model;

    private KillerModelCache() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        DeathRecapPacket recap = ClientDeathRecap.get();
        if (recap != capturedFor) capture(recap);
    }

    /** The killer to draw for {@code recap}, or null if there's none (or it can't be built). */
    @Nullable
    public static LivingEntity get(@Nullable DeathRecapPacket recap) {
        if (recap != capturedFor) capture(recap);
        return model;
    }

    private static void capture(@Nullable DeathRecapPacket recap) {
        capturedFor = recap;
        model = null;
        if (recap == null || recap.killerModel == null) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        DeathRecapPacket.KillerModel m = recap.killerModel;

        Entity live = level.getEntity(m.entityId());
        if (live instanceof LivingEntity living && living.isAlive()
                && m.type().equals(ForgeRegistries.ENTITY_TYPES.getKey(living.getType()))) {
            model = living;
            return;
        }
        try {
            model = rebuild(level, m);
        } catch (RuntimeException | LinkageError e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Couldn't rebuild the killer's model", e);
        }
    }

    @Nullable
    private static LivingEntity rebuild(ClientLevel level, DeathRecapPacket.KillerModel m) {
        LivingEntity entity;
        if (m.profileId() != null) {
            // A real player: same UUID, so the skin comes from the tab list like theirs does.
            entity = new RemotePlayer(level, new GameProfile(m.profileId(), m.profileName())) {
                {
                    // Every skin layer on until their synced data (below) says otherwise.
                    getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
                }
            };
        } else {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(m.type());
            Entity created = type == null ? null : type.create(level);
            if (!(created instanceof LivingEntity living)) return null;
            entity = living;
        }

        // Same order a client sees an entity in: saved state, then spawn data, then synced
        // data, then equipment. Each step is optional - a mod that chokes on one (say, NBT
        // loading that expects a server) still gets the rest.
        if (m.nbt() != null) {
            try {
                entity.load(m.nbt().copy());
            } catch (RuntimeException e) {
                DayzHudMod.LOGGER.debug("[dayzhud] Killer's NBT didn't load on the client", e);
            }
        }
        if (m.spawnData().length > 0 && entity instanceof IEntityAdditionalSpawnData spawn) {
            try {
                spawn.readSpawnData(new FriendlyByteBuf(Unpooled.wrappedBuffer(m.spawnData())));
            } catch (RuntimeException e) {
                DayzHudMod.LOGGER.debug("[dayzhud] Killer's spawn data didn't load", e);
            }
        }
        if (m.syncedData().length > 0) {
            try {
                var values = new ClientboundSetEntityDataPacket(
                        new FriendlyByteBuf(Unpooled.wrappedBuffer(m.syncedData()))).packedItems();
                entity.getEntityData().assignValues(values);
            } catch (RuntimeException e) {
                DayzHudMod.LOGGER.debug("[dayzhud] Killer's entity data didn't load", e);
            }
        }
        EquipmentSlot[] slots = EquipmentSlot.values();
        for (int i = 0; i < slots.length && i < m.gear().size(); i++) {
            entity.setItemSlot(slots[i], m.gear().get(i));
        }
        // A picture of them standing, whatever state the NBT caught them in.
        entity.deathTime = 0;
        entity.hurtTime = 0;
        return entity;
    }
}
