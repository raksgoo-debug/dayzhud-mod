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
    private static Method gunDataGetAmmoId;
    private static Method gunDataGetAmmoAmount;
    private static Method gunDataGetWeight;
    private static Method gunDataGetFireModeSet;
    private static Method bulletGetSpeed;
    private static Method extraGetHeadshot;
    private static Method bulletGetDamage;
    private static Method bulletGetAmount;
    private static Method bulletGetPierce;
    private static Method bulletGetExtraDamage;
    private static Method extraGetArmorIgnore;

    private static Method gunBuilderCreate, gunBuilderSetId, gunBuilderSetAmmoCount, gunBuilderBuild;
    private static Method ammoBuilderCreate, ammoBuilderSetId, ammoBuilderSetCount, ammoBuilderBuild;
    private static Method attachBuilderCreate, attachBuilderSetId, attachBuilderBuild;
    private static Method getAllCommonAttachmentIndex, attachIndexGetType, attachIndexGetData,
            attachDataGetExtendedMagLevel;
    private static boolean gunPriceFailureLogged;

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
            gunDataGetAmmoId = gunData.getMethod("getAmmoId");

            Class<?> bullet = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData", false, cl);
            Class<?> extra = Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage", false, cl);
            bulletGetDamage = bullet.getMethod("getDamageAmount");
            bulletGetAmount = bullet.getMethod("getBulletAmount");
            bulletGetPierce = bullet.getMethod("getPierce");
            bulletGetExtraDamage = bullet.getMethod("getExtraDamage");

            extraGetArmorIgnore = extra.getMethod("getArmorIgnore");

            // Presentation-only extras, resolved after the classes they live on and allowed
            // to fail: a TACZ release renaming one of these should cost a line in the stat
            // card, not the whole gun-pricing integration.
            try {
                gunDataGetAmmoAmount = gunData.getMethod("getAmmoAmount");
                gunDataGetWeight = gunData.getMethod("getWeight");
                gunDataGetFireModeSet = gunData.getMethod("getFireModeSet");
                bulletGetSpeed = bullet.getMethod("getSpeed");
                extraGetHeadshot = extra.getMethod("getHeadShotMultiplier");
            } catch (NoSuchMethodException missing) {
                DayzHudMod.LOGGER.debug("TACZ stat-card extras unavailable: {}", missing.toString());
            }

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

            Class<?> attachB = Class.forName(
                    "com.tacz.guns.api.item.builder.AttachmentItemBuilder", false, cl);
            attachBuilderCreate = attachB.getMethod("create");
            attachBuilderSetId = attachB.getMethod("setId", ResourceLocation.class);
            attachBuilderBuild = attachB.getMethod("build");

            getAllCommonAttachmentIndex = api.getMethod("getAllCommonAttachmentIndex");
            Class<?> attachIndex = Class.forName(
                    "com.tacz.guns.resource.index.CommonAttachmentIndex", false, cl);
            attachIndexGetType = attachIndex.getMethod("getType");
            attachIndexGetData = attachIndex.getMethod("getData");
            Class<?> attachData = Class.forName(
                    "com.tacz.guns.resource.pojo.data.attachment.AttachmentData", false, cl);
            attachDataGetExtendedMagLevel = attachData.getMethod("getExtendedMagLevel");

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
        DayzHudMod.LOGGER.info("TACZ: {} gun(s) priced for the market", out.size());
        return out;
    }

    public record AttachmentEntry(ResourceLocation id, String type, int price) {}

    /**
     * Attachments, priced by type and extended-mag level.
     *
     * TACZ's attachment data carries no ballistics of its own - the effect lives in a
     * modifier map keyed by strings a third party cannot safely interpret - so this is a
     * type table rather than a derivation. Honest about what it is, and overridable per id.
     */
    public static List<AttachmentEntry> listAttachments() {
        List<AttachmentEntry> out = new ArrayList<>();
        if (!isActive() || getAllCommonAttachmentIndex == null) return out;
        int base = MarketConfig.TACZ_ATTACHMENT_PRICE.get();
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> all =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonAttachmentIndex.invoke(null);
            for (Map.Entry<ResourceLocation, Object> e : all) {
                String type = String.valueOf(attachIndexGetType.invoke(e.getValue())).toLowerCase();
                double price = base * switch (type) {
                    case "scope" -> 2.2;
                    case "muzzle" -> 1.4;
                    case "stock" -> 0.9;
                    case "grip" -> 0.7;
                    case "laser" -> 1.1;
                    case "extended_mag" -> 1.0;
                    default -> 1.0;
                };
                try {
                    Object data = attachIndexGetData.invoke(e.getValue());
                    int mag = ((Number) attachDataGetExtendedMagLevel.invoke(data)).intValue();
                    if (mag > 0) price *= 1.0 + mag * 0.6;
                } catch (Throwable ignored) {
                }
                price *= MarketConfig.TACZ_PRICE_SCALE.get();
                out.add(new AttachmentEntry(e.getKey(), type,
                        (int) Math.max(100, Math.round(price / 100.0) * 100)));
            }
        } catch (Throwable t) {
            DayzHudMod.LOGGER.warn("Could not enumerate TACZ attachments: {}", t.toString());
        }
        out.sort((a, b) -> Integer.compare(a.price(), b.price()));
        return out;
    }

    public static ItemStack makeAttachment(ResourceLocation id) {
        if (!isActive() || attachBuilderCreate == null) return ItemStack.EMPTY;
        try {
            Object b = attachBuilderCreate.invoke(null);
            b = attachBuilderSetId.invoke(b, id);
            Object stack = attachBuilderBuild.invoke(b);
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
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
            // Log ONCE rather than per gun. A silent null here drops the whole WEAPONS tab
            // with no trace anywhere, which is exactly how it went unnoticed the first time.
            if (!gunPriceFailureLogged) {
                gunPriceFailureLogged = true;
                DayzHudMod.LOGGER.warn("TACZ gun pricing failed; weapons will be missing "
                        + "from the market", t);
            }
            return null;
        }
    }

    /**
     * Price of ONE round, worked out from the hardest-hitting gun that chambers it.
     *
     * TACZ's ammo index carries no ballistics of its own - damage lives on the gun's bullet
     * data - so without this every calibre in the game costs the same, which is what shipped
     * first and made a shotgun shell cost the same as .338 Lapua.
     */
    public static Integer priceOfAmmo(ResourceLocation ammoId) {
        if (!isActive()) return null;
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> all =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonGunIndex.invoke(null);
            float best = 0f;
            int bestPierce = 0;
            float bestIgnore = 0f;
            for (Map.Entry<ResourceLocation, Object> e : all) {
                Object gunData = gunIndexGetGunData.invoke(e.getValue());
                Object id = gunDataGetAmmoId.invoke(gunData);
                if (!ammoId.equals(id)) continue;
                Object bullet = gunDataGetBulletData.invoke(gunData);
                float damage = ((Number) bulletGetDamage.invoke(bullet)).floatValue();
                int pellets = Math.max(1, ((Number) bulletGetAmount.invoke(bullet)).intValue());
                float total = damage * pellets;
                if (total <= best) continue;
                best = total;
                bestPierce = Math.max(0, ((Number) bulletGetPierce.invoke(bullet)).intValue());
                Object extra = bulletGetExtraDamage.invoke(bullet);
                bestIgnore = 0f;
                if (extra != null) {
                    Object ai = extraGetArmorIgnore.invoke(extra);
                    if (ai != null) bestIgnore = ((Number) ai).floatValue();
                }
            }
            if (best <= 0f) return null;
            double price = 12 + best * 22 + bestPierce * 40 + bestIgnore * 220;
            price *= MarketConfig.TACZ_PRICE_SCALE.get();
            return (int) Math.max(5, Math.round(price / 5.0) * 5);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * One-line status for /market debug. Exists because "no guns in the shop" and "guns in
     * the shop the UI is not showing" look identical from the outside, and telling them apart
     * by reasoning cost a round already.
     */
    public static List<String> debugReport() {
        List<String> out = new ArrayList<>();
        out.add("tacz loaded: " + isModLoaded() + ", config enabled: " + MarketConfig.TACZ_ENABLED.get());
        if (!isModLoaded()) return out;
        out.add("api resolved: " + resolve());
        if (!resolve()) return out;
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> guns =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonGunIndex.invoke(null);
            out.add("gun index entries: " + guns.size());
            int priced = 0;
            String sample = "none";
            for (Map.Entry<ResourceLocation, Object> e : guns) {
                Integer p = priceGun(e.getValue());
                if (p == null) continue;
                priced++;
                if (sample.equals("none")) sample = e.getKey() + " = " + p;
            }
            out.add("guns priced ok: " + priced + " (first: " + sample + ")");
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> ammo =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonAmmoIndex.invoke(null);
            out.add("ammo index entries: " + ammo.size());
            for (Map.Entry<ResourceLocation, Object> e : ammo) {
                Integer p = priceOfAmmo(e.getKey());
                out.add("  ammo " + e.getKey() + " -> " + (p == null ? "NO GUN CHAMBERS IT" : p));
                break;
            }
        } catch (Throwable t) {
            out.add("index walk threw: " + t);
        }
        return out;
    }

    /** Everything the details panel wants about one gun. Client-safe. */
    public record GunStats(float damage, int pellets, int rpm, int pierce, float armorIgnore,
                           float speed, float headshot, int magazine, String ammoId,
                           String fireModes, float weight) {}

    /** Stats for a gun stack, or null when it is not a TACZ gun. */
    public static GunStats statsOf(ItemStack stack) {
        ResourceLocation id = gunIdOf(stack).orElse(null);
        if (id == null || !isActive()) return null;
        try {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<ResourceLocation, Object>> all =
                    (Set<Map.Entry<ResourceLocation, Object>>) getAllCommonGunIndex.invoke(null);
            for (Map.Entry<ResourceLocation, Object> e : all) {
                if (!e.getKey().equals(id)) continue;
                Object gunData = gunIndexGetGunData.invoke(e.getValue());
                Object bullet = gunDataGetBulletData.invoke(gunData);

                float damage = ((Number) bulletGetDamage.invoke(bullet)).floatValue();
                int pellets = Math.max(1, ((Number) bulletGetAmount.invoke(bullet)).intValue());
                int rpm = ((Number) gunDataGetRpm.invoke(gunData)).intValue();
                int pierce = ((Number) bulletGetPierce.invoke(bullet)).intValue();

                float ignore = 0f;
                float headshot = 0f;
                Object extra = bulletGetExtraDamage.invoke(bullet);
                if (extra != null) {
                    Object ai = extraGetArmorIgnore.invoke(extra);
                    if (ai != null) ignore = ((Number) ai).floatValue();
                    if (extraGetHeadshot != null) {
                        Object hs = extraGetHeadshot.invoke(extra);
                        if (hs != null) headshot = ((Number) hs).floatValue();
                    }
                }
                float speed = bulletGetSpeed == null ? 0f
                        : ((Number) bulletGetSpeed.invoke(bullet)).floatValue();
                int mag = gunDataGetAmmoAmount == null ? 0
                        : ((Number) gunDataGetAmmoAmount.invoke(gunData)).intValue();
                float weight = gunDataGetWeight == null ? 0f
                        : ((Number) gunDataGetWeight.invoke(gunData)).floatValue();
                Object ammo = gunDataGetAmmoId.invoke(gunData);
                String modes = "";
                if (gunDataGetFireModeSet != null) {
                    Object set = gunDataGetFireModeSet.invoke(gunData);
                    if (set instanceof List<?> list && !list.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Object m : list) {
                            if (sb.length() > 0) sb.append('/');
                            sb.append(String.valueOf(m).toUpperCase(java.util.Locale.ROOT));
                        }
                        modes = sb.toString();
                    }
                }
                return new GunStats(damage, pellets, rpm, pierce, ignore, speed, headshot, mag,
                        ammo == null ? null : ammo.toString(), modes, weight);
            }
        } catch (Throwable ignored) {
        }
        return null;
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
