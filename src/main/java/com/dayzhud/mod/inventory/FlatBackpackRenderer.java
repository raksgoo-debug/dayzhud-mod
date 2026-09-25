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
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Draws a backpack in the grid as its real 3D model, front-on (pockets toward you), filling
 * and centred in its footprint - the backpack counterpart of TaczFlatGunRenderer (2.13.6).
 * Only the bags listed in DefaultItemFootprints.BACKPACKS; everything else keeps the normal
 * item render.
 *
 * <ul>
 *   <li><b>fieldkit</b> bags' item models ARE the 3D model, built at worn scale with the
 *       pockets facing -z (see fieldkit's BackpackCurioRenderer). Drawn through the item
 *       renderer with no display transform, turned 180 about Y.</li>
 *   <li><b>CAPS AWIM</b> bags are a flat icon as an item; the 3D model only exists in their
 *       worn (Curios) renderer. Each bag {@code <id>} has a renderer class
 *       {@code ...client.renderer.<Id>Renderer} whose constructor bakes its model and which
 *       keeps it in a field, with its texture in a static field - both read reflectively
 *       (CAPS stays optional). Entity models are y-down with the bag on the wearer's back
 *       (+z), so turned 180 about Z. The renderer's own worn scale is left out: the bag is
 *       fitted to its box anyway.</li>
 * </ul>
 *
 * Sized and centred by what's visible, via PixelProbe, measured once per bag.
 */
public final class FlatBackpackRenderer {

    private static final int FULL_BRIGHT = 15728880;
    private static final String CAPS = "caps_awim_tactical_gear_rework";
    private static final String CAPS_RENDERERS = "net.mcreator.capsawimtacticalgearrework.client.renderer.";
    /** Probe px per block: frames +-4 x +-2 blocks, plenty for a bag of ~1.4 blocks. */
    private static final float PROBE_SCALE = 128f;

    private record CapsModel(Model model, ResourceLocation texture) {}

    private static final Map<String, CapsModel> CAPS_MODELS = new HashMap<>();
    private static final Map<String, PixelProbe.Box> VISIBLE = new HashMap<>();
    /** Bags that failed once - drawn as normal items for the rest of the session. */
    private static final Set<String> BROKEN = new HashSet<>();

    private FlatBackpackRenderer() {}

    private static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    public static boolean canRender(ItemStack stack) {
        if (stack.isEmpty() || !GridConfig.FLAT_GUN_RENDER.get()) return false;
        String id = idOf(stack);
        return id != null && DefaultItemFootprints.BACKPACKS.contains(id) && !BROKEN.contains(id);
    }

    /**
     * Draws the bag inside the box ({@code x},{@code y},{@code w},{@code h}) at GUI depth
     * {@code z}; rotated (R in the grid) turns it a quarter so it lies on its side. False if it
     * couldn't, having drawn nothing (the caller falls back to the normal render).
     */
    public static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, float z,
                                 boolean rotated) {
        if (!canRender(stack)) return false;
        String id = idOf(stack);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            Lighting.setupForFlatItems();
            graphics.flush();
            PixelProbe.Box v = VISIBLE.get(id);
            if (v == null) {
                v = PixelProbe.measure(PROBE_SCALE, p -> draw(p, stack, id), id);
                if (v == null) throw new IllegalStateException("drew nothing");
                VISIBLE.put(id, v);
                if (GridConfig.DEBUG_LOGGING.get()) {
                    DayzHudMod.LOGGER.info("flat backpack {}: visible {} x {} blocks, offset ({}, {})", id,
                            v.length(), v.height(), v.dx(), v.dy());
                }
            }
            float s = rotated ? Math.min(w / v.height(), h / v.length()) : Math.min(w / v.length(), h / v.height());
            pose.translate(x + w / 2f, y + h / 2f, z);
            if (rotated) pose.mulPose(Axis.ZP.rotationDegrees(90f));
            pose.scale(s, -s, s);                    // view space: +x right, +y up, +z toward you
            pose.translate(-v.dx(), -v.dy(), 0f);
            draw(pose, stack, id);
            graphics.flush();
            return true;
        } catch (Throwable t) {
            if (BROKEN.add(id)) {
                DayzHudMod.LOGGER.warn("dayzhud: couldn't draw backpack {} as a model; showing its normal "
                        + "icon instead.", id, t);
            }
            return false;
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    /** The bag's model in view space (+y up, pockets toward +z), in block units. */
    private static void draw(PoseStack pose, ItemStack stack, String id) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            if (id.startsWith(CAPS + ":")) {
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
