package com.dayzhud.mod.client;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.recap.ClientDeathRecap;
import com.dayzhud.mod.recap.DeathRecapPacket;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
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

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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
    /** The texture the killer's renderer used when captured - the skin to keep showing. */
    @Nullable private static ResourceLocation skin;
    private static boolean loggedSkinSwap;

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
        skin = null;
        loggedSkinSwap = false;
        if (recap == null || recap.killerModel == null) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        DeathRecapPacket.KillerModel m = recap.killerModel;

        Entity live = level.getEntity(m.entityId());
        boolean captured = live instanceof LivingEntity living && living.isAlive()
                && m.type().equals(ForgeRegistries.ENTITY_TYPES.getKey(living.getType()));
        if (captured) {
            model = (LivingEntity) live;
        } else {
            try {
                model = rebuild(level, m);
            } catch (RuntimeException | LinkageError e) {
                DayzHudMod.LOGGER.warn("[dayzhud] Death screen: couldn't rebuild the killer ({})", m.type(), e);
            }
        }
        if (model == null) return;
        skin = textureOf(model);
        // One line per death, so a wrong skin can be traced to the mod that draws it.
        DayzHudMod.LOGGER.info("[dayzhud] Death screen: killer {} ({}, renderer {}) {}, skin {}",
                m.type(), model.getClass().getName(), rendererName(model),
                captured ? "captured live" : "rebuilt from the server's snapshot", skin);
    }

    // ---- drawing ------------------------------------------------------------------------

    /**
     * Draws the killer like InventoryScreen.renderEntityInInventoryFollowsAngle, but keeps
     * the skin they had when captured.
     *
     * When the client unloads an entity, Forge invalidates its capabilities, and NPC mods often
     * keep the skin in one (or in a client cache that's cleared on unload). The kept object
     * then renders with its renderer's fallback, usually Steve. So: the entity is marked
     * not-removed and its capabilities revived, and if its renderer still asks for a different
     * texture than the captured one, the captured one is swapped back in as it draws.
     */
    public static void draw(GuiGraphics g, LivingEntity entity, int x, int y, int scale, float lookX, float lookY) {
        if (entity.isRemoved()) {
            clearRemoved(entity);
            try {
                entity.reviveCaps();
            } catch (RuntimeException e) {
                DayzHudMod.LOGGER.debug("[dayzhud] Couldn't revive the killer's capabilities", e);
            }
        }
        ResourceLocation now = entity == model ? textureOf(entity) : null;
        ResourceLocation keep = entity == model ? skin : null;
        if (keep == null || now == null || keep.equals(now)) {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x, y, scale, lookX, lookY, entity);
            return;
        }
        if (!loggedSkinSwap) {
            loggedSkinSwap = true;
            DayzHudMod.LOGGER.info("[dayzhud] Death screen: the killer's skin changed from {} to {} after it "
                    + "unloaded - drawing it with {}", keep, now, keep);
        }
        renderWithSkin(g, x, y, scale, lookX, lookY, entity, now, keep);
    }

    /**
     * Entity's removal reason, found by type: Entity.unsetRemoved() would do this but is
     * protected, and the field's name differs between dev and release mappings while its
     * type doesn't. Null if it can't be found - then the entity just stays marked removed.
     */
    @Nullable private static Field removalReason;
    private static boolean removalReasonLooked;

    private static void clearRemoved(Entity entity) {
        if (!removalReasonLooked) {
            removalReasonLooked = true;
            for (Field f : Entity.class.getDeclaredFields()) {
                if (f.getType() == Entity.RemovalReason.class && !Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        removalReason = f;
                    } catch (RuntimeException e) {
                        DayzHudMod.LOGGER.debug("[dayzhud] Entity's removal reason isn't accessible", e);
                    }
                    break;
                }
            }
        }
        if (removalReason == null) return;
        try {
            removalReason.set(entity, null);
        } catch (IllegalAccessException | RuntimeException e) {
            DayzHudMod.LOGGER.debug("[dayzhud] Couldn't clear the killer's removed flag", e);
        }
    }

    @Nullable
    private static ResourceLocation textureOf(LivingEntity entity) {
        try {
            EntityRenderer<? super LivingEntity> renderer =
                    Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            return renderer.getTextureLocation(entity);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String rendererName(LivingEntity entity) {
        try {
            return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).getClass().getName();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /**
     * InventoryScreen.renderEntityInInventoryFollowsAngle / renderEntityInInventory (1.20.1),
     * with one change: the buffer source swaps {@code wrong} for {@code right} in every entity
     * render type that asks for it. Armour, held items and other layers use other textures
     * and pass through untouched.
     */
    private static void renderWithSkin(GuiGraphics g, int x, int y, int scale, float angleX, float angleY,
                                       LivingEntity entity, ResourceLocation wrong, ResourceLocation right) {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(angleY * 20.0F * ((float) Math.PI / 180F));
        pose.mul(camera);
        float bodyRot = entity.yBodyRot, yRot = entity.getYRot(), xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO, headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + angleX * 20.0F;
        entity.setYRot(180.0F + angleX * 40.0F);
        entity.setXRot(-angleY * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        g.pose().pushPose();
        g.pose().translate(x, y, 50.0D);
        g.pose().mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        g.pose().mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        camera.conjugate();
        dispatcher.overrideCameraOrientation(camera);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        String wrongName = wrong.toString();
        MultiBufferSource swapped = type -> buffers.getBuffer(swapSkin(type, wrongName, right));
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                    g.pose(), swapped, 15728880));
        } finally {
            g.flush();
            dispatcher.setRenderShadow(true);
            g.pose().popPose();
            Lighting.setupFor3DItems();
            entity.yBodyRot = bodyRot;
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.yHeadRotO = headRotO;
            entity.yHeadRot = headRot;
        }
    }

    /**
     * {@code type} rebuilt with {@code right} if it's one of vanilla's entity render types and
     * its texture is the wrong skin; otherwise {@code type} itself. Render types don't expose
     * their texture, but their toString names both the type and the texture.
     */
    private static RenderType swapSkin(RenderType type, String wrongName, ResourceLocation right) {
        String text = type.toString();
        if (!text.contains(wrongName) || !text.startsWith("RenderType[")) return type;
        int colon = text.indexOf(':');
        if (colon < 0) return type;
        return switch (text.substring("RenderType[".length(), colon)) {
            case "entity_cutout_no_cull" -> RenderType.entityCutoutNoCull(right);
            case "entity_cutout_no_cull_z_offset" -> RenderType.entityCutoutNoCullZOffset(right);
            case "entity_cutout" -> RenderType.entityCutout(right);
            case "entity_solid" -> RenderType.entitySolid(right);
            case "entity_translucent" -> RenderType.entityTranslucent(right);
            case "entity_translucent_cull" -> RenderType.entityTranslucentCull(right);
            case "entity_smooth_cutout" -> RenderType.entitySmoothCutout(right);
            case "item_entity_translucent_cull" -> RenderType.itemEntityTranslucentCull(right);
            default -> type;
        };
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
