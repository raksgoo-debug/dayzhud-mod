package com.dayzhud.mod.inventory;

import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * TACZ ships its own flat, pre-rendered, already-alpha'd icon per gun - at
 * {@code textures/gun/hud/<gunid>.png} inside its default gun pack, almost certainly what
 * powers TACZ's own weapon-select wheel. They're not reachable through the normal Minecraft
 * resource system: that path lives under a nested {@code custom/tacz_default_gun/...} folder
 * inside TACZ's jar, which is TACZ's own bespoke gun-pack loader, not a standard
 * {@code assets/<namespace>/textures/...} path the vanilla resource manager would ever find.
 * So the 55 icons from the shipped {@code tacz-1.20.1-1.1.8-hotfix.jar} are bundled as this
 * mod's own assets instead - copied once, not generated - under {@code textures/gui/tacz_hud/}.
 *
 * This is why the big-icon renders looked stretched: without these, the only way to show a
 * gun bigger than 1x1 was to scale up TACZ's normal 3D in-hand-style GUI render, which reads
 * fine at its native angle and size but distorts once forced non-uniformly into a wide, short
 * rectangle. These icons are already flat and already correctly proportioned - no rotation,
 * no stretch, no tint needed, just drawn at whatever size fits (see the two render sites:
 * {@code TarkovInventoryScreen#drawBigGridIcon} and the loadout boxes).
 *
 * Four files needed renaming to match their actual gun id (the id {@link
 * TaczMarketCompat#gunIdOf} reports), which is the id used for the lookup here, not the
 * item id (every TACZ gun shares one item; the specific gun is NBT):
 * {@code b93r_hud.png -> b93r.png}, {@code spr15hb_hud.png -> spr15hb.png}, {@code
 * springfield1873_hud.png -> springfield1873.png}, {@code timeless50_hud.png -> timeless50.png}.
 *
 * Only covers guns from TACZ's own default pack - a custom/third-party gun pack's guns fall
 * through to the 3D-render fallback (now fit uniformly rather than stretched, so at worst
 * they're smaller within their footprint than a matched icon would be, never distorted).
 * Extending this to another pack means copying that pack's own hud textures the same way and
 * adding their sizes below - there's no way to discover them automatically from here.
 */
public final class TaczHudIcons {

    private TaczHudIcons() {}

    /** gun id -> {nativeWidth, nativeHeight}, measured directly off each bundled PNG - sizes
     *  and aspect ratios genuinely vary per gun (most share 384x128 or 180x60, a handful of
     *  larger guns don't), so this can't be a shared constant. */
    private static final Map<String, int[]> ICONS = Map.ofEntries(
            Map.entry("aa12", new int[]{384, 128}),
            Map.entry("ai_awp", new int[]{180, 60}),
            Map.entry("ak47", new int[]{384, 128}),
            Map.entry("aug", new int[]{384, 128}),
            Map.entry("b93r", new int[]{384, 128}),
            Map.entry("cz75", new int[]{1111, 371}),
            Map.entry("db_long", new int[]{852, 284}),
            Map.entry("db_short", new int[]{384, 128}),
            Map.entry("deagle", new int[]{180, 60}),
            Map.entry("deagle_golden", new int[]{384, 128}),
            Map.entry("fn_evolys", new int[]{384, 128}),
            Map.entry("fn_fal", new int[]{384, 128}),
            Map.entry("g36k", new int[]{384, 128}),
            Map.entry("glock_17", new int[]{180, 60}),
            Map.entry("hk416a5", new int[]{384, 128}),
            Map.entry("hk416d", new int[]{384, 128}),
            Map.entry("hk_g3", new int[]{1178, 393}),
            Map.entry("hk_mk23", new int[]{384, 128}),
            Map.entry("hk_mp5a5", new int[]{384, 128}),
            Map.entry("kar98", new int[]{385, 128}),
            Map.entry("lonetrail", new int[]{806, 258}),
            Map.entry("m1014", new int[]{384, 128}),
            Map.entry("m107", new int[]{384, 128}),
            Map.entry("m16a1", new int[]{384, 128}),
            Map.entry("m16a4", new int[]{384, 128}),
            Map.entry("m1911", new int[]{384, 128}),
            Map.entry("m249", new int[]{180, 60}),
            Map.entry("m320", new int[]{384, 128}),
            Map.entry("m4a1", new int[]{384, 128}),
            Map.entry("m700", new int[]{384, 128}),
            Map.entry("m870", new int[]{384, 128}),
            Map.entry("m95", new int[]{1547, 516}),
            Map.entry("m9a4", new int[]{180, 60}),
            Map.entry("minigun", new int[]{986, 287}),
            Map.entry("mk14", new int[]{384, 128}),
            Map.entry("p320", new int[]{384, 128}),
            Map.entry("p90", new int[]{384, 128}),
            Map.entry("qbz_191", new int[]{384, 128}),
            Map.entry("qbz_95", new int[]{384, 128}),
            Map.entry("rhino357", new int[]{1299, 433}),
            Map.entry("rpg7", new int[]{384, 128}),
            Map.entry("rpk", new int[]{384, 128}),
            Map.entry("scar_h", new int[]{981, 327}),
            Map.entry("scar_l", new int[]{384, 128}),
            Map.entry("sks_tactical", new int[]{384, 128}),
            Map.entry("spas_12", new int[]{384, 128}),
            Map.entry("spr15hb", new int[]{384, 128}),
            Map.entry("springfield1873", new int[]{384, 128}),
            Map.entry("taurus500", new int[]{180, 60}),
            Map.entry("taurus943", new int[]{384, 128}),
            Map.entry("timeless50", new int[]{384, 128}),
            Map.entry("type_81", new int[]{384, 128}),
            Map.entry("ump45", new int[]{384, 128}),
            Map.entry("uzi", new int[]{180, 60}),
            Map.entry("vector45", new int[]{1537, 512})
    );

    public record Icon(ResourceLocation texture, int width, int height) {}

    /** The bundled flat icon for this stack's specific gun, if we have one. Empty for
     *  anything that isn't a TACZ gun, or a gun from a pack other than TACZ's own default. */
    public static Optional<Icon> iconFor(ItemStack stack) {
        Optional<ResourceLocation> gunId = TaczMarketCompat.gunIdOf(stack);
        if (gunId.isEmpty()) return Optional.empty();
        String path = gunId.get().getPath();
        int[] size = ICONS.get(path);
        if (size == null) return Optional.empty();
        return Optional.of(new Icon(
                new ResourceLocation("dayzhud", "textures/gui/tacz_hud/" + path + ".png"),
                size[0], size[1]));
    }
}
