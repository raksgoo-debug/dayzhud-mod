package com.dayzhud.mod.noise;

/**
 * What made a noise. The radius comes from NoiseConfig; {@link #heardByPlayers} decides
 * whether other players get a direction marker for it (footsteps and bows only draw mobs -
 * a marker for every footstep would be noise of a different kind). Gunshots get no marker:
 * they're heard for real now, from where they were fired (DistantGunfire).
 *
 * Sent over the network by ordinal, so new kinds go on the END.
 */
public enum NoiseKind {
    FOOTSTEP(false, 0xBFBFBF),
    GUNSHOT(false, 0xE2A62E),
    SUPPRESSED_GUNSHOT(false, 0xC9B98A),
    EXPLOSION(true, 0xE23A2E),
    GLASS(true, 0x9CD3E8),
    BOW(false, 0xBFBFBF);

    public final boolean heardByPlayers;
    /** Marker colour on the HUD (RGB). */
    public final int color;

    NoiseKind(boolean heardByPlayers, int color) {
        this.heardByPlayers = heardByPlayers;
        this.color = color;
    }

    public String langKey() {
        return "noise.dayzhud." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public double radius() {
        return switch (this) {
            case FOOTSTEP -> NoiseConfig.WALK.get();
            case GUNSHOT -> NoiseConfig.GUNSHOT.get();
            case SUPPRESSED_GUNSHOT -> NoiseConfig.SUPPRESSED_GUNSHOT.get();
            case EXPLOSION -> NoiseConfig.EXPLOSION.get();
            case GLASS -> NoiseConfig.GLASS.get();
            case BOW -> NoiseConfig.BOW.get();
        };
    }

    public static NoiseKind byOrdinal(int i) {
        NoiseKind[] all = values();
        return i >= 0 && i < all.length ? all[i] : FOOTSTEP;
    }
}
