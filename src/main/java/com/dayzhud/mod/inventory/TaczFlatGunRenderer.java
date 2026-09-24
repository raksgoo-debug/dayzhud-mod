package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.grid.GridConfig;
import com.dayzhud.mod.market.TaczMarketCompat;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.joml.Vector3f;

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
 * <h2>2.12.2: measuring the whole draw, and reading the real muzzle</h2>
 * Walking {@code getShouldRender()} measured the gun body but not its attachments - TACZ
 * draws stocks, scopes etc. as separate attachment models on top - so guns whose stock is a
 * (built-in) attachment measured too short and overflowed their box. Measurement now runs the
 * model's complete render with Minecraft's global buffer (the one TACZ insists on) briefly
 * pointed at the recorder, so it sees exactly what gets drawn. The muzzle end now comes from
 * the model's own muzzle-flash bone, walked exactly the way TACZ itself locates it
 * ({@code getMuzzleFlashPosPath()} + {@code translateAndRotateAndScale}, in list order); the
 * thin-end guess below is only the fallback for a model without that bone. Guns whose pack
 * ships only a low-detail model now fall back to it instead of not drawing at all.
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
    private static Method getGunDisplay, getGunModel, getModelTexture, getLodModel, getShouldRender,
            partRender, partTransform;
    private static Field globalBufferField;
    private static boolean globalBufferLookupDone;
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
            getLodModel = display.getMethod("getLodModel");
            Class<?> bedrockModel = Class.forName("com.tacz.guns.client.model.bedrock.BedrockModel");
            getShouldRender = bedrockModel.getMethod("getShouldRender");
            Class<?> part = Class.forName("com.tacz.guns.client.model.bedrock.BedrockPart");
            partRender = part.getMethod("render", PoseStack.class, ItemDisplayContext.class,
                    VertexConsumer.class, int.class, int.class);
            partTransform = part.getMethod("translateAndRotateAndScale", PoseStack.class);
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
        if (model != null && texture instanceof ResourceLocation tex) return new Model(model, tex);
        // Some packs ship only the low-detail model; TACZ's own renderer falls back the same way.
        Object lod = getLodModel.invoke(display.get());
        if (lod == null) return null;
        Object lodModel = lod.getClass().getMethod("getLeft").invoke(lod);
        Object lodTex = lod.getClass().getMethod("getRight").invoke(lod);
        if (lodModel == null || !(lodTex instanceof ResourceLocation tex)) return null;
        return new Model(lodModel, tex);
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
            boolean full = measureFullDraw(m, stack, rec);
            if (!full) {
                // Body only (misses attachment models like a separate stock), but better than nothing.
                PoseStack ps = new PoseStack();
                for (Object part : (List<?>) getShouldRender.invoke(m.model())) {
                    partRender.invoke(part, ps, ItemDisplayContext.FIXED, rec, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
            }
            Vector3f muzzle = muzzlePoint(m.model());
            Bounds b = rec.toBounds(muzzle);
            if (b == null) {
                if (WARNED_EMPTY.add(key)) {
                    DayzHudMod.LOGGER.warn("dayzhud: measured no geometry for gun {}; drawing it "
                            + "as a normal item instead.", key);
                }
                return null;
            }
            CACHE.put(key, b);
            if (GridConfig.DEBUG_LOGGING.get()) {
                DayzHudMod.LOGGER.info("flat gun: {} -> {} verts ({}), length {} along {}, height {}, "
                                + "muzzle at {} end ({}), up is {}Y",
                        key, rec.n, full ? "full draw" : "body only", b.length(), b.lengthAlongZ() ? "Z" : "X",
                        b.height(), b.muzzleAtMax() ? "max" : "min",
                        muzzle != null ? "muzzle bone" : "thin-end guess", b.upIsNegativeY() ? "-" : "+");
            }
            return b;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    /**
     * Runs the model's complete render (body AND attachment models) with Minecraft's global
     * buffer briefly replaced by one that feeds {@code rec}. TACZ fetches that buffer itself
     * rather than taking one as a parameter, so this is the only way to see everything it
     * draws. Render-thread only, restored in {@code finally}. Returns false (having recorded
     * nothing) if the swap isn't possible, so the caller can fall back.
     *
     * The field is found by identity - whichever {@code BufferSource}-typed field on
     * RenderBuffers currently holds {@code bufferSource()} - not by name, so obfuscated
     * field names don't matter. TACZ's render also toggles the stencil buffer (clear with
     * mask 1024, stencil ops GL_KEEP) - checked in bytecode; nothing else global.
     */
    private static boolean measureFullDraw(Model m, ItemStack stack, Recorder rec) {
        RenderBuffers buffers = Minecraft.getInstance().renderBuffers();
        MultiBufferSource.BufferSource real = buffers.bufferSource();
        Field f = globalBufferField(buffers, real);
        if (f == null) return false;
        MultiBufferSource.BufferSource capture = new MultiBufferSource.BufferSource(new BufferBuilder(256), Map.of()) {
            @Override
            public VertexConsumer getBuffer(RenderType type) {
                return rec;
            }

            @Override
            public void endBatch() {}

            @Override
            public void endBatch(RenderType type) {}
        };
        try {
            f.set(buffers, capture);
            modelRender(m.model().getClass()).invoke(m.model(), new PoseStack(), stack, ItemDisplayContext.FIXED,
                    RenderType.entityCutoutNoCull(m.texture()), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            return rec.n > 0;
        } catch (Throwable t) {
            DayzHudMod.LOGGER.debug("dayzhud: full-draw measurement failed, using body-only", t);
            return false;
        } finally {
            try {
                f.set(buffers, real);
            } catch (Throwable restore) {
                fail(restore);
            }
        }
    }

    private static Field globalBufferField(RenderBuffers buffers, Object current) {
        if (globalBufferLookupDone) return globalBufferField;
        globalBufferLookupDone = true;
        try {
            for (Field f : RenderBuffers.class.getDeclaredFields()) {
                if (!MultiBufferSource.BufferSource.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                if (f.get(buffers) == current) {
                    globalBufferField = f;
                    break;
                }
            }
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("dayzhud: can't reach the global render buffer; flat guns will be "
                    + "measured without their attachment models.", t);
            globalBufferField = null;
        }
        return globalBufferField;
    }

    /**
     * The muzzle-flash point in model space, located exactly the way TACZ's own
     * GunItemRendererWrapper.cacheMuzzlePosition does it (verified in bytecode): walk
     * getMuzzleFlashPosPath() in list order applying each part's translateAndRotateAndScale,
     * then read the resulting translation. Null if the model has no such bone.
     */
    private static Vector3f muzzlePoint(Object model) {
        try {
            Method pathGetter = model.getClass().getMethod("getMuzzleFlashPosPath");
            List<?> path = (List<?>) pathGetter.invoke(model);
            if (path == null || path.isEmpty()) return null;
            PoseStack ps = new PoseStack();
            for (Object part : path) partTransform.invoke(part, ps);
            return ps.last().pose().transformPosition(new Vector3f());
        } catch (Throwable t) {
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

        Bounds toBounds(Vector3f muzzle) {
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
            boolean muzzleAtMax;
            float muzzleCentre;
            if (muzzle != null) {
                // The model's own muzzle-flash point: authoritative.
                float along = alongZ ? muzzle.z() : muzzle.x();
                muzzleAtMax = along > (lo + hi) / 2f;
                muzzleCentre = muzzle.y();
            } else {
                // Fallback guess: the muzzle is the thinner end.
                muzzleAtMax = (hiMax - hiMin) < (loMax - loMin);
                muzzleCentre = muzzleAtMax ? (hiMin + hiMax) / 2f : (loMin + loMax) / 2f;
            }
            // The bore runs along the top: if the muzzle sits below the model's middle, the
            // model is upside down in this space.
            boolean upNegative = muzzleCentre < (minY + maxY) / 2f;
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, alongZ, muzzleAtMax, upNegative);
        }
    }
}
