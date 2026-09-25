package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.grid.GridConfig;
import com.dayzhud.mod.market.TaczMarketCompat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
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
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
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
 * <h2>2.12.2: measuring the whole draw, and reading the real muzzle</h2>
 * Walking {@code getShouldRender()} measured the gun body but not its attachments - TACZ
 * draws stocks, scopes etc. as separate attachment models on top - so guns whose stock is a
 * (built-in) attachment measured too short and overflowed their box. Measurement now runs the
 * model's complete render with Minecraft's global buffer (the one TACZ insists on) briefly
 * pointed at the recorder, so it sees exactly what gets drawn. Guns whose pack ships only a
 * low-detail model now fall back to it instead of not drawing at all. (2.12.2 also located
 * the muzzle from the muzzle-flash bone; superseded by the constant orientation below.)
 *
 * <h2>2.12.4: orientation is a constant, and the hand markers are hidden</h2>
 * Every TACZ gun model is authored the same way, and TACZ converts them all the same way on
 * load (verified in BedrockModel.convertPivot / convertOrigin bytecode: Y is flipped, X and Z
 * are kept - Minecraft's y-down model convention). So in model space every gun has its length
 * along Z, its muzzle at -Z (checked across all 52 default guns that have a muzzle bone) and
 * its top toward -Y. The pose is therefore fixed - roll 180 about the length axis, then turn
 * +Z onto screen-right - with no per-gun guessing. The earlier "muzzle is the thinner end" and
 * "muzzle sits above the middle" guesses got the AK and SCAR wrong respectively.
 *
 * Every model also carries two hand-position markers ({@code lefthand_pos} /
 * {@code righthand_pos}, under {@code leftHand} / {@code rightHand}): opaque 4x12 boxes that
 * reach far above the gun. TACZ hides them when it draws a gun, but not before our
 * measurement ran, so they inflated the measured height of 48 of 54 guns - up to 2.3x on
 * pistols - shrinking the drawn gun to fit a mostly-empty box and dragging the old "middle"
 * off-centre (which is what flipped the SCAR). They are now explicitly hidden, by bone name,
 * for both the measuring pass and the draw, and restored afterwards.
 *
 * <h2>2.13.4: sized and centred from what is actually visible</h2>
 * Every measurement before this counted vertices, and TACZ emits geometry you never see:
 * scope reticle and lens planes that only show through a stencil mask, transparent cubes, and
 * so on. That inflated box shrank guns well below their footprint (the AK drew at ~2/3 of its
 * 5x2) and pulled them off-centre (the M4 sat high, the scoped AUG low). The first time an item
 * is drawn, it is now also drawn once into a private off-screen buffer (with a stencil, like the
 * main one) and its box is read back from the pixels that ended up opaque. That box - not the
 * vertices - is what gets fitted and centred. It replaces the 2.13.1 per-frame vertex
 * self-centring, which chased the same inflated box.
 *
 * TACZ guns now also FILL their footprint (2 px in from the border); relative sizes come from
 * the footprint table, which was re-derived from the same visible bounds.
 *
 * <h2>2.13.5: attachments grow the box, not shrink the gun</h2>
 * A gun is drawn at the scale that fills its footprint WITHOUT attachments, always; the
 * footprint itself grows to hold what's fitted (GunSizes, decided on the server from the
 * gun's attachment NBT). Fitting into the box is only a fallback now, for guns and
 * attachments GunSizes has no data for.
 *
 * <h2>Failure mode</h2>
 * Any exception latches {@link #broken} and every caller falls back to the plain item render
 * for the rest of the session, with one warning in the log.
 */
public final class TaczFlatGunRenderer {

    private static final int FULL_BRIGHT = 15728880;

    /**
     * Model-space extent, plus how it maps to the flat view. For TACZ guns the orientation is a
     * constant (see class doc): length along Z, height along Y, {@code rot} null. For LR
     * Tactical items (melee, consumables, throwables) there is no shared convention, so
     * {@code rot} is a model-to-view rotation read from the geometry (see
     * Recorder.toBounds), and length/height are the extents along its horizontal/vertical.
     */
    private record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ,
                          float length, float height, float[] rot, float cx, float cy, float cz,
                          float unitScale) {
        /** unitScale: px per in-game model unit at the shared scale (2 px per default-model
         *  unit), or -1 to fill the box instead (pistols, and items with no reference length). */
        Bounds withUnitScale(float u) {
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, length, height, rot, cx, cy, cz, u);
        }
    }

    /** Shared render scale for LR Tactical items: 2 px per model unit = 9 units per 18-px cell,
     *  the scale their footprint table was sized at. */
    private static final float PX_PER_UNIT = 2f;

    /** GUI px between a grid item's box edge and the gun: 1 for the panel's own border, 1 of
     *  breathing room so a gun that fills its box doesn't touch the outline. */
    public static final int GRID_INSET = com.dayzhud.mod.inventory.grid.GunSizes.INSET;

    /**
     * The item as it really shows on screen, read from pixels (see {@link #measureVisible}): its
     * centre relative to the vertex-bounds centre, and its visible length/height - all in
     * view-space model units (+x screen-right, +y screen-up).
     */
    private record Visible(float dx, float dy, float length, float height) {}

    private static final Map<String, Visible> VISIBLE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Visible> eldest) {
            return size() > 256;
        }
    };

    /** Off-screen buffer the pixel measurement draws into; made on first use. The item is framed
     *  at half the buffer's size, so geometry the vertex pass missed still lands inside it. */
    private static TextureTarget probe;
    private static final int PROBE_W = 1024, PROBE_H = 512;
    /** A pixel counts as visible from this alpha up (cutout textures keep >= 0.1, i.e. 26). */
    private static final int PROBE_ALPHA_MIN = 8;

    private static final Map<String, Bounds> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bounds> eldest) {
            return size() > 256;
        }
    };

    private static boolean broken;
    private static boolean reflectionReady;
    private static Method getGunDisplay, getGunModel, getModelTexture, getLodModel, getShouldRender,
            partRender, plainModelRender;
    /** LrTacticalAPI.getMeleeDisplay / getConsumableDisplay / getThrowableDisplay, keyed by the
     *  NBT tag that marks each kind. Empty when LR Tactical isn't installed. */
    private static final Map<String, Method> LR_DISPLAY = new java.util.HashMap<>();
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
            // TACZ's model render without an ItemStack - what LR Tactical's models (a TACZ
            // BedrockAnimatedModel subclass) are drawn with. Verified public in TACZ 1.1.8.
            plainModelRender = bedrockModel.getMethod("render", PoseStack.class, ItemDisplayContext.class,
                    RenderType.class, int.class, int.class);
            try {
                Class<?> lr = Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI");
                LR_DISPLAY.put("MeleeWeaponId", lr.getMethod("getMeleeDisplay", ItemStack.class));
                LR_DISPLAY.put("ConsumableId", lr.getMethod("getConsumableDisplay", ItemStack.class));
                LR_DISPLAY.put("ThrowableId", lr.getMethod("getThrowableDisplay", ItemStack.class));
            } catch (Throwable absent) {
                // LR Tactical not installed - guns only.
            }
            reflectionReady = true;
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    private record Model(Object model, ResourceLocation texture, boolean gun) {}

    /** The LR Tactical NBT id tag this stack carries (MeleeWeaponId / ConsumableId /
     *  ThrowableId), or null if it isn't an LR Tactical item with a display. */
    static String lrTag(ItemStack stack) {
        if (!stack.hasTag()) return null;
        for (String tag : new String[]{"MeleeWeaponId", "ConsumableId", "ThrowableId"}) {
            if (stack.getTag().contains(tag)) return tag;
        }
        return null;
    }

    private static Model modelFor(ItemStack stack) throws Exception {
        if (TaczMarketCompat.gunIdOf(stack).isPresent()) {
            Optional<?> display = (Optional<?>) getGunDisplay.invoke(null, stack);
            if (display.isEmpty()) return null;
            Object model = getGunModel.invoke(display.get());
            Object texture = getModelTexture.invoke(display.get());
            if (model != null && texture instanceof ResourceLocation tex) return new Model(model, tex, true);
            // Some packs ship only the low-detail model; TACZ's own renderer falls back the same way.
            Object lod = getLodModel.invoke(display.get());
            if (lod == null) return null;
            Object lodModel = lod.getClass().getMethod("getLeft").invoke(lod);
            Object lodTex = lod.getClass().getMethod("getRight").invoke(lod);
            if (lodModel == null || !(lodTex instanceof ResourceLocation tex)) return null;
            return new Model(lodModel, tex, true);
        }
        String tag = lrTag(stack);
        Method getter = tag == null ? null : LR_DISPLAY.get(tag);
        if (getter == null) return null;
        Optional<?> display = (Optional<?>) getter.invoke(null, stack);
        if (display.isEmpty()) return null;
        Object d = display.get();
        Object model = d.getClass().getMethod("getModel").invoke(d);
        Object texture = d.getClass().getMethod("getTexture").invoke(d);
        if (model == null || !(texture instanceof ResourceLocation tex)) return null;
        return new Model(model, tex, false);
    }

    /** One call that draws the model the way its owner does: TACZ guns through
     *  BedrockGunModel.render (with the stack, for attachments), LR items through the plain
     *  BedrockModel.render. */
    private static void drawModel(Model m, PoseStack pose, ItemStack stack) throws Exception {
        RenderType type = RenderType.entityCutoutNoCull(m.texture());
        if (m.gun()) {
            modelRender(m.model().getClass()).invoke(m.model(), pose, stack, ItemDisplayContext.FIXED,
                    type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else {
            plainModelRender.invoke(m.model(), pose, ItemDisplayContext.FIXED, type, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
        }
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
        if (TaczMarketCompat.gunIdOf(stack).isEmpty() && lrTag(stack) == null) return false;
        if (!initReflection()) return false;
        Bounds b = bounds(stack);
        return b != null && b.length() > 1e-4f && b.height() > 1e-4f;
    }

    /**
     * Draws the gun side-on inside the box ({@code x},{@code y},{@code w},{@code h}), at GUI
     * depth {@code z}. Returns false (drawing nothing) if it can't; call {@link #canRender}
     * first so the caller can prepare the box only when this will succeed.
     */
    public static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, float z,
                                 boolean rotated) {
        return render(graphics, stack, x, y, w, h, z, rotated, -1f);
    }

    /**
     * The scale this item is drawn at in the grid, inside its own (unrotated) footprint -
     * pixels per model unit. The loadout boxes use this so equipping a gun doesn't make it
     * bigger or smaller than it looks in your inventory (2.12.6); a box only scales it down
     * further when the gun is genuinely longer than the box. -1 if it can't be measured.
     */
    public static float gridScale(ItemStack stack) {
        if (!canRender(stack)) return -1f;
        Bounds b = bounds(stack);
        // Visible size once the item has been drawn at least once; vertex size until then.
        Visible v = VISIBLE.get(cacheKey(stack));
        float len = v != null ? v.length() : b.length(), hei = v != null ? v.height() : b.height();
        com.dayzhud.mod.inventory.grid.Footprint fp =
                com.dayzhud.mod.inventory.grid.ItemFootprints.baseFootprintOf(stack);
        // Same area drawFlatGunBox gives a grid item: the footprint minus its GRID_INSET.
        float fill = Math.min((fp.width() * 18 - 2 * GRID_INSET) / len, (fp.height() * 18 - 2 * GRID_INSET) / hei);
        return b.unitScale() > 0 ? Math.min(fill, b.unitScale()) : fill;
    }

    /**
     * Draws the item side-on in the box. {@code maxScale} > 0 caps the scale (pixels per model
     * unit) - how the loadout boxes keep the grid's size; otherwise it fills the box.
     */
    public static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y, int w, int h, float z,
                                 boolean rotated, float maxScale) {
        if (!canRender(stack)) return false;
        Bounds b = bounds(stack);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        List<Object> hidden = List.of();
        try {
            Model m = modelFor(stack);
            if (m == null) return false;

            Lighting.setupForFlatItems();
            // TACZ draws into Minecraft's global buffer and flushes it itself, so make sure
            // anything already queued in the GUI (the panel behind the item) goes out first.
            graphics.flush();
            hidden = hideHandParts(m.model());
            Visible v = visible(m, stack, b);

            // No padding here: the caller's box is already inset from its panel border.
            // Rotated (R in the grid): the footprint is tall, so the length fits the box's height.
            float s = rotated ? Math.min(w / v.height(), h / v.length())
                              : Math.min(w / v.length(), h / v.height());
            if (maxScale > 0) s = Math.min(s, maxScale);
            if (b.unitScale() > 0) s = Math.min(s, b.unitScale());   // shared scale; only ever shrinks to fit

            pose.translate(x + w / 2f, y + h / 2f, z);
            if (rotated) {
                // Screen-space quarter turn (y-down): screen-left goes to screen-up, so the
                // barrel points up. Applied outside everything below.
                pose.mulPose(Axis.ZP.rotationDegrees(90f));
            }
            // View space from here on: +X screen-right, +Y screen-up.
            pose.scale(s, -s, s);
            // Centre what is visible, not the vertex box (see class doc, 2.13.4).
            pose.translate(-v.dx(), -v.dy(), 0f);
            orient(pose, b);
            drawModel(m, pose, stack);
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        } finally {
            restoreVisible(hidden);
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    /** Model space -> view space, vertex-bounds centre at the origin. */
    private static void orient(PoseStack pose, Bounds b) {
        if (b.rot() == null) {
            // TACZ guns: constant pose (see class doc) - roll 180 about Z, then turn +Z
            // onto +X. Proper rotations, never mirrors.
            pose.mulPose(Axis.YP.rotationDegrees(90f));
            pose.mulPose(Axis.ZP.rotationDegrees(180f));
        } else {
            // LR items: the rotation read from the model's own geometry.
            float[] r = b.rot();
            // JOML's Matrix3f constructor is column-major: column j = (r[j], r[3+j], r[6+j]).
            pose.mulPose(new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(
                    r[0], r[3], r[6], r[1], r[4], r[7], r[2], r[5], r[8])));
        }
        pose.translate(-b.cx(), -b.cy(), -b.cz());
    }

    /** The item's visible box - measured from pixels the first time it is drawn, then cached.
     *  Falls back to the vertex box if the measurement can't run or sees nothing. Call with the
     *  hand markers already hidden and the GUI's pending draws already flushed. */
    private static Visible visible(Model m, ItemStack stack, Bounds b) {
        String key = cacheKey(stack);
        Visible v = VISIBLE.get(key);
        if (v != null) return v;
        try {
            v = measureVisible(m, stack, b);
        } catch (Throwable t) {
            if (WARNED_EMPTY.add(key + "#probe")) {
                DayzHudMod.LOGGER.warn("dayzhud: couldn't measure {} from pixels; fitting its vertex bounds "
                        + "instead.", key, t);
            }
        }
        if (v == null) v = new Visible(0f, 0f, b.length(), b.height());
        VISIBLE.put(key, v);
        if (GridConfig.DEBUG_LOGGING.get()) {
            DayzHudMod.LOGGER.info("flat item {}: vertex box {} x {}, visible {} x {} offset ({}, {})", key,
                    b.length(), b.height(), v.length(), v.height(), v.dx(), v.dy());
        }
        return v;
    }

    /**
     * Draws the item once into {@link #probe} - same model, same stack, same orientation as the
     * real draw, only scaled to frame its vertex box at half the buffer - reads the colour buffer
     * back and returns the box of pixels with alpha, converted to view units. Null if nothing
     * visible was drawn.
     *
     * Everything global it touches is put back in {@code finally}: the bound framebuffer and
     * viewport (read from GL, so it restores whatever the GUI was drawing into, not assuming the
     * main target), projection and vertex sorting, model-view matrix, and the scissor test (the
     * scrolling backpack enables one, which would otherwise clip the probe).
     */
    private static Visible measureVisible(Model m, ItemStack stack, Bounds b) throws Exception {
        float sp = Math.min(PROBE_W / 2f / b.length(), PROBE_H / 2f / b.height());

        // Saved before anything else - creating the probe binds framebuffer 0 as a side effect.
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        Matrix4f prevProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting prevSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        int x0 = PROBE_W, x1 = -1, y0 = PROBE_H, y1 = -1;
        try {
            if (probe == null) {
                probe = new TextureTarget(PROBE_W, PROBE_H, true, Minecraft.ON_OSX);
                // TACZ masks scope reticles and lenses with the stencil; without one here they
                // would all show and be measured.
                probe.enableStencil();
            }
            if (scissor) GlStateManager._disableScissorTest();
            probe.setClearColor(0f, 0f, 0f, 0f);
            probe.clear(Minecraft.ON_OSX);                 // leaves framebuffer 0 bound
            probe.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, PROBE_W, PROBE_H, 0f, -4000f, 4000f),
                    VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();

            PoseStack pose = new PoseStack();
            pose.translate(PROBE_W / 2f, PROBE_H / 2f, 0f);
            pose.scale(sp, -sp, sp);
            orient(pose, b);
            drawModel(m, pose, stack);
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

            try (NativeImage img = new NativeImage(PROBE_W, PROBE_H, false)) {
                RenderSystem.bindTexture(probe.getColorTextureId());
                img.downloadTexture(0, false);
                for (int row = 0; row < PROBE_H; row++) {
                    int y = PROBE_H - 1 - row;             // GL rows run bottom-up
                    for (int x = 0; x < PROBE_W; x++) {
                        if ((img.getPixelRGBA(x, row) >>> 24) < PROBE_ALPHA_MIN) continue;
                        if (x < x0) x0 = x;
                        if (x > x1) x1 = x;
                        if (y < y0) y0 = y;
                        if (y > y1) y1 = y;
                    }
                }
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(prevProj, prevSorting);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            RenderSystem.viewport(vp[0], vp[1], vp[2], vp[3]);
            if (scissor) GlStateManager._enableScissorTest();
        }
        if (x1 < 0) return null;
        if (GridConfig.DEBUG_LOGGING.get() && (x0 == 0 || y0 == 0 || x1 == PROBE_W - 1 || y1 == PROBE_H - 1)) {
            DayzHudMod.LOGGER.info("flat item {}: visible pixels reach the probe's edge; its size may be "
                    + "under-measured", cacheKey(stack));
        }
        // Probe px -> view units: screen x = W/2 + sp*vx, screen y = H/2 - sp*vy.
        float left = x0, right = x1 + 1, top = y0, bottom = y1 + 1;
        return new Visible(((left + right) / 2f - PROBE_W / 2f) / sp, (PROBE_H / 2f - (top + bottom) / 2f) / sp,
                (right - left) / sp, (bottom - top) / sp);
    }

    /**
     * px per in-game unit at the shared scale, or -1 (fill the box). The reference length is
     * the default model's length in the built-in tables; TACZ's in-game model units are a
     * fixed multiple of those (16 if parts are in block units, 1 if in pixels), so the ratio is
     * snapped to one of those two - a fitted suppressor makes the in-game item longer, and that
     * must make it LONGER on screen, not shrink it to the reference length.
     *
     * TACZ guns in GunSizes: the scale that fills their footprint without attachments
     * (2.13.5); the footprint grows for attachments instead. Other packs' guns: -1, fill.
     */
    private static float unitScaleFor(ItemStack stack, Bounds b) {
        Optional<ResourceLocation> gunId = TaczMarketCompat.gunIdOf(stack);
        if (gunId.isPresent()) {
            // The scale that fills the gun's footprint without attachments; the footprint grows
            // for what's fitted (GunSizes), so attachments never shrink the gun.
            float s = com.dayzhud.mod.inventory.grid.GunSizes.baseScale(gunId.get(),
                    com.dayzhud.mod.inventory.grid.ItemFootprints.plainFootprintOf(stack));
            float ref = com.dayzhud.mod.inventory.grid.GunSizes.length(gunId.get());
            if (s <= 0 || ref <= 0 || b.length() <= 0) return -1f;
            return s * (ref / b.length() > 4f ? 16f : 1f);
        }
        String tag = lrTag(stack);
        Float ref = tag == null ? null
                : com.dayzhud.mod.inventory.grid.DefaultItemFootprints.LR_LENGTH.get(stack.getTag().getString(tag));
        if (ref == null || b.length() <= 0) return -1f;
        return PX_PER_UNIT * (ref / b.length() > 4f ? 16f : 1f);
    }

    /** Model units per in-game unit (16 if TACZ draws in block units, 1 if pixels), from the
     *  built-in reference length vs the measured longest extent. 16 when unknown - TACZ parts
     *  follow Minecraft's ModelPart convention of block units. */
    private static float unitFactorFor(ItemStack stack, float measuredLongest) {
        String tag = lrTag(stack);
        Float ref = tag == null ? null
                : com.dayzhud.mod.inventory.grid.DefaultItemFootprints.LR_LENGTH.get(stack.getTag().getString(tag));
        if (ref == null || measuredLongest <= 0) return 16f;
        return ref / measuredLongest > 4f ? 16f : 1f;
    }

    private static String cacheKey(ItemStack stack) {
        String id = TaczMarketCompat.gunIdOf(stack).map(Object::toString).orElseGet(() -> {
            String tag = lrTag(stack);
            return tag == null ? "?" : tag + "=" + stack.getTag().getString(tag);
        });
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
            List<Object> hidden = hideHandParts(m.model());
            boolean full;
            try {
                full = measureFullDraw(m, stack, rec, key, null);
                if (!full) {
                    // Body only (misses attachment models like a separate stock), but better than nothing.
                    PoseStack ps = new PoseStack();
                    for (Object part : (List<?>) getShouldRender.invoke(m.model())) {
                        partRender.invoke(part, ps, ItemDisplayContext.FIXED, rec, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    }
                }
            } finally {
                restoreVisible(hidden);
            }
            // LR items are held at their model origin (Bedrock (0,0,0)). TACZ's loader (which LR
            // uses) maps it to y = 24 - 0 in pixels, and parts are then drawn in block units
            // (/16) - so in game the origin is (0, 24/k, 0), k being the same unit factor the
            // shared scale detects. 2.13.0 used (0,24,0) in block units - far above the model,
            // so the "farthest point" was a knife's bottom edge and it pointed down.
            // (The hand markers were tried as the grip and rejected: they mark where the
            // first-person hand model goes, which on bats and karambits is off the item.)
            float[] grip = null;
            if (!m.gun()) {
                Bounds probe = rec.toBounds(true, false, null);
                float k = unitFactorFor(stack, probe == null ? 0f : Math.max(probe.maxX() - probe.minX(),
                        Math.max(probe.maxY() - probe.minY(), probe.maxZ() - probe.minZ())));
                grip = new float[]{0f, 24f / k, 0f};
            }
            Bounds b = rec.toBounds(m.gun(), "MeleeWeaponId".equals(lrTag(stack)), grip);
            if (b != null) b = b.withUnitScale(unitScaleFor(stack, b));
            if (b == null) {
                if (WARNED_EMPTY.add(key)) {
                    DayzHudMod.LOGGER.warn("dayzhud: measured no geometry for gun {}; drawing it "
                            + "as a normal item instead.", key);
                }
                return null;
            }
            CACHE.put(key, b);
            if (GridConfig.DEBUG_LOGGING.get()) {
                DayzHudMod.LOGGER.info("flat gun: {} -> {} verts ({}), {} hand parts hidden, length {}, height {}, "
                                + "px/unit {}, grip {}", key, rec.n, full ? "full draw" : "body only", hidden.size(),
                        b.length(), b.height(), b.unitScale(), grip == null ? "-" : Arrays.toString(grip));
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
    /** Render types contributing fewer vertices than this are effects - muzzle flash and glow
     *  billboards, laser beams: a few quads - not geometry, and are left out of the size. */
    private static final int MIN_GEOMETRY_VERTS = 32;

    private static boolean measureFullDraw(Model m, ItemStack stack, Recorder rec, String key, double[] raw) {
        RenderBuffers buffers = Minecraft.getInstance().renderBuffers();
        MultiBufferSource.BufferSource real = buffers.bufferSource();
        Field f = globalBufferField(buffers, real);
        if (f == null) return false;
        // One recorder per render type, so effects can be told apart from geometry. Filtering
        // by type NAME doesn't work: TACZ draws attachments with the same translucent type as
        // its muzzle flash (verified in bytecode). Filtering by SIZE does: an effect is a
        // billboard of a few quads, a stock or scope is dozens of cubes.
        Map<RenderType, Recorder> perType = new LinkedHashMap<>();
        MultiBufferSource.BufferSource capture = new MultiBufferSource.BufferSource(new BufferBuilder(256), Map.of()) {
            @Override
            public VertexConsumer getBuffer(RenderType type) {
                return perType.computeIfAbsent(type, t -> new Recorder());
            }

            @Override
            public void endBatch() {}

            @Override
            public void endBatch(RenderType type) {}
        };
        try {
            f.set(buffers, capture);
            drawModel(m, new PoseStack(), stack);
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
        if (raw != null) {
            for (Recorder r : perType.values()) {
                for (int i = 0; i < r.n; i++) { raw[0] += r.xs[i]; raw[1] += r.ys[i]; raw[2] += r.zs[i]; }
                raw[3] += r.n;
            }
            if (rec == null) return raw[3] > 0;
        }
        boolean anyGeometry = perType.values().stream().anyMatch(r -> r.n >= MIN_GEOMETRY_VERTS);
        for (Map.Entry<RenderType, Recorder> e : perType.entrySet()) {
            Recorder r = e.getValue();
            boolean keep = !anyGeometry || r.n >= MIN_GEOMETRY_VERTS;
            if (keep) rec.addAll(r);
            if (GridConfig.DEBUG_LOGGING.get() && key != null) {
                Bounds rb = r.toBounds(true, false, null);
                DayzHudMod.LOGGER.info("flat item {}: {} {} verts {} x[{},{}] y[{},{}] z[{},{}]", key,
                        keep ? "kept" : "SKIPPED (effect)", r.n, e.getKey(),
                        rb == null ? 0 : rb.minX(), rb == null ? 0 : rb.maxX(), rb == null ? 0 : rb.minY(),
                        rb == null ? 0 : rb.maxY(), rb == null ? 0 : rb.minZ(), rb == null ? 0 : rb.maxZ());
            }
        }
        return rec.n > 0;
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

    /** TACZ's hand-position marker bones: rightHand / leftHand and their *_pos boxes. Case-
     *  insensitive and anchored, so it never matches e.g. "Handguard". */
    private static final java.util.regex.Pattern HAND_PART =
            java.util.regex.Pattern.compile("(?i)^(left|right)_?hand(_pos)?$");
    private static Field partName, partVisible, partChildren;

    /**
     * Sets visible=false on every hand-marker part in the model's tree (TACZ's BedrockPart has
     * public name / visible / children fields) and returns the parts it changed, so the caller
     * can restore them. Hiding a parent hides its children (e.g. bullets held during reload).
     */
    private static List<Object> hideHandParts(Object model) {
        List<Object> changed = new java.util.ArrayList<>();
        try {
            if (partName == null) {
                Class<?> part = Class.forName("com.tacz.guns.client.model.bedrock.BedrockPart");
                partName = part.getField("name");
                partVisible = part.getField("visible");
                partChildren = part.getField("children");
            }
            java.util.ArrayDeque<Object> todo = new java.util.ArrayDeque<>((List<?>) getShouldRender.invoke(model));
            while (!todo.isEmpty()) {
                Object p = todo.pop();
                Object name = partName.get(p);
                if (name instanceof String n && HAND_PART.matcher(n).matches()) {
                    if (partVisible.getBoolean(p)) {
                        partVisible.setBoolean(p, false);
                        changed.add(p);
                    }
                    continue;
                }
                Object kids = partChildren.get(p);
                if (kids instanceof java.util.Collection<?> c) todo.addAll(c);
            }
        } catch (Throwable t) {
            DayzHudMod.LOGGER.debug("dayzhud: couldn't hide TACZ hand parts", t);
        }
        return changed;
    }

    private static void restoreVisible(List<Object> parts) {
        for (Object p : parts) {
            try {
                partVisible.setBoolean(p, true);
            } catch (Throwable ignored) {
            }
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

        void addAll(Recorder o) {
            for (int i = 0; i < o.n; i++) vertex(o.xs[i], o.ys[i], o.zs[i]);
        }

        /**
         * TACZ guns: fixed orientation (length Z, height Y). LR Tactical items: read from the
         * geometry, because its models share no convention (melee run along Y or Z,
         * consumables any way - measured across every LR / Apocalyptic Arsenal model):
         * the longest axis becomes horizontal, the second-longest vertical, and you look along
         * the thinnest - a blade shows its flat. The tip goes left, like a barrel: the tip is
         * the end farther from where the model is held, which for every LR melee model is its
         * origin - Bedrock (0,0,0), i.e. (0,24,0) after TACZ's load-time Y flip. Up is model-up
         * (-Y after that flip) when the vertical axis is Y. Built as a proper rotation (rows
         * -tip, up, -(tip x up)), never a mirror.
         */
        Bounds toBounds(boolean gun, boolean melee, float[] grip) {
            if (n == 0) return null;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE,
                    maxY = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
                minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
            }
            if (gun) {
                return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, maxZ - minZ, maxY - minY, null,
                        (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f, -1f);
            }
            float[] ext = {maxX - minX, maxY - minY, maxZ - minZ};
            // Where the item is held: the centroid of its hand-position markers, measured in the
            // same in-game units (see bounds()). 2.13.0 assumed the model origin at (0,24,0) -
            // an unverified unit/offset guess that made knives point down in game.
            float[] lo = {minX, minY, minZ}, hi = {maxX, maxY, maxZ},
                    ref = grip != null ? grip : new float[]{0f, 0f, 0f};
            int a0 = 0;
            for (int i = 1; i < 3; i++) if (ext[i] > ext[a0]) a0 = i;
            int a1 = -1;
            for (int i = 0; i < 3; i++) if (i != a0 && (a1 < 0 || ext[i] > ext[a1])) a1 = i;

            // Length direction t (pointing at the tip, which goes screen-left).
            float[] t = new float[3];
            if (melee) {
                // Grip -> farthest point: the tip. Also straightens a model authored at an angle.
                int far = 0;
                float best = -1f;
                for (int i = 0; i < n; i++) {
                    float dx = xs[i] - ref[0], dy = ys[i] - ref[1], dz = zs[i] - ref[2];
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > best) { best = d2; far = i; }
                }
                t[0] = xs[far] - ref[0]; t[1] = ys[far] - ref[1]; t[2] = zs[far] - ref[2];
            } else {
                t[a0] = Math.abs(hi[a0] - ref[a0]) >= Math.abs(lo[a0] - ref[a0]) ? 1f : -1f;
            }
            normalize(t);
            // Up u: the second-longest model axis (model-up, -Y after TACZ's load-time flip,
            // when that axis is Y), made perpendicular to t.
            float[] u = new float[3];
            u[a1] = a1 == 1 ? -1f : 1f;
            float dot = u[0] * t[0] + u[1] * t[1] + u[2] * t[2];
            for (int i = 0; i < 3; i++) u[i] -= dot * t[i];
            if (!normalize(u)) {           // t lay along that axis: fall back to the third one
                u = new float[3];
                u[3 - a0 - a1] = 1f;
                dot = u[0] * t[0] + u[1] * t[1] + u[2] * t[2];
                for (int i = 0; i < 3; i++) u[i] -= dot * t[i];
                normalize(u);
            }
            float[] w = {t[1] * u[2] - t[2] * u[1], t[2] * u[0] - t[0] * u[2], t[0] * u[1] - t[1] * u[0]};
            // Extents along the new axes, and the centre that puts the item mid-box.
            float tLo = Float.MAX_VALUE, tHi = -Float.MAX_VALUE, uLo = Float.MAX_VALUE, uHi = -Float.MAX_VALUE,
                    wLo = Float.MAX_VALUE, wHi = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float pt = xs[i] * t[0] + ys[i] * t[1] + zs[i] * t[2];
                float pu = xs[i] * u[0] + ys[i] * u[1] + zs[i] * u[2];
                float pw = xs[i] * w[0] + ys[i] * w[1] + zs[i] * w[2];
                tLo = Math.min(tLo, pt); tHi = Math.max(tHi, pt);
                uLo = Math.min(uLo, pu); uHi = Math.max(uHi, pu);
                wLo = Math.min(wLo, pw); wHi = Math.max(wHi, pw);
            }
            float mt = (tLo + tHi) / 2f, mu = (uLo + uHi) / 2f, mw = (wLo + wHi) / 2f;
            float cx = t[0] * mt + u[0] * mu + w[0] * mw;
            float cy = t[1] * mt + u[1] * mu + w[1] * mw;
            float cz = t[2] * mt + u[2] * mu + w[2] * mw;
            // Rows -t, u, -w: tip -> screen-left, u -> screen-up; det +1 (never a mirror).
            float[] rot = {-t[0], -t[1], -t[2], u[0], u[1], u[2], -w[0], -w[1], -w[2]};
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, tHi - tLo, uHi - uLo, rot, cx, cy, cz, -1f);
        }

        private static boolean normalize(float[] v) {
            float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (len < 1e-4f) return false;
            v[0] /= len; v[1] /= len; v[2] /= len;
            return true;
        }
    }
}
