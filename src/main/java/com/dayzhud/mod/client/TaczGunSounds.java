package com.dayzhud.mod.client;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which file a TACZ gun's third-person shot plays (2.19.0).
 *
 * Asked of TACZ itself, by reflection: TimelessAPI.getGunDisplay(stack) (verified, it's what
 * the flat gun renderer uses) and the display's sound map, keyed "shoot_3p" / "silence_3p"
 * like the gun display JSON. That's the sound id TACZ would play, e.g. tacz:m4a1/m4a1_shoot_3p,
 * which lives at assets/tacz/tacz_sounds/m4a1/m4a1_shoot_3p.ogg - and a resource pack that
 * replaces that file (custom gun sounds) is honoured, because the file is then loaded through
 * the normal resource manager.
 *
 * If the display can't be read, a couple of TACZ's naming conventions are tried. Every
 * candidate is checked to exist before it's used, and the answer is cached per gun.
 */
final class TaczGunSounds {

    record Resolved(ResourceLocation soundId, ResourceLocation file) {}

    private static final Map<String, Optional<Resolved>> CACHE = new HashMap<>();
    private static boolean reflectionTried;
    @Nullable private static Method getGunDisplay;
    @Nullable private static Method getSound;

    private TaczGunSounds() {}

    static void clear() {
        CACHE.clear();
    }

    @Nullable
    static Resolved thirdPerson(ItemStack gun, boolean suppressed) {
        ResourceLocation gunId = TaczMarketCompat.gunIdOf(gun).orElse(null);
        if (gunId == null) return null;
        String key = gunId + (suppressed ? "|s" : "|n");
        Optional<Resolved> cached = CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        Resolved found = null;
        if (suppressed) found = fromDisplay(gun, "silence_3p");
        if (found == null) found = fromDisplay(gun, "shoot_3p");
        if (found == null) found = byConvention(gunId, suppressed ? "silence_3p" : "shoot_3p");
        if (found == null && suppressed) found = byConvention(gunId, "shoot_3p");
        if (found == null) {
            DayzHudMod.LOGGER.info("[dayzhud] No third-person shot sound found for {} - it won't be heard "
                    + "from far away.", gunId);
        }
        CACHE.put(key, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Resolved fromDisplay(ItemStack gun, String soundKey) {
        if (!resolveReflection()) return null;
        try {
            Object display = getGunDisplay.invoke(null, gun);
            if (!(display instanceof Optional<?> opt) || opt.isEmpty()) return null;
            Object id = getSound.invoke(opt.get(), soundKey);
            return id instanceof ResourceLocation rl ? existing(rl) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** tacz:m4a1 -> tacz:m4a1/m4a1_shoot_3p, and the underscore-less tacz:glock_17/glock17_shoot_3p. */
    @Nullable
    private static Resolved byConvention(ResourceLocation gunId, String soundKey) {
        String path = gunId.getPath();
        for (String name : new String[]{path, path.replace("_", "")}) {
            Resolved r = existing(new ResourceLocation(gunId.getNamespace(), path + "/" + name + "_" + soundKey));
            if (r != null) return r;
        }
        return null;
    }

    @Nullable
    private static Resolved existing(ResourceLocation soundId) {
        ResourceLocation file = new ResourceLocation(soundId.getNamespace(),
                "tacz_sounds/" + soundId.getPath() + ".ogg");
        return Minecraft.getInstance().getResourceManager().getResource(file).isPresent()
                ? new Resolved(soundId, file) : null;
    }

    /**
     * GunDisplayInstance's sound lookup. Its name is getSounds(String) as far as I know, but
     * it's matched loosely - any public method taking a String and returning a
     * ResourceLocation with "sound" in its name - so a rename in TACZ doesn't silently lose it.
     */
    private static boolean resolveReflection() {
        if (reflectionTried) return getGunDisplay != null && getSound != null;
        reflectionTried = true;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            getGunDisplay = api.getMethod("getGunDisplay", ItemStack.class);
            Class<?> display = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance");
            try {
                getSound = display.getMethod("getSounds", String.class);
            } catch (NoSuchMethodException e) {
                for (Method m : display.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class
                            && m.getReturnType() == ResourceLocation.class
                            && m.getName().toLowerCase(Locale.ROOT).contains("sound")) {
                        getSound = m;
                        break;
                    }
                }
            }
            if (getSound == null) {
                DayzHudMod.LOGGER.warn("[dayzhud] TACZ's gun display has no sound lookup that dayzhud recognises "
                        + "- distant gunshots fall back to TACZ's file naming.");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            DayzHudMod.LOGGER.warn("[dayzhud] TACZ's gun display couldn't be read - distant gunshots fall back "
                    + "to TACZ's file naming.", e);
            getGunDisplay = null;
        }
        return getGunDisplay != null && getSound != null;
    }
}
