package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Rummage compatibility: do not steal a container that still has to be searched.
 *
 * Rummage hides a container's contents until the player has rummaged through it, by masking
 * {@code Slot.getItem} for slots bound to an {@code IRummageable} container. This mod's
 * container merging opens its own menu over that same container, and while the masking
 * follows the slots, the search interaction, its progress bar and its sounds belong to
 * Rummage's own screen - replacing that screen removes the mechanic even though the items
 * stay hidden.
 *
 * So the rule is simple and one-directional: while a container still needs rummaging for this
 * player, the redirect stands down and Rummage's screen is left alone. Once it has been
 * searched, opening it again gives the merged view as usual. That keeps the searching
 * mechanic exactly as its author wrote it rather than reimplementing it badly.
 */
public final class RummageCompat {

    public static final String MOD_ID = "rummage";

    private static Boolean loaded;
    private static boolean resolved;
    private static Class<?> rummageable;
    private static Method isNeedRummageForPlayer;

    private RummageCompat() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MOD_ID);
        return loaded;
    }

    private static synchronized boolean resolve() {
        if (resolved) return rummageable != null;
        resolved = true;
        try {
            ClassLoader cl = RummageCompat.class.getClassLoader();
            rummageable = Class.forName("com.scarasol.rummage.api.mixin.IRummageable", false, cl);
            // The one-arg overload is the per-player question; the no-arg one only says
            // whether the container is the kind that CAN be rummaged.
            isNeedRummageForPlayer = rummageable.getMethod("isNeedRummage", Player.class);
            return true;
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("Rummage is installed but its API did not resolve - "
                    + "container merging will not stand down for unsearched containers: {}",
                    t.toString());
            rummageable = null;
            return false;
        }
    }

    /**
     * True when this container has not yet been searched by this player, and the redirect
     * should therefore leave Rummage's own screen in place.
     *
     * Fails open: any doubt and we report false, because a false negative costs the merged
     * view for one container while a false positive would hide a container from the player.
     */
    public static boolean needsSearching(Container container, Player player) {
        if (container == null || player == null) return false;
        if (!MarketConfig.RESPECT_RUMMAGE.get() || !isModLoaded() || !resolve()) return false;
        if (!rummageable.isInstance(container)) return false;
        try {
            Object result = isNeedRummageForPlayer.invoke(container, player);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }
}
