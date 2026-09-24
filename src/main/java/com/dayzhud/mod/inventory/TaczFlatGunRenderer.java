package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.grid.GridConfig;
import com.dayzhud.mod.market.TaczMarketCompat;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws a TACZ gun as its real 3D model, side-on, barrel pointing left, contained (never
 * stretched) inside a box - the look of a gun lying in a Tarkov-style grid.
 *
 * <h2>Why FIXED, and why TACZ is never called directly</h2>
 * Verified by disassembling TACZ 1.1.8's {@code GunItemRendererWrapper}: in the GUI context
 * TACZ deliberately draws only a small diagonal 64x64 "slot" sprite, never the model - which
 * is why stretching or tilting the normal inventory icon could never produce a side view. In
 * the {@code FIXED} context (item frames) it renders the real model, posed by the model's own
 * {@code fixed} bone (rotated 90 degrees about Y in the model files - i.e. side-on) and the
 * per-gun "fixed" scale from its display file. So this just asks Minecraft to draw the stack
 * in {@code FIXED} mode inside our own pose; TACZ does all the model work with its own code,
 * attachments included. No reflection into TACZ at all, so no TACZ-version coupling here.
 *
 * <h2>Fitting it into the box: measured, not guessed</h2>
 * Every gun model is a different size, so there is no correct constant scale. The first time
 * a given gun is drawn it is rendered once into {@link Recorder} - a VertexConsumer that
 * stores vertex positions instead of drawing - to get the model's real extent. That is
 * cached (keyed on gun id + NBT, since attachments change the silhouette) and used to scale
 * uniformly and centre every later draw.
 *
 * <h2>Which way the barrel points: also measured</h2>
 * Deriving the final facing from TACZ's bone-rotation sign conventions in bytecode would be a
 * guess stacked on a guess. Instead: a gun's muzzle end is thin and its stock/grip end is
 * tall, so the measured vertices at each end of the model's length are compared, and the gun
 * is turned 180 degrees about the vertical axis if the muzzle came out on the right. A turn,
 * not a mirror - so details like the ejection port stay on their real side. Can mis-guess on a
 * gun that is equally tall at both ends (a plain tube launcher); that only flips its facing.
 *
 * <h2>Failure mode</h2>
 * Any exception latches {@link #broken} and every caller falls back to the plain item render
 * for the rest of the session, with one warning in the log - a cosmetic feature must never be
 * what breaks the inventory screen.
 */
public final class TaczFlatGunRenderer {

    private static final int FULL_BRIGHT = 15728880;
    /** Fraction of the model's length, at each end, sampled to decide which end is the muzzle. */
    private static final float END_BAND = 0.15f;

    private record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ,
                          boolean muzzleRight) {
        float width() { return maxX - minX; }
        float height() { return maxY - minY; }
    }

    private static final Map<String, Bounds> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bounds> eldest) {
            return size() > 256;
        }
    };

    private static boolean broken;

    private TaczFlatGunRenderer() {}

    /** Whether {@link #render} will draw this stack - measures it (once) if needed. */
    public static boolean canRender(ItemStack stack) {
        if (broken || !GridConfig.FLAT_GUN_RENDER.get()) return false;
        if (TaczMarketCompat.gunIdOf(stack).isEmpty()) return false;
        Bounds b = bounds(stack);
        return b != null && b.width() > 1e-4f && b.height() > 1e-4f;
    }

    /**
     * Draws the gun side-on inside the box ({@code x},{@code y},{@code w},{@code h}), at GUI
     * depth {@code z}. Returns false (drawing nothing) if it can't; call {@link #canRender}
     * first so the caller can prepare the box only when this will succeed.
     */
    public static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, float z) {
        if (!canRender(stack)) return false;
        Bounds b = bounds(stack);
        int pad = 2;
        float s = Math.min((w - pad * 2) / b.width(), (h - pad * 2) / b.height());

        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + w / 2f, y + h / 2f, z);
            // Y flipped, like vanilla's own GUI item render: model space is y-up, the screen
            // is y-down; the GUI projection makes the combined handedness come out right.
            pose.scale(s, -s, s);
            if (b.muzzleRight()) {
                pose.mulPose(Axis.YP.rotationDegrees(180f));
            }
            pose.translate(-(b.minX() + b.maxX()) / 2f, -(b.minY() + b.maxY()) / 2f,
                    -(b.minZ() + b.maxZ()) / 2f);

            // Flat-item lighting lights faces pointing at the viewer - the gun's side, here.
            // The default 3D-item lighting lights mostly from above and leaves a side-on
            // model dark. Restored below either way, since that's what vanilla leaves set.
            Lighting.setupForFlatItems();
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, graphics.bufferSource(), mc.level, 0);
            graphics.flush();
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    private static String cacheKey(ItemStack stack) {
        String id = TaczMarketCompat.gunIdOf(stack).map(Object::toString).orElse("?");
        return stack.hasTag() ? id + "#" + stack.getTag().hashCode() : id;
    }

    private static Bounds bounds(ItemStack stack) {
        String key = cacheKey(stack);
        Bounds cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            Recorder rec = new Recorder();
            MultiBufferSource capture = renderType -> rec;
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, new PoseStack(), capture, mc.level, 0);
            Bounds b = rec.toBounds();
            if (b == null) return null;
            CACHE.put(key, b);
            if (GridConfig.DEBUG_LOGGING.get()) {
                DayzHudMod.LOGGER.info("flat gun: measured {} -> {}x{}x{} model units, muzzle {}",
                        key, b.width(), b.height(), b.maxZ() - b.minZ(),
                        b.muzzleRight() ? "right (turning 180)" : "left");
            }
            return b;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    private static void fail(Throwable t) {
        if (!broken) {
            broken = true;
            DayzHudMod.LOGGER.warn("dayzhud: flat gun rendering disabled for this session after "
                    + "an error; guns fall back to the normal item render.", t);
        }
    }

    /**
     * Stores vertex positions instead of drawing them. Positions arrive already transformed by
     * the PoseStack passed to the render call, which is what is measured.
     *
     * Deliberately no {@code @Override} on any method: if this list has one that the 1.20.1
     * interface doesn't, it's just an unused method; if it's MISSING one the interface
     * requires, compilation fails loudly - never a silent wrong-behaviour case either way.
     */
    private static final class Recorder implements VertexConsumer {
        private float[] xs = new float[4096], ys = new float[4096], zs = new float[4096];
        private int n;

        public VertexConsumer vertex(double x, double y, double z) {
            if (n == xs.length) {
                xs = Arrays.copyOf(xs, n * 2);
                ys = Arrays.copyOf(ys, n * 2);
                zs = Arrays.copyOf(zs, n * 2);
            }
            xs[n] = (float) x;
            ys[n] = (float) y;
            zs[n] = (float) z;
            n++;
            return this;
        }

        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}

        Bounds toBounds() {
            if (n == 0) return null;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE,
                    maxY = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
                minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
            }
            float band = (maxX - minX) * END_BAND;
            float lMin = Float.MAX_VALUE, lMax = -Float.MAX_VALUE, rMin = Float.MAX_VALUE, rMax = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (xs[i] <= minX + band) { lMin = Math.min(lMin, ys[i]); lMax = Math.max(lMax, ys[i]); }
                if (xs[i] >= maxX - band) { rMin = Math.min(rMin, ys[i]); rMax = Math.max(rMax, ys[i]); }
            }
            float leftSpread = lMax - lMin, rightSpread = rMax - rMin;
            // Muzzle = the thinner end. Screen x follows model x in render()'s pose, so a
            // thin RIGHT end means the barrel would point right: turn it around.
            boolean muzzleRight = rightSpread < leftSpread;
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, muzzleRight);
        }
    }
}
