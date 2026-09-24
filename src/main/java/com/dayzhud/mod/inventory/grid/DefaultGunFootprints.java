package com.dayzhud.mod.inventory.grid;

import java.util.Map;

/**
 * Per-gun footprints for TACZ's default gun pack, sized from each gun's real proportions.
 *
 * Why a built-in table rather than measuring in game: footprints decide what fits where, and
 * that is decided on the SERVER - which never loads gun models (a dedicated server has no
 * client assets at all). So a gun's size has to be data both sides already agree on.
 *
 * How the numbers were made (offline, from the tacz-1.20.1-1.1.8-hotfix jar): each gun's
 * low-detail model - which shows its default configuration - was measured side-on over every
 * vertex (the same all-geometry measure the in-game renderer uses to fit it), hiding only
 * non-default variants (extended mags, alternative "oem_" stocks) and non-geometry bones.
 * Then one shared scale for every gun, 9 model units per cell, so guns sit at a consistent
 * real-world size relative to each other like a Tarkov grid: cells = ceil(size / 9 - 0.25)
 * (the 0.25 lets a gun shrink ~5% rather than claim a whole extra cell). Then the pack's
 * rules: pistols at least 2 wide, everything else at least 2 tall, nothing over 9x3 (the
 * inventory is 9x3).
 *
 * Priority in ItemFootprints: a config gunIdFootprints entry beats this table, which beats
 * the per-type defaults - so any gun here can still be overridden, and guns from other packs
 * fall back to their type's size.
 */
public final class DefaultGunFootprints {

    public static final Map<String, Footprint> TABLE = Map.ofEntries(
            Map.entry("tacz:aa12", new Footprint(5, 2)),
            Map.entry("tacz:ai_awp", new Footprint(7, 2)),
            Map.entry("tacz:ak47", new Footprint(5, 2)),
            Map.entry("tacz:aug", new Footprint(4, 2)),
            Map.entry("tacz:b93r", new Footprint(2, 1)),
            Map.entry("tacz:cz75", new Footprint(2, 1)),
            Map.entry("tacz:db_long", new Footprint(5, 2)),
            Map.entry("tacz:db_short", new Footprint(3, 2)),
            Map.entry("tacz:deagle", new Footprint(2, 1)),
            Map.entry("tacz:deagle_golden", new Footprint(2, 1)),
            Map.entry("tacz:fn_evolys", new Footprint(5, 2)),
            Map.entry("tacz:fn_fal", new Footprint(6, 2)),
            Map.entry("tacz:g36k", new Footprint(5, 2)),
            Map.entry("tacz:glock_17", new Footprint(2, 1)),
            Map.entry("tacz:hk416d", new Footprint(4, 2)),
            Map.entry("tacz:hk_g3", new Footprint(6, 2)),
            Map.entry("tacz:hk_mk23", new Footprint(2, 1)),
            Map.entry("tacz:hk_mp5a5", new Footprint(3, 2)),
            Map.entry("tacz:kar98", new Footprint(6, 2)),
            Map.entry("tacz:lonetrail", new Footprint(3, 1)),
            Map.entry("tacz:m1014", new Footprint(6, 2)),
            Map.entry("tacz:m107", new Footprint(7, 3)),
            Map.entry("tacz:m16a1", new Footprint(6, 2)),
            Map.entry("tacz:m16a4", new Footprint(6, 2)),
            Map.entry("tacz:m1911", new Footprint(2, 1)),
            Map.entry("tacz:m249", new Footprint(6, 2)),
            Map.entry("tacz:m320", new Footprint(2, 2)),
            Map.entry("tacz:m4a1", new Footprint(5, 2)),
            Map.entry("tacz:m700", new Footprint(6, 2)),
            Map.entry("tacz:m870", new Footprint(5, 2)),
            Map.entry("tacz:m95", new Footprint(7, 2)),
            Map.entry("tacz:m9a4", new Footprint(2, 1)),
            Map.entry("tacz:minigun", new Footprint(6, 3)),
            Map.entry("tacz:mk14", new Footprint(5, 2)),
            Map.entry("tacz:p320", new Footprint(2, 1)),
            Map.entry("tacz:p90", new Footprint(3, 2)),
            Map.entry("tacz:qbz_191", new Footprint(5, 2)),
            Map.entry("tacz:qbz_95", new Footprint(4, 2)),
            Map.entry("tacz:rhino357", new Footprint(2, 1)),
            Map.entry("tacz:rpg7", new Footprint(5, 2)),
            Map.entry("tacz:rpk", new Footprint(6, 2)),
            Map.entry("tacz:scar_h", new Footprint(4, 2)),
            Map.entry("tacz:scar_l", new Footprint(4, 2)),
            Map.entry("tacz:sks_tactical", new Footprint(6, 2)),
            Map.entry("tacz:spas_12", new Footprint(6, 2)),
            Map.entry("tacz:spr15hb", new Footprint(6, 2)),
            Map.entry("tacz:springfield1873", new Footprint(7, 2)),
            Map.entry("tacz:taurus500", new Footprint(2, 1)),
            Map.entry("tacz:taurus943", new Footprint(2, 1)),
            Map.entry("tacz:timeless50", new Footprint(2, 1)),
            Map.entry("tacz:type_81", new Footprint(5, 2)),
            Map.entry("tacz:ump45", new Footprint(4, 2)),
            Map.entry("tacz:uzi", new Footprint(3, 2)),
            Map.entry("tacz:vector45", new Footprint(3, 2))
    );

    /**
     * Each gun's side-on length in model units, from the same default-configuration models the
     * table above was sized from. The renderer draws every non-pistol gun at one shared scale -
     * 2 px per unit, i.e. 9 units per 18-px cell - using this to convert TACZ's in-game model
     * units, so a gun's drawn size reflects its real length relative to every other gun
     * instead of how full its footprint happens to be. (Checked against published lengths:
     * the models are within about 7% of real proportions, e.g. Glock/AK 0.24 vs 0.23,
     * AWP/AK 1.39 vs 1.36; the Uzi's stock is modelled folded.)
     */
    public static final Map<String, Float> LENGTH = Map.ofEntries(
            Map.entry("tacz:aa12", 41.8f),
            Map.entry("tacz:ai_awp", 57.1f),
            Map.entry("tacz:ak47", 41.2f),
            Map.entry("tacz:aug", 36.7f),
            Map.entry("tacz:b93r", 12.2f),
            Map.entry("tacz:cz75", 10.2f),
            Map.entry("tacz:db_long", 44.6f),
            Map.entry("tacz:db_short", 22.9f),
            Map.entry("tacz:deagle", 13.1f),
            Map.entry("tacz:deagle_golden", 13.1f),
            Map.entry("tacz:fn_evolys", 45.3f),
            Map.entry("tacz:fn_fal", 52.8f),
            Map.entry("tacz:g36k", 41.3f),
            Map.entry("tacz:glock_17", 9.8f),
            Map.entry("tacz:hk416d", 37.3f),
            Map.entry("tacz:hk_g3", 48.9f),
            Map.entry("tacz:hk_mk23", 12.7f),
            Map.entry("tacz:hk_mp5a5", 21.9f),
            Map.entry("tacz:kar98", 52.9f),
            Map.entry("tacz:lonetrail", 22.2f),
            Map.entry("tacz:m1014", 47.4f),
            Map.entry("tacz:m107", 62.0f),
            Map.entry("tacz:m16a1", 47.5f),
            Map.entry("tacz:m16a4", 47.5f),
            Map.entry("tacz:m1911", 10.4f),
            Map.entry("tacz:m249", 48.0f),
            Map.entry("tacz:m320", 16.8f),
            Map.entry("tacz:m4a1", 39.8f),
            Map.entry("tacz:m700", 47.4f),
            Map.entry("tacz:m870", 46.4f),
            Map.entry("tacz:m95", 63.3f),
            Map.entry("tacz:m9a4", 11.7f),
            Map.entry("tacz:minigun", 50.0f),
            Map.entry("tacz:mk14", 46.4f),
            Map.entry("tacz:p320", 9.3f),
            Map.entry("tacz:p90", 23.2f),
            Map.entry("tacz:qbz_191", 40.2f),
            Map.entry("tacz:qbz_95", 37.1f),
            Map.entry("tacz:rhino357", 13.2f),
            Map.entry("tacz:rpg7", 45.4f),
            Map.entry("tacz:rpk", 49.3f),
            Map.entry("tacz:scar_h", 38.1f),
            Map.entry("tacz:scar_l", 37.1f),
            Map.entry("tacz:sks_tactical", 48.4f),
            Map.entry("tacz:spas_12", 54.4f),
            Map.entry("tacz:spr15hb", 50.4f),
            Map.entry("tacz:springfield1873", 62.6f),
            Map.entry("tacz:taurus500", 18.7f),
            Map.entry("tacz:taurus943", 8.0f),
            Map.entry("tacz:timeless50", 10.4f),
            Map.entry("tacz:type_81", 46.2f),
            Map.entry("tacz:ump45", 34.1f),
            Map.entry("tacz:uzi", 22.5f),
            Map.entry("tacz:vector45", 27.2f)
    );

    private DefaultGunFootprints() {}
}
