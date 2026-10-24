package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.grid.DefaultItemFootprints;
import com.dayzhud.mod.inventory.grid.GridConfig;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Draws non-gun items in the grid as their real 3D model, filling and centred in their
 * footprint - the counterpart of TaczFlatGunRenderer for:
 *
 * <ul>
 *   <li><b>Backpacks</b> (DefaultItemFootprints.BACKPACKS), front-on, pockets toward you.
 *       fieldkit bags' item models ARE the 3D model, built at worn scale with the pockets at
 *       -z (fieldkit's BackpackCurioRenderer): drawn through the item renderer with no display
 *       transform, turned 180 about Y. CAPS AWIM bags are a flat icon as an item; the 3D model
 *       only exists in their worn (Curios) renderer. Each bag {@code <id>} has a renderer class
 *       {@code ...client.renderer.<Id>Renderer} whose constructor bakes its model and keeps it
 *       in a field, with its texture in a static field - both read reflectively (CAPS stays
 *       optional). Entity models are y-down with the bag on the wearer's back (+z), so turned
 *       180 about Z. (2.13.6)</li>
 *   <li><b>TaCZ: Magazines</b>, side-on like the gun they come from, standing up (2.13.7).
 *       Its renderer draws each magazine from its gun's TACZ model; in the GUI it uses a tilted
 *       3/4 view that turns into a thin diagonal sliver when the grid scales it up. In every
 *       other context (verified in its applyDisplayTransform bytecode) it draws the magazine in
 *       the gun model's own axes, y flipped up - so FIXED, turned 90 about Y, is exactly the
 *       guns' side-on pose. Reached through Forge's IClientItemExtensions, no reflection.</li>
 *   <li><b>Field Kit tools and melee</b> (DefaultItemFootprints.SIDE_ON), side-on, head to the
 *       left (2.18.0). Their item models are the 3D model, upright in block space.</li>
 *   <li><b>Rig &amp; Plate gear</b>, front-on as worn, attachments included (2.18.0) - see
 *       rigModel.</li>
 * </ul>
 *
 * Sized and centred by what's visible, via PixelProbe, measured once per item (per NBT for
 * magazines - each gun's magazine is a different model).
 */
public final class FlatModelRenderer {

    private static final int FULL_BRIGHT = 15728880;
    private static final float DEPTH_SCALE = 0.25f;
    private static final String CAPS = "caps_awim_tactical_gear_rework";
    private static final String CAPS_RENDERERS = "net.mcreator.capsawimtacticalgearrework.client.renderer.";
    public static final Set<String> MAGAZINES = Set.of("taczmagazines:magazine", "taczmagazines:magazine_small");

    private record CapsModel(Model model, ResourceLocation texture) {}

    private static final Map<String, CapsModel> CAPS_MODELS = new HashMap<>();
    private static final Map<String, PixelProbe.Box> VISIBLE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PixelProbe.Box> eldest) {
            return size() > 256;
        }
    };
    /** Items that failed once - drawn normally for the rest of the session. */
    private static final Set<String> BROKEN = new HashSet<>();

    private FlatModelRenderer() {}

    private static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    private static boolean handles(ItemStack stack, String id) {
        return id != null && (DefaultItemFootprints.BACKPACKS.contains(id) || MAGAZINES.contains(id)
                || DefaultItemFootprints.SIDE_ON.contains(id) || isRigGear(stack, id));
    }

    public static boolean canRender(ItemStack stack) {
        if (stack.isEmpty() || !GridConfig.FLAT_GUN_RENDER.get()) return false;
        String id = idOf(stack);
        return handles(stack, id) && !BROKEN.contains(id);
    }

    // ---- Rig & Plate (2.18.0) -------------------------------------------------------------
    //
    // Its gear is GeckoLib, and as an item it is only a flat sprite: the 3D model exists in its
    // worn renderers. Helmets and vests are ArmorItems whose IClientItemExtensions hand back that
    // renderer, already prepared, from getHumanoidArmorModel (checked in GearArmorItem$1's
    // bytecode). Rigs, masks and the headset are curios: CurioGearRenderer keeps the same
    // GearRenderer in its "renderer" field and prepares it itself with
    // prepForRender(entity, stack, CHEST for a rig / HEAD otherwise, base model) - done here the
    // same way, reflectively, so neither Rig & Plate nor GeckoLib is a compile dependency.
    // GeoArmorRenderer.renderToBuffer ignores the buffer it's given and draws through the shared
    // buffer source, undoing its own y-up flip, so the result is vanilla model space: y down,
    // the wearer's front at -z. Fitted attachments (visors, NVGs) show, as worn.

    private static final String RIG = "rigandplate:";
    private static final Map<String, Boolean> RIG_GEAR = new HashMap<>();
    private static net.minecraft.client.model.HumanoidModel<net.minecraft.world.entity.LivingEntity> baseModel;

    private static boolean isRigGear(ItemStack stack, String id) {
        if (!id.startsWith(RIG)) return false;
        return RIG_GEAR.computeIfAbsent(id, k -> stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                || top.theillusivec4.curios.api.client.CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent());
    }

    /** Rig & Plate's worn model for this stack, prepared on the local player. */
    private static Model rigModel(ItemStack stack) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) throw new IllegalStateException("no player to wear it");
        if (baseModel == null) {
            baseModel = new net.minecraft.client.model.HumanoidModel<>(
                    mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR));
        }
        if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
            Model m = IClientItemExtensions.of(stack).getHumanoidArmorModel(mc.player, stack,
                    armor.getEquipmentSlot(), baseModel);
            if (m == baseModel) throw new IllegalStateException("no worn model");
            return m;
        }
        Object curioRenderer = top.theillusivec4.curios.api.client.CuriosRendererRegistry.getRenderer(stack.getItem())
                .orElseThrow(() -> new IllegalStateException("no curio renderer"));
        Field f = curioRenderer.getClass().getDeclaredField("renderer");
        f.setAccessible(true);
        Object renderer = f.get(curioRenderer);
        // GearCurioItem.gear.kind(): RIG is worn on the body, the rest on the head.
        Object gear = stack.getItem().getClass().getField("gear").get(stack.getItem());
        String kind = String.valueOf(gear.getClass().getMethod("kind").invoke(gear));
        net.minecraft.world.entity.EquipmentSlot slot = "RIG".equals(kind)
                ? net.minecraft.world.entity.EquipmentSlot.CHEST : net.minecraft.world.entity.EquipmentSlot.HEAD;
        java.lang.reflect.Method prep = null;
        for (Class<?> c = renderer.getClass(); c != null && prep == null; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("prepForRender") && m.getParameterCount() == 4) {
                    prep = m;
                    break;
                }
            }
        }
        if (prep == null) throw new IllegalStateException("no prepForRender on " + renderer.getClass().getName());
        prep.setAccessible(true);
        prep.invoke(renderer, mc.player, stack, slot, baseModel);
        return (Model) renderer;
    }

    private static String cacheKey(ItemStack stack, String id) {
        // Per NBT for magazines (one item, every gun's model) and Rig & Plate gear (fitted
        // attachments and a flipped visor change its outline).
        return (MAGAZINES.contains(id) || id.startsWith(RIG)) && stack.hasTag()
                ? id + "#" + stack.getTag().hashCode() : id;
    }

    /**
     * Draws the item inside the box ({@code x},{@code y},{@code w},{@code h}) at GUI depth
     * {@code z}; rotated (R in the grid) turns it a quarter. False if it couldn't, having drawn
     * nothing (the caller falls back to the normal render).
     */
    public static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, float z,
                                 boolean rotated) {
        if (!canRender(stack)) return false;
        String id = idOf(stack);
        String key = cacheKey(stack, id);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            Lighting.setupForFlatItems();
            graphics.flush();
            PixelProbe.Box v = VISIBLE.get(key);
            if (v == null) {
                // Probe px per block: bags are ~1.4 blocks (frame +-4 x +-2), magazines are
                // fitted to 0.5 by their own renderer (frame +-2 x +-1), tools are one block
                // long laid flat, the spear 1.5 (+-2 x +-1). Rig & Plate gear is drawn where it's
                // worn, relative to the neck: a vest's groin flap or a rig's low pouches reach
                // more than a block below it, so it gets the bags' larger frame (2.19.0) - in a
                // +-1 frame they were cut off, measured short, and hung out of their cells.
                float sp = DefaultItemFootprints.BACKPACKS.contains(id) || id.startsWith(RIG) ? 128f : 256f;
                v = PixelProbe.measure(sp, p -> draw(p, stack, id), key);
                // Anything still touching the frame's edge didn't fit: measure again with the
                // frame doubled, twice at most.
                for (int retry = 0; v != null && v.clipped() && retry < 2; retry++) {
                    sp /= 2f;
                    v = PixelProbe.measure(sp, p -> draw(p, stack, id), key);
                }
                if (v == null) throw new IllegalStateException("drew nothing");
                VISIBLE.put(key, v);
                if (GridConfig.DEBUG_LOGGING.get()) {
                    DayzHudMod.LOGGER.info("flat model {}: visible {} x {} blocks, offset ({}, {})", key,
                            v.length(), v.height(), v.dx(), v.dy());
                }
            }
            float s = rotated ? Math.min(w / v.height(), h / v.length()) : Math.min(w / v.length(), h / v.height());
            pose.translate(x + w / 2f, y + h / 2f, z);
            if (rotated) pose.mulPose(Axis.ZP.rotationDegrees(90f));
            // View space: +x right, +y up, +z toward you. Depth squashed to a quarter: the
            // probe centres x/y only, so a model sitting off-centre in depth (CAPS bags sit on
            // the wearer's back) would otherwise reach through the grid's hover/placement
            // layers, which are only a few units apart (2.13.8).
            pose.scale(s, -s, s * DEPTH_SCALE);
            pose.translate(-v.dx(), -v.dy(), 0f);
            draw(pose, stack, id);
            graphics.flush();
            return true;
        } catch (Throwable t) {
            if (BROKEN.add(id)) {
                DayzHudMod.LOGGER.warn("dayzhud: couldn't draw {} as a model; showing its normal icon instead.",
                        id, t);
            }
            return false;
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    /** The item's model in view space (+y up, facing +z), in block units. */
    private static void draw(PoseStack pose, ItemStack stack, String id) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            if (MAGAZINES.contains(id)) {
                // Gun-model axes (length along z, muzzle -z) -> +z to screen-right, as the guns.
                pose.mulPose(Axis.YP.rotationDegrees(90f));
                // Its FIXED transform starts with translate(0.5, 0.5, 0.5) (checked in its
                // applyDisplayTransform bytecode) - a half-block DEPTH offset once turned
                // side-on, which put a carried magazine ~30 units behind the grid's placement
                // preview, so it vanished over the grid (2.13.8). Cancelled exactly here.
                pose.translate(-0.5f, -0.5f, -0.5f);
                IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.FIXED,
                        pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } else if (DefaultItemFootprints.SIDE_ON.contains(id)) {
                // Upright in block space, head at +y: a quarter turn puts the head screen-left.
                pose.mulPose(Axis.ZP.rotationDegrees(90f));
                mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, mc.getItemRenderer().getModel(stack, mc.level, null, 0));
            } else if (id.startsWith(RIG)) {
                // Model space (y down, front at -z) -> view space, front toward you: a half turn
                // about X. Left stays left, so the wearer's left side is on screen-right.
                Model model = rigModel(stack);
                pose.mulPose(Axis.XP.rotationDegrees(180f));
                model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(
                                net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            } else if (id.startsWith(CAPS + ":")) {
                CapsModel cm = capsModel(id);
                pose.mulPose(Axis.ZP.rotationDegrees(180f));
                cm.model().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(cm.texture())),
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, mc.getItemRenderer().getModel(stack, mc.level, null, 0));
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    /** The model and texture CAPS's own worn renderer for this bag uses. */
    private static CapsModel capsModel(String id) throws Exception {
        CapsModel cached = CAPS_MODELS.get(id);
        if (cached != null) return cached;
        String path = id.substring(id.indexOf(':') + 1);
        Class<?> cls = Class.forName(CAPS_RENDERERS + Character.toUpperCase(path.charAt(0)) + path.substring(1)
                + "Renderer");
        Object renderer = cls.getDeclaredConstructor().newInstance();
        Model model = null;
        ResourceLocation texture = null;
        for (Field f : cls.getDeclaredFields()) {
            if (Model.class.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                model = (Model) f.get(renderer);
            } else if (f.getType() == ResourceLocation.class && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                texture = (ResourceLocation) f.get(null);
            }
        }
        if (model == null || texture == null) {
            throw new IllegalStateException(cls.getName() + " has no model/texture field");
        }
        CapsModel cm = new CapsModel(model, texture);
        CAPS_MODELS.put(id, cm);
        return cm;
    }
}
