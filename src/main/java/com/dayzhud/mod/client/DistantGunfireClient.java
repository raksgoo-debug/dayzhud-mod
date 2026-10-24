package com.dayzhud.mod.client;

import com.dayzhud.mod.gunfire.DistantShotPacket;
import com.dayzhud.mod.gunfire.GunfireConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Distant gunfire (2.19.0), client side: plays a far-away TACZ shot the way it would reach you.
 *
 * <ul>
 *   <li><b>From where it was fired</b> - the gun's own third-person sound (TaczGunSounds),
 *       positioned at the muzzle, so you hear which way it came from.</li>
 *   <li><b>Late</b> - delayed by distance / speedOfSound: a shot 340 blocks off lands a
 *       second after the flash.</li>
 *   <li><b>Quieter with distance</b> - -6 dB per doubling (spherical spreading) plus a little
 *       air absorption, faded in under TACZ's own sound and out at the gun's range - and
 *       louder or quieter by calibre (GunLoudness, worked out on the server).</li>
 *   <li><b>Muffled through terrain</b> - solid blocks on the line to the shot make the direct
 *       sound quieter and duller; echoes still come round.</li>
 *   <li><b>Darker with distance</b> - a few percent lower pitch, which is as close to air
 *       eating the high end as vanilla's sound engine gets without filters.</li>
 *   <li><b>Echoes</b> - rays from you find walls, cliffs and buildings within 160 blocks. Each
 *       becomes an echo coming from that surface, delayed by the extra path the sound took to
 *       bounce off it. In open country there's nothing near to bounce off, so you get the long,
 *       soft roll off distant terrain instead.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "dayzhud", value = Dist.CLIENT)
public final class DistantGunfireClient {

    private static final int ECHO_RAYS = 10;
    private static final double ECHO_REACH = 160.0;
    /** Reflections arriving less than this much later than the shot blur into it - skipped. */
    private static final double MIN_ECHO_EXTRA = 14.0;
    private static final int MAX_ECHOES = 3;
    private static final float REFLECTIVITY = 0.5f;
    /** Echoes per shooter at most this often (ms) - full auto would otherwise stack dozens. */
    private static final long ECHO_INTERVAL_MS = 220;
    private static final float MIN_GAIN = 0.004f;

    private static final Map<Integer, Long> LAST_ECHO = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private record Tap(Vec3 from, double extra, float gain) {}

    private DistantGunfireClient() {}

    public static void play(DistantShotPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !GunfireConfig.ENABLED.get()) return;
        TaczGunSounds.Resolved sound = TaczGunSounds.thirdPerson(p.gun(), p.suppressed());
        if (sound == null) return;

        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 source = new Vec3(p.x(), p.y(), p.z());
        double distance = ear.distanceTo(source);
        float open = Math.min(1f, gain(distance, p.range(), p.suppressed()) * p.loudness());
        if (open < MIN_GAIN) return;
        float pitch = 1f - 0.06f * (float) Mth.clamp(distance / Math.max(1.0, p.range()), 0.0, 1.0);

        // Terrain or walls in the way: the direct sound is muffled, the more so the thicker it
        // is. Echoes reach you around it, so they're only somewhat weaker.
        float gain = open, echoGain = open;
        if (GunfireConfig.OCCLUSION.get()) {
            double thickness = obstruction(level, ear, source, distance);
            if (thickness > 0) {
                gain = open * (float) Math.max(0.12, 0.45 * Math.exp(-thickness / 16.0));
                pitch *= 0.93f;
                echoGain = open * 0.65f;
            }
        }

        playAt(sound, source, gain, pitch, distance);

        if (!GunfireConfig.ECHOES.get()) return;
        long now = System.currentTimeMillis();
        Long last = LAST_ECHO.get(p.shooterId());
        if (last != null && now - last < ECHO_INTERVAL_MS) return;
        LAST_ECHO.put(p.shooterId(), now);
        for (Tap tap : echoes(level, ear, source, distance, echoGain, p.suppressed())) {
            float echoPitch = pitch * (0.93f + RANDOM.nextFloat() * 0.04f);
            playAt(sound, tap.from, tap.gain, echoPitch, distance + tap.extra);
        }
    }

    /**
     * Volume at {@code distance}: VOLUME at distantFrom, halving per doubling beyond it, a
     * touch of air absorption, faded in under TACZ's own sound and out over the last 20% of range.
     */
    static float gain(double distance, double range, boolean suppressed) {
        double start = GunfireConfig.DISTANT_FROM.get();
        double fadeIn = Mth.clamp((distance - start * 0.6) / (start * 0.4), 0.0, 1.0);
        double spreading = start / Math.max(distance, start);
        double air = Math.exp(-distance / 2000.0);
        double fadeOut = Mth.clamp((range - distance) / (range * 0.2), 0.0, 1.0);
        double g = GunfireConfig.VOLUME.get() * fadeIn * spreading * air * fadeOut * (suppressed ? 0.7 : 1.0);
        return (float) Mth.clamp(g, 0.0, 1.0);
    }

    private static void playAt(TaczGunSounds.Resolved sound, Vec3 from, float gain, float pitch, double pathLength) {
        if (gain < MIN_GAIN) return;
        double speed = GunfireConfig.SPEED_OF_SOUND.get();
        int delayTicks = speed <= 0 ? 0 : (int) Math.round(pathLength / speed * 20.0);
        TaczFileSound instance = new TaczFileSound(sound.soundId(), sound.file(), from, gain,
                Mth.clamp(pitch, 0.5f, 2f));
        Minecraft.getInstance().getSoundManager().playDelayed(instance, delayTicks);
    }

    /**
     * Blocks of solid stuff on the straight line from you to the shot, sampled every block or
     * so (coarser past 256 blocks, at most 300 samples). Full blocks only - leaves, glass and
     * fences let sound through - and the first and last couple of blocks are skipped, so the
     * shooter's own cover or the wall you're leaning on doesn't count. Unloaded chunks read as
     * air.
     */
    private static double obstruction(ClientLevel level, Vec3 ear, Vec3 source, double distance) {
        if (distance < 6) return 0;
        double step = Math.max(1.0, distance / 300.0);
        Vec3 dir = source.subtract(ear).scale(1.0 / distance);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        double solid = 0;
        for (double t = 2.0; t < distance - 2.0; t += step) {
            at.set(ear.x + dir.x * t, ear.y + dir.y * t, ear.z + dir.z * t);
            BlockState state = level.getBlockState(at);
            if (!state.isAir() && state.isSolidRender(level, at)) solid += step;
        }
        return solid;
    }

    /** Up to MAX_ECHOES reflections, strongest first. */
    private static List<Tap> echoes(ClientLevel level, Vec3 ear, Vec3 source, double distance, float gain,
                                    boolean suppressed) {
        List<Tap> taps = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        double turn = RANDOM.nextDouble() * Math.PI * 2;
        for (int i = 0; i < ECHO_RAYS; i++) {
            double angle = turn + i * (Math.PI * 2 / ECHO_RAYS);
            // Slightly upward, so rays clear bumps in the ground and find walls and hillsides.
            Vec3 dir = new Vec3(Math.cos(angle), 0.08, Math.sin(angle)).normalize();
            BlockHitResult hit = level.clip(new ClipContext(ear, ear.add(dir.scale(ECHO_REACH)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            Vec3 wall = hit.getLocation();
            double path = source.distanceTo(wall) + wall.distanceTo(ear);
            double extra = path - distance;
            if (extra < MIN_ECHO_EXTRA) continue;
            // Weaker the longer the detour: spreading over the extra path, and some loss at the surface.
            float g = gain * REFLECTIVITY * (float) (distance / path);
            taps.add(new Tap(wall, extra, g));
        }
        taps.sort(Comparator.comparingDouble((Tap t) -> -t.gain));
        if (taps.size() > MAX_ECHOES) taps = new ArrayList<>(taps.subList(0, MAX_ECHOES));

        if (taps.isEmpty() && !suppressed) {
            // Open country: nothing near to bounce off, just the slow roll back off far terrain,
            // from roughly the same side as the shot.
            Vec3 toward = source.subtract(ear).normalize();
            for (int i = 0; i < 2; i++) {
                double swing = (RANDOM.nextDouble() - 0.5) * Math.PI * 0.8;
                Vec3 dir = new Vec3(toward.x * Math.cos(swing) - toward.z * Math.sin(swing), 0,
                        toward.x * Math.sin(swing) + toward.z * Math.cos(swing)).normalize();
                double extra = (i == 0 ? 160 : 320) + RANDOM.nextDouble() * 120;
                taps.add(new Tap(ear.add(dir.scale(200)), extra, gain * (i == 0 ? 0.22f : 0.12f)));
            }
        }
        return taps;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LAST_ECHO.clear();
        TaczGunSounds.clear();
    }
}
