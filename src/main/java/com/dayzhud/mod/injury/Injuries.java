package com.dayzhud.mod.injury;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's injuries (2.15.0): open wounds and pain. Kept in a capability so they survive
 * a relog; cleared by death (SecureContainerCapability-style copy is NOT done for these).
 */
public class Injuries implements INBTSerializable<CompoundTag> {

    /** Remaining ticks until each light wound clots; -1 = never. */
    final List<Integer> lightWounds = new ArrayList<>();
    int heavyWounds;
    /** 0-100. */
    float pain;
    /** Ticks of painkiller left. */
    int reliefTicks;
    /** Bleed damage accumulated but not yet dealt (dealt in whole points). */
    float bleedDebt;

    public int light() {
        return lightWounds.size();
    }

    public int heavy() {
        return heavyWounds;
    }

    public float pain() {
        return pain;
    }

    public boolean relieved() {
        return reliefTicks > 0;
    }

    void clear() {
        lightWounds.clear();
        heavyWounds = 0;
        pain = 0;
        reliefTicks = 0;
        bleedDebt = 0;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Light", new IntArrayTag(lightWounds));
        tag.putInt("Heavy", heavyWounds);
        tag.putFloat("Pain", pain);
        tag.putInt("Relief", reliefTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        lightWounds.clear();
        for (int t : tag.getIntArray("Light")) lightWounds.add(t);
        heavyWounds = tag.getInt("Heavy");
        pain = tag.getFloat("Pain");
        reliefTicks = tag.getInt("Relief");
    }
}
