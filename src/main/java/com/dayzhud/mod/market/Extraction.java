package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Extraction (2.14.0): stand inside an extraction point (ExtractionData) for
 * extractSeconds and you're taken to your hideout with everything you carry. Leaving the
 * point, dying or logging out resets the countdown. The hideout is, in order: your bed or
 * respawn anchor, the nearest safe zone in your dimension, world spawn.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class Extraction {

    /** Checked every CHECK ticks, not every tick - the countdown is in whole seconds anyway. */
    private static final int CHECK = 5;
    private static final double PARTICLE_RANGE = 48.0;

    /** Ticks spent inside a point so far, per player. */
    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();
    /** Players who must step out of a point before a new countdown starts - set on arrival,
     *  so a hideout that happens to be inside a point can't bounce you straight back. */
    private static final Set<UUID> MUST_LEAVE = new HashSet<>();

    private Extraction() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!MarketConfig.EXTRACTION_ENABLED.get() || player.tickCount % CHECK != 0) return;
        UUID id = player.getUUID();
        if (player.isSpectator() || player.isDeadOrDying()) {
            PROGRESS.remove(id);
            return;
        }
        SafeZoneData.Zone point = ExtractionData.get(player.serverLevel()).pointAt(player.blockPosition());
        if (point == null) {
            MUST_LEAVE.remove(id);
            if (PROGRESS.remove(id) != null) {
                player.displayClientMessage(Component.translatable("message.dayzhud.extract.cancelled")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (MUST_LEAVE.contains(id)) return;

        int total = MarketConfig.EXTRACTION_SECONDS.get() * 20;
        int ticks = PROGRESS.merge(id, CHECK, Integer::sum);
        if (ticks >= total) {
            PROGRESS.remove(id);
            extract(player);
            com.dayzhud.mod.quest.QuestSystem.onExtract(player, point.name());
            return;
        }
        int left = (total - ticks + 19) / 20;
        player.displayClientMessage(Component.translatable("message.dayzhud.extract.countdown", point.name(), left)
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static void extract(ServerPlayer player) {
        Destination to = hideoutOf(player);
        player.teleportTo(to.level(), to.pos().x, to.pos().y, to.pos().z, to.yRot(), player.getXRot());
        MUST_LEAVE.add(player.getUUID());
        player.displayClientMessage(Component.translatable("message.dayzhud.extract.done")
                .withStyle(ChatFormatting.GREEN), true);
        to.level().playSound(null, BlockPos.containing(to.pos()), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.5f, 1.4f);
    }

    private record Destination(ServerLevel level, Vec3 pos, float yRot) {}

    private static Destination hideoutOf(ServerPlayer player) {
        // 1. Bed or respawn anchor - alive=true, so an anchor's charge is not spent.
        BlockPos respawn = player.getRespawnPosition();
        ServerLevel respawnLevel = player.server.getLevel(player.getRespawnDimension());
        if (respawn != null && respawnLevel != null) {
            Optional<Vec3> stand = Player.findRespawnPositionAndUseSpawnBlock(respawnLevel, respawn,
                    player.getRespawnAngle(), player.isRespawnForced(), true);
            if (stand.isPresent()) return new Destination(respawnLevel, stand.get(), player.getRespawnAngle());
        }
        // 2. Nearest safe zone in this dimension - where the traders are.
        ServerLevel here = player.serverLevel();
        SafeZoneData.Zone best = null;
        double bestDist = Double.MAX_VALUE;
        for (SafeZoneData.Zone z : SafeZoneData.get(here).zones()) {
            double d = player.distanceToSqr(z.x() + 0.5, z.y(), z.z() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = z;
            }
        }
        if (best != null) return new Destination(here, surface(here, best.x(), best.z()), player.getYRot());
        // 3. World spawn.
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        return new Destination(overworld, surface(overworld, spawn.getX(), spawn.getZ()), player.getYRot());
    }

    private static Vec3 surface(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        return new Vec3(x + 0.5, top.getY(), z + 0.5);
    }

    /** Once a second, a ring of green particles on each point's edge for players nearby. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0) return;
        if (!MarketConfig.EXTRACTION_ENABLED.get() || !MarketConfig.EXTRACTION_PARTICLES.get()) return;
        for (SafeZoneData.Zone z : ExtractionData.get(level).points()) {
            double cx = z.x() + 0.5, cz = z.z() + 0.5;
            if (level.getNearestPlayer(cx, z.y(), cz, PARTICLE_RANGE, false) == null) continue;
            int n = Math.max(12, Math.min(64, z.radius() * 6));
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n;
                double px = cx + Math.cos(a) * z.radius(), pz = cz + Math.sin(a) * z.radius();
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(px, z.y(), pz));
                double py = Math.abs(ground.getY() - z.y()) <= ExtractionData.VERTICAL ? ground.getY() + 0.2 : z.y() + 0.2;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 1, 0.0, 0.1, 0.0, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PROGRESS.remove(event.getEntity().getUUID());
        MUST_LEAVE.remove(event.getEntity().getUUID());
    }
}
