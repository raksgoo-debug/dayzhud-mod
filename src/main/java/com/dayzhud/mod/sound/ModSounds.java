package com.dayzhud.mod.sound;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * This mod's sound events.
 *
 * Each name here must match a key in {@code assets/dayzhud/sounds.json}, which is what maps
 * an event to the actual .ogg files. Minecraft only reads **OGG Vorbis** - wav, mp3 and m4a
 * are silently ignored, which is the usual reason a new sound "does nothing".
 *
 * {@code inventory_move} deliberately lists three files in sounds.json. Minecraft picks one
 * at random per play, so shuffling items doesn't turn into the same click on a metronome.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DayzHudMod.MOD_ID);

    public static final RegistryObject<SoundEvent> INVENTORY_OPEN = register("inventory_open");
    public static final RegistryObject<SoundEvent> INVENTORY_CLOSE = register("inventory_close");
    public static final RegistryObject<SoundEvent> INVENTORY_MOVE = register("inventory_move");
    public static final RegistryObject<SoundEvent> HEAVY_BREATHING = register("heavy_breathing");

    /**
     * Played once when a body is opened unsearched.
     *
     * One sound per body rather than per slot: a clip that fires on every reveal turns into a
     * metronome, and the interesting moment is starting on a fresh corpse, not the ninth slot
     * of it.
     */
    public static final RegistryObject<SoundEvent> CORPSE_SEARCH = register("corpse_search");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(DayzHudMod.MOD_ID, name)));
    }
}
