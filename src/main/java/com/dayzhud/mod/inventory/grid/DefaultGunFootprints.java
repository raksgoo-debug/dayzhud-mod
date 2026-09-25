package com.dayzhud.mod.inventory.grid;

import java.util.Map;

/**
 * Per-gun footprints for TACZ's default gun pack, sized from each gun's real proportions.
 *
 * Why a built-in table rather than measuring in game: footprints decide what fits where, and
 * that is decided on the SERVER - which never loads gun models (a dedicated server has no
 * client assets at all). So a gun's size has to be data both sides already agree on.
 *
 * How the numbers were made (2.13.4, offline, from the tacz-1.20.1-1.1.8-hotfix jar): each
 * gun's full model was rendered side-on in the configuration TACZ draws with no attachments
 * (standard mag, default handguard and iron sights, carry handle shown, adapter-mounted stocks
 * hidden) and measured by its VISIBLE pixels - the same box the in-game renderer now fits and
 * centres (TaczFlatGunRenderer, 2.13.4). Then:
 * <ul>
 *   <li>Everything but pistols is exactly 2 tall. Width is the gun's length at one shared
 *       scale - 2 px per model unit, 9 units per cell, capped so the gun's height fits the
 *       2-cell box - rounded to the NEAREST cell after the 2 px inset each side, 2 to 9 wide.
 *       The gun is then drawn filling that box, so rounding changes its drawn size by at most
 *       about half a cell rather than leaving up to a cell empty.</li>
 *   <li>Pistols are 1 tall and fill that height; width is what that makes them, at least 2.</li>
 * </ul>
 *
 * These are the sizes with NO attachments. GunSizes grows them for what's fitted (2.13.5).
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
            Map.entry("tacz:m1014", new Footprint(5, 2)),
            Map.entry("tacz:m107", new Footprint(6, 2)),
            Map.entry("tacz:m16a1", new Footprint(6, 2)),
            Map.entry("tacz:m16a4", new Footprint(6, 2)),
            Map.entry("tacz:m1911", new Footprint(2, 1)),
            Map.entry("tacz:m249", new Footprint(6, 2)),
            Map.entry("tacz:m320", new Footprint(3, 2)),
            Map.entry("tacz:m4a1", new Footprint(4, 2)),
            Map.entry("tacz:m700", new Footprint(6, 2)),
            Map.entry("tacz:m870", new Footprint(5, 2)),
            Map.entry("tacz:m95", new Footprint(7, 2)),
            Map.entry("tacz:m9a4", new Footprint(2, 1)),
            Map.entry("tacz:minigun", new Footprint(6, 2)),
            Map.entry("tacz:mk14", new Footprint(5, 2)),
            Map.entry("tacz:p320", new Footprint(2, 1)),
            Map.entry("tacz:p90", new Footprint(3, 2)),
            Map.entry("tacz:qbz_191", new Footprint(5, 2)),
            Map.entry("tacz:qbz_95", new Footprint(4, 2)),
            Map.entry("tacz:rhino357", new Footprint(2, 1)),
            Map.entry("tacz:rpg7", new Footprint(5, 2)),
            Map.entry("tacz:rpk", new Footprint(4, 2)),
            Map.entry("tacz:scar_h", new Footprint(5, 2)),
            Map.entry("tacz:scar_l", new Footprint(4, 2)),
            Map.entry("tacz:sks_tactical", new Footprint(5, 2)),
            Map.entry("tacz:spas_12", new Footprint(5, 2)),
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
