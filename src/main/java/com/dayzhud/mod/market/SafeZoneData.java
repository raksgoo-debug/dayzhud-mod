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
 * Per-dimension list of safe zones - the hideout areas where a portable terminal works.
 *
 * Kept as SavedData rather than config entries because zones are placed in the world by
 * whoever runs the server, with /market zone add, and a runtime command cannot write back
 * into a ForgeConfigSpec file. It also means the zones travel with the world save.
 */
public class SafeZoneData extends SavedData {

    private static final String NAME = "dayzhud_safezones";

    public record Zone(String name, int x, int y, int z, int radius) {
        public boolean contains(BlockPos pos) {
            long dx = (long) pos.getX() - x;
            long dz = (long) pos.getZ() - z;
            // Horizontal distance only: a hideout is a footprint, and checking Y would make
            // a basement or a second storey fall outside a zone centred on the ground floor.
            return dx * dx + dz * dz <= (long) radius * radius;
        }
    }

    private final List<Zone> zones = new ArrayList<>();

    public static SafeZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SafeZoneData::load, SafeZoneData::new, NAME);
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    public void add(Zone zone) {
        zones.add(zone);
        setDirty();
    }

    /** Removes the first zone with this name. Returns false when there was none. */
    public boolean remove(String name) {
        boolean removed = zones.removeIf(z -> z.name().equalsIgnoreCase(name));
        if (removed) setDirty();
        return removed;
    }

    public boolean isInside(BlockPos pos) {
        for (Zone z : zones) {
            if (z.contains(pos)) return true;
        }
        return false;
    }

    public static SafeZoneData load(CompoundTag tag) {
        SafeZoneData data = new SafeZoneData();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag z = list.getCompound(i);
            data.zones.add(new Zone(z.getString("Name"), z.getInt("X"), z.getInt("Y"),
                    z.getInt("Z"), z.getInt("Radius")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone z : zones) {
            CompoundTag t = new CompoundTag();
            t.putString("Name", z.name());
            t.putInt("X", z.x());
            t.putInt("Y", z.y());
            t.putInt("Z", z.z());
            t.putInt("Radius", z.radius());
            list.add(t);
        }
        tag.put("Zones", list);
        return tag;
    }
}
