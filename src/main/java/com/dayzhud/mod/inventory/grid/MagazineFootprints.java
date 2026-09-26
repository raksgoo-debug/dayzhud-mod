package com.dayzhud.mod.inventory.grid;

import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaCZ: Magazines uses ONE item, {@code taczmagazines:magazine}, for every gun's magazine -
 * a Glock's as well as an M4's - so its size can't come from the item id (2.13.7 made them all
 * 1x2). The stack carries its magazine family in NBT ({@code MagazineFamily}); TaCZ:
 * Magazines' own {@code MagazineFamilySystem.getRepresentativeGun(family)} names a gun that
 * takes it, and that gun's TACZ type decides: a pistol's standard magazine is 1x1, anything
 * else - rifles, SMGs, and extended pistol magazines - stands 1x2.
 *
 * Both calls only read data both sides have, so the server and client agree. Resolved
 * reflectively (TaCZ: Magazines is optional); anything unresolvable is 1x2, as before.
 */
final class MagazineFootprints {

    static final String MAGAZINE = "taczmagazines:magazine";
    private static final Footprint STANDING = new Footprint(1, 2);

    private static boolean resolved;
    private static Method representativeGun, isExtendedFamily;
    /** Per family, once its gun's type is known (families are fixed for a session). */
    private static final Map<String, Footprint> BY_FAMILY = new ConcurrentHashMap<>();

    private MagazineFootprints() {}

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        if (!ModList.get().isLoaded("taczmagazines")) return;
        try {
            Class<?> fam = Class.forName("com.raiiiden.taczmagazines.magazine.MagazineFamilySystem");
            representativeGun = fam.getMethod("getRepresentativeGun", String.class);
            isExtendedFamily = fam.getMethod("isExtendedFamily", String.class);
        } catch (Throwable t) {
            representativeGun = null;
        }
    }

    static Footprint of(ItemStack stack) {
        String family = stack.hasTag() ? stack.getTag().getString("MagazineFamily") : "";
        if (family.isEmpty()) return STANDING;
        Footprint cached = BY_FAMILY.get(family);
        if (cached != null) return cached;
        resolve();
        if (representativeGun == null) return STANDING;
        try {
            Object gun = representativeGun.invoke(null, family);
            if (!(gun instanceof ResourceLocation gunId)) return STANDING;   // not discovered yet: ask again later
            String type = TaczMarketCompat.gunTypeOfId(gunId).orElse(null);
            if (type == null) return STANDING;                               // gun index not loaded yet
            boolean extended = Boolean.TRUE.equals(isExtendedFamily.invoke(null, family));
            Footprint fp = "pistol".equals(type) && !extended ? Footprint.SINGLE : STANDING;
            BY_FAMILY.put(family, fp);
            return fp;
        } catch (Throwable t) {
            return STANDING;
        }
    }
}
