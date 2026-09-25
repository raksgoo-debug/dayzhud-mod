package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.grid.GridConfig;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Measures what a flat-drawn item really looks like on screen: draws it once into a private
 * off-screen buffer (with a stencil, like the main one) and reads back the box of pixels that
 * came out opaque. Vertex counting can't do this - models carry geometry you never see (TACZ's
 * stencil-masked scope reticles, transparent cubes, ...) - see TaczFlatGunRenderer 2.13.4.
 *
 * Shared by TaczFlatGunRenderer and FlatModelRenderer since 2.13.6.
 */
public final class PixelProbe {

    /**
     * An item's visible box, in the units of the pose it was drawn in (+x screen-right, +y
     * screen-up): its centre relative to the draw's origin, and its width/height.
     */
    public record Box(float dx, float dy, float length, float height) {}

    /** Draws an item into the given pose; may throw, which the caller handles. */
    @FunctionalInterface
    public interface Draw {
        void draw(PoseStack pose) throws Exception;
    }

    /** Made on first use. Callers frame the item at about half its size, so geometry they
     *  didn't expect still lands inside it. */
    private static TextureTarget probe;
    public static final int W = 1024, H = 512;
    /** A pixel counts as visible from this alpha up (cutout textures keep >= 0.1, i.e. 26). */
    private static final int ALPHA_MIN = 8;

    private PixelProbe() {}

    /**
     * Draws with {@code draw} at {@code sp} probe px per unit, origin at the buffer's centre,
     * and returns the visible box in those units - null if nothing visible was drawn. Call on
     * the render thread with the GUI's pending draws already flushed.
     *
     * Everything global it touches is put back in {@code finally}: the bound framebuffer and
     * viewport (read from GL, so it restores whatever the GUI was drawing into, not assuming the
     * main target), projection and vertex sorting, model-view matrix, and the scissor test (the
     * scrolling backpack enables one, which would otherwise clip the probe).
     */
    public static Box measure(float sp, Draw draw, String what) throws Exception {
        // Saved before anything else - creating the probe binds framebuffer 0 as a side effect.
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        Matrix4f prevProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting prevSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        int x0 = W, x1 = -1, y0 = H, y1 = -1;
        try {
            if (probe == null) {
                probe = new TextureTarget(W, H, true, Minecraft.ON_OSX);
                // TACZ masks scope reticles and lenses with the stencil; without one here they
                // would all show and be measured.
                probe.enableStencil();
            }
            if (scissor) GlStateManager._disableScissorTest();
            probe.setClearColor(0f, 0f, 0f, 0f);
            probe.clear(Minecraft.ON_OSX);                 // leaves framebuffer 0 bound
            probe.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, W, H, 0f, -4000f, 4000f),
                    VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();

            PoseStack pose = new PoseStack();
            pose.translate(W / 2f, H / 2f, 0f);
            pose.scale(sp, -sp, sp);
            draw.draw(pose);
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

            try (NativeImage img = new NativeImage(W, H, false)) {
                RenderSystem.bindTexture(probe.getColorTextureId());
                img.downloadTexture(0, false);
                for (int row = 0; row < H; row++) {
                    int y = H - 1 - row;                   // GL rows run bottom-up
                    for (int x = 0; x < W; x++) {
                        if ((img.getPixelRGBA(x, row) >>> 24) < ALPHA_MIN) continue;
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
        if (GridConfig.DEBUG_LOGGING.get() && (x0 == 0 || y0 == 0 || x1 == W - 1 || y1 == H - 1)) {
            DayzHudMod.LOGGER.info("flat item {}: visible pixels reach the probe's edge; its size may be "
                    + "under-measured", what);
        }
        // Probe px -> units: screen x = W/2 + sp*vx, screen y = H/2 - sp*vy.
        float left = x0, right = x1 + 1, top = y0, bottom = y1 + 1;
        return new Box(((left + right) / 2f - W / 2f) / sp, (H / 2f - (top + bottom) / 2f) / sp,
                (right - left) / sp, (bottom - top) / sp);
    }
}
