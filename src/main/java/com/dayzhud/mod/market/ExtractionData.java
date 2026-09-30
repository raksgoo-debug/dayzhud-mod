package com.dayzhud.mod.market;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-dimension list of extraction points (2.14.0) - same shape and same commands as
 * SafeZoneData, placed with /market extract add. A point is a circle on the ground: inside
 * means within its radius horizontally and no more than {@link #VERTICAL} blocks above or
 * below its centre (so a cave under a point isn't one).
 */
public class ExtractionData extends SavedData {

    private static final String NAME = "dayzhud_extractions";
    static final int VERTICAL = 8;

    private final List<SafeZoneData.Zone> points = new ArrayList<>();

    public static ExtractionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ExtractionData::load, ExtractionData::new, NAME);
    }

    public List<SafeZoneData.Zone> points() {
        return List.copyOf(points);
    }

    public void add(SafeZoneData.Zone point) {
        points.add(point);
        setDirty();
    }

    public boolean remove(String name) {
        boolean removed = points.removeIf(z -> z.name().equalsIgnoreCase(name));
        if (removed) setDirty();
        return removed;
    }

    /** The point {@code pos} is inside, or null. */
    public SafeZoneData.Zone pointAt(BlockPos pos) {
        for (SafeZoneData.Zone z : points) {
            if (z.contains(pos) && Math.abs(pos.getY() - z.y()) <= VERTICAL) return z;
        }
        return null;
    }

    public static ExtractionData load(CompoundTag tag) {
        ExtractionData data = new ExtractionData();
        ListTag list = tag.getList("Points", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag z = list.getCompound(i);
            data.points.add(new SafeZoneData.Zone(z.getString("Name"), z.getInt("X"), z.getInt("Y"),
                    z.getInt("Z"), z.getInt("Radius")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SafeZoneData.Zone z : points) {
            CompoundTag t = new CompoundTag();
            t.putString("Name", z.name());
            t.putInt("X", z.x());
            t.putInt("Y", z.y());
            t.putInt("Z", z.z());
            t.putInt("Radius", z.radius());
            list.add(t);
        }
        tag.put("Points", list);
        return tag;
    }
}
