package com.dayzhud.mod.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * One player's skill levels. Attached as a capability (see {@link SkillCapability}) so it
 * saves with the player and survives death.
 *
 * Stored by skill ID STRING rather than ordinal, so reordering or inserting entries in the
 * {@link Skill} enum can't silently reassign someone's levels to the wrong skill. Unknown
 * ids in saved data are ignored, which is what lets a skill be removed without corrupting
 * the rest of the save.
 */
public class PlayerSkills implements INBTSerializable<CompoundTag> {

    private final int[] levels = new int[Skill.values().length];

    public int getLevel(Skill skill) {
        return levels[skill.ordinal()];
    }

    public void setLevel(Skill skill, int level) {
        levels[skill.ordinal()] = Math.max(0, Math.min(level, skill.maxLevel()));
    }

    /** Adds one level if there's headroom. Returns the new level, or -1 if already capped. */
    public int increment(Skill skill) {
        int current = getLevel(skill);
        if (current >= skill.maxLevel()) return -1;
        setLevel(skill, current + 1);
        return current + 1;
    }

    public int totalLevels() {
        int total = 0;
        for (int level : levels) total += level;
        return total;
    }

    public void copyFrom(PlayerSkills other) {
        System.arraycopy(other.levels, 0, this.levels, 0, this.levels.length);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (Skill skill : Skill.values()) {
            tag.putInt(skill.id(), getLevel(skill));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        for (Skill skill : Skill.values()) {
            // Absent key -> 0, which is exactly right for a skill added after this save
            // was written.
            setLevel(skill, tag.getInt(skill.id()));
        }
    }

    /**
     * Wire format: one varint per skill, in enum order.
     *
     * Order-dependent, unlike the NBT above - but both ends are the same jar, so they can
     * never disagree about the enum. Keeping it positional avoids sending five strings on
     * every sync.
     */
    public void writeTo(FriendlyByteBuf buf) {
        for (Skill skill : Skill.values()) {
            buf.writeVarInt(getLevel(skill));
        }
    }

    public void readFrom(FriendlyByteBuf buf) {
        for (Skill skill : Skill.values()) {
            setLevel(skill, buf.readVarInt());
        }
    }
}
