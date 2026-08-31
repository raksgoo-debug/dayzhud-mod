package com.dayzhud.mod.client;

import com.dayzhud.mod.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Plays this mod's interface sounds on the client.
 *
 * Kept separate from {@link ModSounds} on purpose: that class is the common-side registry and
 * has to load on a dedicated server, while everything here touches {@code Minecraft} and would
 * blow up there. The registry holds the events; this holds the playback.
 *
 * All of these go through {@code SimpleSoundInstance.forUI}, which plays non-positionally at
 * full volume regardless of where the player is looking or standing - correct for interface
 * feedback, and it also means the stereo files stay stereo (a positional sound would have to
 * be mono or Minecraft ignores the attenuation).
 */
@OnlyIn(Dist.CLIENT)
public final class UiSounds {

    /** Interface sounds sit under the master slider, the same as vanilla's button clicks. */
    private static final float INVENTORY_VOLUME = 0.7f;

    private UiSounds() {}

    public static void inventoryOpen() {
        play(ModSounds.INVENTORY_OPEN.get(), INVENTORY_VOLUME);
    }

    public static void inventoryClose() {
        play(ModSounds.INVENTORY_CLOSE.get(), INVENTORY_VOLUME);
    }

    /**
     * One of three variants, chosen at random by Minecraft from the sounds.json entry - so
     * moving a stack of items doesn't sound like a machine.
     */
    public static void inventoryMove() {
        play(ModSounds.INVENTORY_MOVE.get(), INVENTORY_VOLUME);
    }

    private static void play(SoundEvent event, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, volume));
    }
}
