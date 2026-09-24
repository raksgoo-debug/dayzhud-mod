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

    private DefaultGunFootprints() {}
}
