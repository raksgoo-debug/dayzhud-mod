package com.dayzhud.mod.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;

/**
 * Plays one of TACZ's sound files directly (2.19.0).
 *
 * TACZ keeps gun sounds outside vanilla's sound registry, at
 * assets/&lt;ns&gt;/tacz_sounds/&lt;path&gt;.ogg, so they have no sound event to play. This
 * instance hands the engine a Sound pointing straight at that file. It goes through the
 * normal resource manager, so whichever resource pack wins for that file - a custom gun
 * sound pack, say - is what plays.
 *
 * No distance attenuation (DistantGunfireClient works out the volume itself), but still
 * positioned, so it's heard from where it came from. The file must be mono for that, which
 * TACZ's third-person sounds are.
 */
final class TaczFileSound extends AbstractSoundInstance {

    private final ResourceLocation file;

    TaczFileSound(ResourceLocation soundId, ResourceLocation file, Vec3 pos, float volume, float pitch) {
        super(soundId, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.file = file;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.volume = volume;
        this.pitch = pitch;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = false;
        this.looping = false;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new FileSound(this.location.toString(), file);
        return new WeighedSoundEvents(this.location, null);
    }

    /** A Sound whose file is wherever we say, rather than under sounds/. */
    private static final class FileSound extends Sound {
        private final ResourceLocation file;

        FileSound(String id, ResourceLocation file) {
            super(id, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, false, false, 16);
            this.file = file;
        }

        @Override
        public ResourceLocation getPath() {
            return file;
        }
    }
}
