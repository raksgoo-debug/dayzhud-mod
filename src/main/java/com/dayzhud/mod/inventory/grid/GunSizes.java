package com.dayzhud.mod.inventory.grid;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a TACZ gun keep its size when attachments go on: the gun is always drawn at the scale
 * that fills its plain footprint, and its footprint GROWS to make room for what's fitted -
 * a suppressor adds width, a tall scope with a grip can add a row - instead of the whole gun
 * being shrunk into the same box.
 *
 * Why data, not measuring: the footprint is decided on the server, which never loads models.
 * {@code /dayzhud/gun_sizes.txt} (in the jar, so both sides read the same file) holds each
 * default gun's visible length/height and, per gun and attachment, how far that attachment
 * reaches past the plain gun on each side - measured offline from the TACZ jar the same way
 * DefaultGunFootprints was. Several attachments combine per side by the furthest reach (a
 * suppressor and a bayonet both on the left count once, as the longer).
 *
 * Guns or attachments not in the file (other gun packs) don't grow; the renderer then fits
 * them into the footprint they have, shrinking if it must, as before.
 */
public final class GunSizes {

    /** GUI px between a grid item's box edge and the gun drawn in it. */
    public static final int INSET = 2;
    /** How far past its INSET a gun may reach before it takes another cell: 1 px each side,
     *  still clear of the box's outline. Saves a whole extra cell for a sliver of muzzle. */
    private static final int SLACK = 2;
    /** Growth limits - the inventory is 9 wide; nothing needs more than 3 rows. */
    private static final int MAX_W = 9, MAX_H = 3;

    private record Size(float length, float height) {}

    private static Map<String, Size> guns;
    /** "gunId attachmentId" -> reach past the plain gun: left, right, top, bottom (model units). */
    private static Map<String, float[]> reach;

    private GunSizes() {}

    private static synchronized void load() {
        if (guns != null) return;
        Map<String, Size> g = new HashMap<>();
        Map<String, float[]> r = new HashMap<>();
        try (InputStream in = GunSizes.class.getResourceAsStream("/dayzhud/gun_sizes.txt")) {
            if (in == null) throw new IllegalStateException("missing from the jar");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = br.readLine()) != null; ) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 4 && p[0].equals("gun")) {
                    g.put(p[1], new Size(Float.parseFloat(p[2]), Float.parseFloat(p[3])));
                } else if (p.length == 7 && p[0].equals("att")) {
                    r.put(p[1] + " " + p[2], new float[]{Float.parseFloat(p[3]), Float.parseFloat(p[4]),
                            Float.parseFloat(p[5]), Float.parseFloat(p[6])});
                }
            }
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("dayzhud: couldn't read gun_sizes.txt; guns won't grow for attachments", e);
        }
        reach = r;
        guns = g;
    }

    /**
     * GUI px per model unit a gun is drawn at: the scale that fills {@code plain} - its
     * footprint without attachments - edge to edge, INSET in. -1 for a gun not in the file.
     */
    public static float baseScale(ResourceLocation gunId, Footprint plain) {
        load();
        Size s = guns.get(gunId.toString());
        if (s == null) return -1f;
        return Math.min((plain.width() * 18 - 2 * INSET) / s.length(), (plain.height() * 18 - 2 * INSET) / s.height());
    }

    /** The plain gun's visible length in model units, or -1 for a gun not in the file. */
    public static float length(ResourceLocation gunId) {
        load();
        Size s = guns.get(gunId.toString());
        return s == null ? -1f : s.length();
    }

    /** {@code plain}, grown just enough to hold these attachments at {@link #baseScale}. */
    public static Footprint withAttachments(ResourceLocation gunId, List<ResourceLocation> attachments,
                                            Footprint plain) {
        if (attachments.isEmpty()) return plain;
        float scale = baseScale(gunId, plain);
        if (scale <= 0) return plain;
        Size s = guns.get(gunId.toString());
        float[] side = new float[4];
        for (ResourceLocation a : attachments) {
            float[] r = reach.get(gunId + " " + a);
            if (r == null) continue;
            for (int i = 0; i < 4; i++) side[i] = Math.max(side[i], r[i]);
        }
        int w = Math.min(Math.max(plain.width(), cells((s.length() + side[0] + side[1]) * scale)),
                Math.max(plain.width(), MAX_W));
        int h = Math.min(Math.max(plain.height(), cells((s.height() + side[2] + side[3]) * scale)),
                Math.max(plain.height(), MAX_H));
        return w == plain.width() && h == plain.height() ? plain : new Footprint(w, h);
    }

    /** Cells needed for a gun this many px long (or tall). */
    private static int cells(float px) {
        return (int) Math.ceil((px + 2 * INSET - SLACK) / 18f - 1e-4f);
    }
}
