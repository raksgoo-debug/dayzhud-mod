package com.dayzhud.mod.client;

import com.dayzhud.mod.noise.ClientNoise;
import com.dayzhud.mod.noise.NoiseConfig;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Noise markers (2.19.0): for each gunshot, explosion or breaking glass you heard in the last
 * few seconds, an arrowhead on a ring around the crosshair pointing the way it came from,
 * with a small label ("GUNSHOT - DISTANT"). It turns with you, so you can face it.
 *
 * The arrowhead grows the closer the noise was, and fades out over its last second.
 */
public class NoiseOverlay implements IGuiOverlay {

    private static final int RING_RADIUS = 28;
    private static final int LABEL_RADIUS = 44;
    private static final float LABEL_SCALE = 0.6f;
    /** Fully opaque until this fraction of the marker's life, then fades. */
    private static final float FADE_FROM = 0.75f;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !NoiseConfig.ENABLED.get()) return;
        var markers = ClientNoise.markers();
        if (markers.isEmpty()) return;

        float cx = width / 2f, cy = height / 2f;
        long now = System.currentTimeMillis();
        Font font = mc.font;
        for (ClientNoise.Heard m : markers) {
            float life = (now - m.at()) / (float) ClientNoise.MARKER_MS;
            float alpha = life < FADE_FROM ? 1f : Math.max(0f, (1f - life) / (1f - FADE_FROM));
            int a = Math.round(alpha * 230);
            if (a < 8) continue;
            int argb = (a << 24) | m.kind().color;
            float relative = Mth.wrapDegrees(m.bearing() - player.getYRot());

            // Arrowhead, drawn pointing up and rotated into place. In GUI space (y down) a
            // positive Z rotation turns clockwise, which matches yaw: +90 is to your right.
            int size = 3 + Math.round(Mth.clamp(m.closeness(), 0f, 1f) * 3f);
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(relative));
            int tip = -RING_RADIUS - size;
            for (int row = 0; row < size; row++) {
                graphics.fill(-row, tip + row, row + 1, tip + row + 1, argb);
            }
            graphics.pose().popPose();

            // Label, upright, a little further out along the same direction.
            double rad = Math.toRadians(relative);
            float lx = cx + (float) Math.sin(rad) * LABEL_RADIUS;
            float ly = cy - (float) Math.cos(rad) * LABEL_RADIUS;
            String text = I18n.get(m.kind().langKey()) + " · " + I18n.get(distanceKey(m.closeness()));
            graphics.pose().pushPose();
            graphics.pose().translate(lx, ly, 0);
            graphics.pose().scale(LABEL_SCALE, LABEL_SCALE, 1f);
            graphics.drawString(font, text, -font.width(text) / 2, -font.lineHeight / 2, argb, true);
            graphics.pose().popPose();
        }
    }

    private static String distanceKey(float closeness) {
        if (closeness >= 0.66f) return "noise.dayzhud.distance.close";
        if (closeness >= 0.33f) return "noise.dayzhud.distance.near";
        return "noise.dayzhud.distance.far";
    }
}
