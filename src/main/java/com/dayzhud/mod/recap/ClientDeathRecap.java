package com.dayzhud.mod.recap;

import javax.annotation.Nullable;

/**
 * The local player's last death recap, until they respawn or leave the world.
 * Plain data - no client-only imports - so DeathRecapPacket.handle is safe on any side.
 */
public final class ClientDeathRecap {

    @Nullable
    private static DeathRecapPacket recap;

    private ClientDeathRecap() {}

    static void set(DeathRecapPacket p) {
        recap = p;
    }

    @Nullable
    public static DeathRecapPacket get() {
        return recap;
    }

    public static void clear() {
        recap = null;
    }
}
