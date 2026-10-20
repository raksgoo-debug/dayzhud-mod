package com.dayzhud.mod.noise;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Noises the local player heard recently, as sent by the server (NoisePacket), plus a
 * short-lived "you just made a loud noise" pulse for the noise meter.
 *
 * Plain data on purpose - no client-only imports - so NoisePacket.handle can reach it
 * without classloading anything client-side on a dedicated server.
 */
public final class ClientNoise {

    /** How long a direction marker stays on screen. */
    public static final long MARKER_MS = 4000;
    /** How long your own loud noise holds the meter up. */
    public static final long PULSE_MS = 1500;
    private static final int MAX_MARKERS = 8;

    public record Heard(NoiseKind kind, float bearing, float closeness, long at) {}

    private static final List<Heard> HEARD = new ArrayList<>();
    private static float pulse01;
    private static long pulseAt;

    private ClientNoise() {}

    static void accept(NoiseKind kind, float bearing, float closeness, boolean self) {
        long now = System.currentTimeMillis();
        if (self) {
            float level = (float) Math.min(1.0, kind.radius() / 32.0);
            if (level >= selfPulse01()) {
                pulse01 = level;
                pulseAt = now;
            }
            return;
        }
        // A new noise from (roughly) the same direction replaces the old marker rather than
        // stacking - an automatic weapon would otherwise pile ten markers on one spot.
        HEARD.removeIf(h -> h.kind == kind && Math.abs(wrap(h.bearing - bearing)) < 20f);
        HEARD.add(new Heard(kind, bearing, closeness, now));
        while (HEARD.size() > MAX_MARKERS) HEARD.remove(0);
    }

    /** Markers still on screen, oldest first. Expired ones are dropped as a side effect. */
    public static List<Heard> markers() {
        long now = System.currentTimeMillis();
        for (Iterator<Heard> it = HEARD.iterator(); it.hasNext(); ) {
            if (now - it.next().at > MARKER_MS) it.remove();
        }
        return HEARD;
    }

    /** 0-1, fading out over PULSE_MS after your own gunshot/explosion/glass. */
    public static float selfPulse01() {
        long age = System.currentTimeMillis() - pulseAt;
        if (age >= PULSE_MS) return 0f;
        return pulse01 * (1f - age / (float) PULSE_MS);
    }

    public static void reset() {
        HEARD.clear();
        pulse01 = 0f;
        pulseAt = 0L;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
