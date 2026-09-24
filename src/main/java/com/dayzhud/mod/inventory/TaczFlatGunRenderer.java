package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.grid.GridConfig;
import com.dayzhud.mod.market.TaczMarketCompat;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Draws a TACZ gun as its real 3D model, side-on, barrel pointing left, contained (never
 * stretched) inside a box - the look of a gun lying in a Tarkov-style grid.
 *
 * <h2>How it gets at the model (every call below verified against TACZ 1.1.8's bytecode)</h2>
 * In GUI slots TACZ deliberately draws only a small diagonal 64x64 sprite, never the model,
 * so the model has to be drawn directly: {@code TimelessAPI.getGunDisplay(stack)} ->
 * {@code GunDisplayInstance.getGunModel()} / {@code getModelTexture()} ->
 * {@code BedrockGunModel.render(PoseStack, ItemStack, ItemDisplayContext, RenderType, int, int)}.
 * TACZ still does all the model work with its own code (attachments included); this only
 * supplies the pose. All TACZ calls go through reflection, same as TaczMarketCompat, so TACZ
 * stays an optional dependency.
 *
 * <h2>Why 2.12.0 silently drew nothing</h2>
 * It asked the item renderer to draw in FIXED mode and measured the result by passing a
 * recording MultiBufferSource. But TACZ's BedrockModel.render ignores any buffer you hand it:
 * it takes Minecraft's global {@code renderBuffers().bufferSource()} itself and flushes it
 * itself. The recorder never saw a vertex, the measurement came back empty, and every gun
 * quietly fell back to the plain sprite. One level down, though,
 * {@code BedrockPart.render(PoseStack, ItemDisplayContext, VertexConsumer, int, int)} DOES
 * take a consumer, and {@code BedrockModel.getShouldRender()} is the exact list of top-level
 * parts the model draws - so measuring now walks those parts into the recorder directly. An
 * empty measurement also logs a warning now instead of failing silently.
 *
 * <h2>Orientation: read from the geometry, not from TACZ's conventions</h2>
 * From the measured vertices: the gun's length axis is the longer of X and Z; the muzzle is
 * the thinner end along it (barrels are thin, stocks and grips are tall); "up" is the side
 * the muzzle sits on vertically (the bore runs along the top of a gun - grips, magazines and
 * stock drops hang below it). Three rotations then put the muzzle left and the top up. All
 * are proper rotations, never mirrors, so the gun's real side faces out. Can mis-guess on a
 * gun equally thick at both ends (a plain launcher tube) - that only flips its facing.
 *
 * <h2>Failure mode</h2>
 * Any exception latches {@link #broken} and every caller falls back to the plain item render
 * for the rest of the session, with one warning in the log.
 */
public final class TaczFlatGunRenderer {

    private static final int FULL_BRIGHT = 15728880;
    /** Fraction of the model's length, at each end, sampled to decide which end is the muzzle. */
    private static final float END_BAND = 0.15f;

    /** Model-space extent plus the orientation decisions derived from it. */
    private record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ,
                          boolean lengthAlongZ, boolean muzzleAtMax, boolean upIsNegativeY) {
        float length() { return lengthAlongZ ? maxZ - minZ : maxX - minX; }
        float height() { return maxY - minY; }
    }

    private static final Map<String, Bounds> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bounds> eldest) {
            return size() > 256;
        }
    };

    private static boolean broken;
    private static boolean reflectionReady;
    private static Method getGunDisplay, getGunModel, getModelTexture, getShouldRender, partRender;
    private static final Map<Class<?>, Method> MODEL_RENDER = new java.util.HashMap<>();
    private static final java.util.Set<String> WARNED_EMPTY = new java.util.HashSet<>();

    private TaczFlatGunRenderer() {}

    private static boolean initReflection() {
        if (reflectionReady) return true;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            getGunDisplay = api.getMethod("getGunDisplay", ItemStack.class);
            Class<?> display = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance");
            getGunModel = display.getMethod("getGunModel");
            getModelTexture = display.getMethod("getModelTexture");
            Class<?> bedrockModel = Class.forName("com.tacz.guns.client.model.bedrock.BedrockModel");
            getShouldRender = bedrockModel.getMethod("getShouldRender");
            Class<?> part = Class.forName("com.tacz.guns.client.model.bedrock.BedrockPart");
            partRender = part.getMethod("render", PoseStack.class, ItemDisplayContext.class,
                    VertexConsumer.class, int.class, int.class);
            reflectionReady = true;
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    private record Model(Object model, ResourceLocation texture) {}

    private static Model modelFor(ItemStack stack) throws Exception {
        Optional<?> display = (Optional<?>) getGunDisplay.invoke(null, stack);
        if (display.isEmpty()) return null;
        Object model = getGunModel.invoke(display.get());
        Object texture = getModelTexture.invoke(display.get());
        if (model == null || !(texture instanceof ResourceLocation tex)) return null;
        return new Model(model, tex);
    }

    private static Method modelRender(Class<?> modelClass) throws NoSuchMethodException {
        Method m = MODEL_RENDER.get(modelClass);
        if (m == null) {
            m = modelClass.getMethod("render", PoseStack.class, ItemStack.class,
                    ItemDisplayContext.class, RenderType.class, int.class, int.class);
            MODEL_RENDER.put(modelClass, m);
        }
        return m;
    }

    /** Whether {@link #render} will draw this stack - measures it (once) if needed. */
    public static boolean canRender(ItemStack stack) {
        if (broken || !GridConfig.FLAT_GUN_RENDER.get()) return false;
        if (TaczMarketCompat.gunIdOf(stack).isEmpty()) return false;
        if (!initReflection()) return false;
        Bounds b = bounds(stack);
        return b != null && b.length() > 1e-4f && b.height() > 1e-4f;
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
        float s = Math.min((w - pad * 2) / b.length(), (h - pad * 2) / b.height());

        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            Model m = modelFor(stack);
            if (m == null) return false;

            pose.translate(x + w / 2f, y + h / 2f, z);
            // View space from here on: +X screen-right, +Y screen-up (the Y flip, like
            // vanilla's own GUI item render, turns model-up into screen-up).
            pose.scale(s, -s, s);
            // Rotations listed outermost first; each vertex sees them innermost first:
            // (1) roll 180 about the length axis if the gun came out upside down,
            // (2) turn a Z-length model so its length lies along X,
            // (3) turn 180 about vertical if the muzzle is now on the right.
            // (1) doesn't move the muzzle along the length axis and (2) maps +Z to +X, so
            // "muzzle at the max end" is still the right test for (3).
            if (b.muzzleAtMax()) pose.mulPose(Axis.YP.rotationDegrees(180f));
            if (b.lengthAlongZ()) pose.mulPose(Axis.YP.rotationDegrees(90f));
            if (b.upIsNegativeY()) {
                pose.mulPose((b.lengthAlongZ() ? Axis.ZP : Axis.XP).rotationDegrees(180f));
            }
            pose.translate(-(b.minX() + b.maxX()) / 2f, -(b.minY() + b.maxY()) / 2f,
                    -(b.minZ() + b.maxZ()) / 2f);

            // Flat-item lighting lights faces pointing at the viewer - the gun's side, here.
            // The default 3D-item lighting comes mostly from above and leaves it dark.
            Lighting.setupForFlatItems();
            // TACZ draws into Minecraft's global buffer and flushes it itself, so make sure
            // anything already queued in the GUI (the panel behind the gun) goes out first.
            graphics.flush();
            modelRender(m.model().getClass()).invoke(m.model(), pose, stack, ItemDisplayContext.FIXED,
                    RenderType.entityCutoutNoCull(m.texture()), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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
            Model m = modelFor(stack);
            if (m == null) return null;
            Recorder rec = new Recorder();
            PoseStack ps = new PoseStack();
            for (Object part : (List<?>) getShouldRender.invoke(m.model())) {
                partRender.invoke(part, ps, ItemDisplayContext.FIXED, rec, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            Bounds b = rec.toBounds();
            if (b == null) {
                if (WARNED_EMPTY.add(key)) {
                    DayzHudMod.LOGGER.warn("dayzhud: measured no geometry for gun {}; drawing it "
                            + "as a normal item instead.", key);
                }
                return null;
            }
            CACHE.put(key, b);
            if (GridConfig.DEBUG_LOGGING.get()) {
                DayzHudMod.LOGGER.info("flat gun: {} -> {} verts, length {} along {}, height {}, "
                                + "muzzle at {} end, up is {}Y",
                        key, rec.n, b.length(), b.lengthAlongZ() ? "Z" : "X", b.height(),
                        b.muzzleAtMax() ? "max" : "min", b.upIsNegativeY() ? "-" : "+");
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
            boolean alongZ = (maxZ - minZ) > (maxX - minX);
            float[] len = alongZ ? zs : xs;
            float lo = alongZ ? minZ : minX, hi = alongZ ? maxZ : maxX;
            float band = (hi - lo) * END_BAND;

            float loMin = Float.MAX_VALUE, loMax = -Float.MAX_VALUE, hiMin = Float.MAX_VALUE, hiMax = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (len[i] <= lo + band) { loMin = Math.min(loMin, ys[i]); loMax = Math.max(loMax, ys[i]); }
                if (len[i] >= hi - band) { hiMin = Math.min(hiMin, ys[i]); hiMax = Math.max(hiMax, ys[i]); }
            }
            // Muzzle = the thinner end.
            boolean muzzleAtMax = (hiMax - hiMin) < (loMax - loMin);
            // The bore runs along the top: if the muzzle's vertical centre is below the
            // model's middle, the model is upside down in this space.
            float muzzleCentre = muzzleAtMax ? (hiMin + hiMax) / 2f : (loMin + loMax) / 2f;
            boolean upNegative = muzzleCentre < (minY + maxY) / 2f;
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, alongZ, muzzleAtMax, upNegative);
        }
    }
}
