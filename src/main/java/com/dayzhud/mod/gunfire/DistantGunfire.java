package com.dayzhud.mod.gunfire;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.market.TaczMarketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Distant gunfire (2.19.0), server side: decides who hears a shot from beyond TACZ's own
 * sound range and tells their client where it was fired. Called for every round TACZ fires
 * (from NoiseSystem's TACZ listener), not rate-limited - at a distance you still hear each
 * shot of a burst.
 */
public final class DistantGunfire {

    private static List<? extends String> parsedFrom;
    private static Map<String, Double> gunRanges = Map.of();

    private DistantGunfire() {}

    public static void onShot(LivingEntity shooter, ItemStack gun, boolean suppressed) {
        if (!GunfireConfig.ENABLED.get() || !(shooter.level() instanceof ServerLevel level)) return;
        GunLoudness.Profile loud = GunLoudness.of(gun);
        double range = suppressed ? GunfireConfig.SUPPRESSED_RANGE.get() * Math.min(loud.range(), 1.5)
                : rangeOf(gun, loud);
        double near = GunfireConfig.DISTANT_FROM.get() * 0.6;
        if (range <= near) return;
        Vec3 pos = shooter.getEyePosition();
        double nearSq = near * near, rangeSq = range * range;
        DistantShotPacket packet = null;
        for (ServerPlayer listener : level.players()) {
            if (listener == shooter) continue;
            double distSq = listener.distanceToSqr(pos);
            if (distSq < nearSq || distSq > rangeSq) continue;
            if (packet == null) {
                packet = new DistantShotPacket(pos.x, pos.y, pos.z, gun.copy(), suppressed, shooter.getId(),
                        (float) range, loud.gain());
            }
            DistantShotPacket send = packet;
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener), send);
        }
    }

    /** gunRanges' entry for this gun if it has one, else range scaled by its calibre. */
    private static double rangeOf(ItemStack gun, GunLoudness.Profile loud) {
        double fallback = GunfireConfig.RANGE.get() * loud.range();
        List<? extends String> lines = GunfireConfig.GUN_RANGES.get();
        if (lines.isEmpty()) return fallback;
        if (lines != parsedFrom) {
            Map<String, Double> parsed = new HashMap<>();
            for (String line : lines) {
                int eq = line.lastIndexOf('=');
                try {
                    parsed.put(line.substring(0, eq).trim(), Double.parseDouble(line.substring(eq + 1).trim()));
                } catch (NumberFormatException e) {
                    DayzHudMod.LOGGER.warn("dayzhud gunfire config: ignoring '{}' (expected gunId=blocks)", line);
                }
            }
            gunRanges = parsed;
            parsedFrom = lines;
        }
        ResourceLocation id = TaczMarketCompat.gunIdOf(gun).orElse(null);
        Double range = id == null ? null : gunRanges.get(id.toString());
        return range != null ? range : fallback;
    }
}
