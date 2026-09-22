package com.dayzhud.mod.inventory;

import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * What may go in each of the four loadout slots (PRIMARY/SECONDARY/HOLSTER/SHEATH), and the
 * label/order the screen draws them in.
 *
 * The restriction is entirely gun-TYPE based for the three gun slots, via
 * {@link TaczMarketCompat#gunTypeOf}, and tag-based for the melee slot - see
 * {@link WeaponSlotConfig} for why both are configurable rather than hardcoded here.
 */
public enum WeaponSlots {

    PRIMARY("PRIMARY", WeaponSlotConfig.PRIMARY_GUN_TYPES),
    SECONDARY("SECONDARY", WeaponSlotConfig.SECONDARY_GUN_TYPES),
    HOLSTER("HOLSTER", WeaponSlotConfig.HOLSTER_GUN_TYPES),
    SHEATH("SHEATH", null);

    public final String label;
    private final net.minecraftforge.common.ForgeConfigSpec.ConfigValue<List<? extends String>> gunTypes;

    WeaponSlots(String label,
                net.minecraftforge.common.ForgeConfigSpec.ConfigValue<List<? extends String>> gunTypes) {
        this.label = label;
        this.gunTypes = gunTypes;
    }

    /** In screen order, left to right. */
    public static final WeaponSlots[] ORDER = {PRIMARY, SECONDARY, HOLSTER, SHEATH};

    /**
     * Whether {@code stack} may sit in this slot. Empty stacks are always allowed - that is
     * what lets a slot be cleared.
     */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!WeaponSlotConfig.ENFORCE.get()) return true;

        Optional<String> gunType = TaczMarketCompat.gunTypeOf(stack);
        if (this == SHEATH) {
            // Never a gun in the sheath, regardless of the tag - see WeaponSlotConfig's note.
            if (gunType.isPresent()) return false;
            return stack.is(sheathTag());
        }
        // The other three slots are gun-only: nothing that isn't a recognised TACZ gun type
        // gets a free pass just because the type lookup came back empty (mod missing, or a
        // gun id the loaded pack doesn't know) - see the config comment on why that is not a
        // relaxation worth making silently.
        if (gunType.isEmpty()) return false;
        return gunTypes.get().contains(gunType.get());
    }

    private static TagKey<net.minecraft.world.item.Item> sheathTag() {
        ResourceLocation id = ResourceLocation.tryParse(WeaponSlotConfig.SHEATH_TAG.get());
        if (id == null) id = new ResourceLocation("dayzhud", "sheath_weapons");
        return ItemTags.create(id);
    }
}
