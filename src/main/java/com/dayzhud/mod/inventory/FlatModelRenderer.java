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
 * </ul>
 *
 * Sized and centred by what's visible, via PixelProbe, measured once per item (per NBT for
 * magazines - each gun's magazine is a different model).
 */
public final class FlatModelRenderer {

    private static final int FULL_BRIGHT = 15728880;
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

    private static boolean handles(String id) {
        return id != null && (DefaultItemFootprints.BACKPACKS.contains(id) || MAGAZINES.contains(id));
    }

    public static boolean canRender(ItemStack stack) {
        if (stack.isEmpty() || !GridConfig.FLAT_GUN_RENDER.get()) return false;
        String id = idOf(stack);
        return handles(id) && !BROKEN.contains(id);
    }

    private static String cacheKey(ItemStack stack, String id) {
        return MAGAZINES.contains(id) && stack.hasTag() ? id + "#" + stack.getTag().hashCode() : id;
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
                // fitted to 0.5 by their own renderer (frame +-2 x +-1).
                float sp = MAGAZINES.contains(id) ? 256f : 128f;
                v = PixelProbe.measure(sp, p -> draw(p, stack, id), key);
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
            pose.scale(s, -s, s);                    // view space: +x right, +y up, +z toward you
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
                IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.FIXED,
                        pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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
