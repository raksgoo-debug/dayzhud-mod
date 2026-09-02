package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TACZ pricing and stack building, entirely by reflection.
 *
 * Same shape as ThirstWasTakenCompat and FirstAidCompat: no compile-time dependency, every
 * lookup cached, everything degrading to empty. That is deliberate rather than lazy - a
 * hard dependency on TACZ pins this mod to one TACZ version, and the weapon-handling skill
 * was already turned down for exactly that reason.
 *
 * The thing that makes TACZ awkward for a shop is that guns are not distinct items. Every
 * gun is one item, {@code tacz:modern_kinetic_gun}, carrying a GunId in NBT; ammo and
 * attachments are the same. So a listing has to be an NBT-bearing ItemStack built through
 * TACZ's own builders, and a price key has to be derived from the NBT rather than the item
 * id - otherwise every gun in the game would share one price.
 */
public final class TaczMarketCompat {

    public static final String MOD_ID = "tacz";

    private static Boolean loaded;
    private static boolean resolved;

    private static Method getAllCommonGunIndex;
    private static Method getAllCommonAmmoIndex;
    private static Method gunIndexGetType;
    private static Method gunIndexGetGunData;
    private static Method gunDataGetRpm;
    private static Method gunDataGetBulletData;
    private static Method bulletGetDamage;
    private static Method bulletGetAmount;
    private static Method bulletGetPierce;
    private static Method bulletGetExtraDamage;
    private static Method extraGetArmorIgnore;

    private static Method gunBuilderCreate, gunBuilderSetId, gunBuilderSetAmmoCount, gunBuilderBuild;
    private static Method ammoBuilderCreate, ammoBuilderSetId, ammoBuilderSetCount, ammoBuilderBuild;

    private static Method getIGunOrNull, iGunGetGunId;
    private static Method getIAmmoOrNull, iAmmoGetAmmoId;

    private TaczMarketCompat() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MOD_ID);
        return loaded;
    }

    public static boolean isActive() {
        return MarketConfig.TACZ_ENABLED.get() && isModLoaded() && resolve();
    }

    private static synchronized boolean resolve() {
        if (resolved) return getAllCommonGunIndex != null;
        resolved = true;
        try {
            ClassLoader cl = TaczMarketCompat.class.getClassLoader();
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI", false, cl);
            getAllCommonGunIndex = api.getMethod("getAllCommonGunIndex");
            getAllCommonAmmoIndex = api.getMethod("getAllCommonAmmoIndex");

            Class<?> gunIndex = Class.forName("com.tacz.guns.resource.index.CommonGunIndex", false, cl);
            gunIndexGetType = gunIndex.getMethod("getType");
            gunIndexGetGunData = gunIndex.getMethod("getGunData");

            Class<?> gunData = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData", false, cl);
            gunDataGetRpm = gunData.getMethod("getRoundsPerMinute");
            gunDataGetBulletData = gunData.getMethod("getBulletData");

            Class<?> bullet = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData", false, cl);
            bulletGetDamage = bullet.getMethod("getDamageAmount");
            bulletGetAmount = bullet.getMethod("getBulletAmount");
            bulletGetPierce = bullet.getMethod("getPierce");
            bulletGetExtraDamage = bullet.getMethod("getExtraDamage");

            Class<?> extra = Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage", false, cl);
            extraGetArmorIgnore = extra.getMethod("getArmorIgnore");

            Class<?> gunB = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder", false, cl);
            gunBuilderCreate = gunB.getMethod("create");
            gunBuilderSetId = gunB.getMethod("setId", ResourceLocation.class);
            gunBuilderSetAmmoCount = gunB.getMethod("setAmmoCount", int.class);
            gunBuilderBuild = gunB.getMethod("build");

            Class<?> ammoB = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder", false, cl);
            ammoBuilderCreate = ammoB.getMethod("create");
            ammoBuilderSetId = ammoB.getMethod("setId", ResourceLocation.class);
            ammoBuilderSetCount = ammoB.getMethod("setCount", int.class);
            ammoBuilderBuild = ammoB.getMethod("build");

            Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun", false, cl);
            getIGunOrNull = iGun.getMethod("getIGunOrNull", ItemStack.class);
            iGunGetGunId = iGun.getMethod("getGunId", ItemStack.class);

            Class<?> iAmmo = Class.forName("com.tacz.guns.api.item.IAmmo", false, cl);
            getIAmmoOrNull = iAmmo.getMethod("getIAmmoOrNull", ItemStack.class);
            iAmmoGetAmmoId = iAmmo.getMethod("getAmmoId", ItemStack.class);
            return true;
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("TACZ is installed but its API did not resolve - guns will "
                    + "not be priced. This usually means a TACZ version whose API moved: {}", t.toString());
            getAllCommonGunIndex = null;
            return false;
        }
    }

    /** The gun id in a stack's NBT, or empty when this is not a TACZ gun. */
    public static Optional<ResourceLocation> gunIdOf(ItemStack stack) {
        if (stack.isEmpty() || !isModLoaded() || !resolve()) return Optional.empty();
        try {
            Object gun = getIGunOrNull.invoke(null, stack);
            if (gun == null) return Optional.empty();
            Object id = iGunGetGunId.invoke(gun, stack);
            return Optional.ofNullable((ResourceLocation) id);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public static Optional<ResourceLocation> ammoIdOf(ItemStack stack) {
        if (stack.isEmpty() || !isModLoaded() || !resolve()) return Optional.empty();
        try {
            Object ammo = getIAmmoOrNull.invoke(null, stack);
            if (ammo == null) return Optional.empty();
            Object id = iAmmoGetAmmoId.invoke(ammo, stack);
            return Optional.ofNullable((ResourceLocation) id);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public record GunEntry(ResourceLocation id, String type, int price) {}

    /**
     * Every installed gun, priced from its own ballistics.
     *
     * Deriving the price rather than hardcoding a list is the whole point: a pack can swap
     * gun packs and the shop follows, instead of silently selling nothing.
     */
    public static List<GunEntry> listGuns() {
        List<GunEntry> out = new ArrayList<>();
        if (!isActive()) return out;
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> all =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonGunIndex.invoke(null);
            for (Map.Entry<ResourceLocation, Object> e : all) {
                Integer price = priceGun(e.getValue());
                if (price == null) continue;
                String type = String.valueOf(gunIndexGetType.invoke(e.getValue()));
                out.add(new GunEntry(e.getKey(), type, price));
            }
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("Could not enumerate TACZ guns: {}", t.toString());
        }
        out.sort((a, b) -> Integer.compare(a.price(), b.price()));
        return out;
    }

    public static List<ResourceLocation> listAmmo() {
        List<ResourceLocation> out = new ArrayList<>();
        if (!isActive()) return out;
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> all =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonAmmoIndex.invoke(null);
            for (Map.Entry<ResourceLocation, Object> e : all) out.add(e.getKey());
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("Could not enumerate TACZ ammo: {}", t.toString());
        }
        out.sort(ResourceLocation::compareTo);
        return out;
    }

    /** Price for a gun id, looked up fresh from its index. Null when unknown. */
    public static Integer priceOfGun(ResourceLocation id) {
        if (!isActive()) return null;
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> all =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonGunIndex.invoke(null);
            for (Map.Entry<ResourceLocation, Object> e : all) {
                if (e.getKey().equals(id)) return priceGun(e.getValue());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Sustained damage output, plus separate premiums for the two things that decide
     * whether a round beats armour. Pierce and armour-ignore are priced apart from raw
     * damage on purpose: in a pack with plate carriers those are what a weapon is actually
     * bought for, and folding them into a damage number would price an armour-piercing
     * rifle the same as a hard-hitting shotgun.
     */
    private static Integer priceGun(Object index) {
        try {
            Object gunData = gunIndexGetGunData.invoke(index);
            Object bullet = gunDataGetBulletData.invoke(gunData);
            float damage = ((Number) bulletGetDamage.invoke(bullet)).floatValue();
            int pellets = Math.max(1, ((Number) bulletGetAmount.invoke(bullet)).intValue());
            int rpm = Math.max(1, ((Number) gunDataGetRpm.invoke(gunData)).intValue());
            int pierce = Math.max(0, ((Number) bulletGetPierce.invoke(bullet)).intValue());

            float armorIgnore = 0f;
            Object extra = bulletGetExtraDamage.invoke(bullet);
            if (extra != null) {
                Object ai = extraGetArmorIgnore.invoke(extra);
                if (ai != null) armorIgnore = ((Number) ai).floatValue();
            }

            double dps = damage * pellets * (rpm / 60.0);
            double base = 900 + dps * 260 + pierce * 1500 + armorIgnore * 6000;

            String type = String.valueOf(gunIndexGetType.invoke(index)).toLowerCase();
            base *= switch (type) {
                case "pistol" -> 0.55;
                case "smg" -> 0.80;
                case "shotgun" -> 0.85;
                case "sniper" -> 1.35;
                case "mg" -> 1.60;
                case "rpg" -> 2.50;
                default -> 1.00;   // rifle and anything a pack invents
            };
            base *= MarketConfig.TACZ_PRICE_SCALE.get();
            // Round to the nearest hundred so prices read as prices, not as measurements.
            long rounded = Math.round(base / 100.0) * 100L;
            return (int) Math.max(100L, Math.min(Integer.MAX_VALUE, rounded));
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack makeGun(ResourceLocation id) {
        if (!isActive()) return ItemStack.EMPTY;
        try {
            Object b = gunBuilderCreate.invoke(null);
            b = gunBuilderSetId.invoke(b, id);
            // Sold empty: ammunition is a separate purchase, which is the point of the
            // ammo tab existing at all.
            b = gunBuilderSetAmmoCount.invoke(b, 0);
            Object stack = gunBuilderBuild.invoke(b);
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack makeAmmo(ResourceLocation id, int count) {
        if (!isActive()) return ItemStack.EMPTY;
        try {
            Object b = ammoBuilderCreate.invoke(null);
            b = ammoBuilderSetId.invoke(b, id);
            b = ammoBuilderSetCount.invoke(b, count);
            Object stack = ammoBuilderBuild.invoke(b);
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
