package com.dayzhud.mod.noise;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.NetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Noise (2.19.0), Project Zomboid style.
 *
 * <b>Making noise.</b> {@link #emit} is the one entry point. Gunshots (TACZ, by reflection),
 * explosions, a player breaking glass, loosing an arrow, and footsteps all go through it,
 * each with its own radius from NoiseConfig. Sprinting is loud, walking is quieter, sneaking
 * and crawling are silent by default.
 *
 * <b>Mobs.</b> Hostile mobs (anything implementing Enemy) that hear a noise and have no
 * target walk to where it came from, give or take a few blocks, for up to
 * investigateSeconds. Once there they're back on their own AI - which, if you're still
 * standing nearby, means they'll spot you. Nothing here makes a mob target anyone directly.
 *
 * <b>Players.</b> Other players within radius x playerHearingMultiplier get a NoisePacket
 * with the bearing and a 0-1 closeness (never the position), which the HUD draws as a marker
 * around the crosshair. The player who made the noise gets a "self" packet for their meter.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class NoiseSystem {

    /** Footsteps are checked this often (ticks), and a moving player emits once per check. */
    private static final int FOOTSTEP_INTERVAL = 10;
    /** Less than this many blocks moved in one interval counts as standing still. */
    private static final double FOOTSTEP_MIN_MOVE = 0.25;
    /** An automatic weapon makes noise at most this often per shooter (ticks). */
    private static final int GUNSHOT_COOLDOWN = 10;
    /** Mobs re-path to their noise this often (ticks). */
    private static final int INVESTIGATE_INTERVAL = 10;
    private static final double ARRIVED_SQ = 3.0 * 3.0;
    private static final double INVESTIGATE_SPEED = 1.0;

    private record Investigation(Vec3 target, long until) {}

    /** Mobs currently walking to a noise. Weak, so an unloaded mob doesn't leak. */
    private static final Map<Mob, Investigation> INVESTIGATING = new WeakHashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_GUNSHOT = new HashMap<>();
    private static int serverTicks;

    private NoiseSystem() {}

    // ---- the entry point -----------------------------------------------------------------

    /**
     * A noise of {@code kind} at {@code pos}, heard out to {@code radius} blocks.
     * {@code source} is who made it (they get no marker for their own noise), or null.
     */
    public static void emit(ServerLevel level, Vec3 pos, NoiseKind kind, double radius, @Nullable Entity source) {
        if (!NoiseConfig.ENABLED.get() || radius <= 0) return;
        if (NoiseConfig.MOBS_INVESTIGATE.get()) alertMobs(level, pos, radius, source);
        if (kind.heardByPlayers && NoiseConfig.PLAYER_MARKERS.get()) notifyPlayers(level, pos, kind, radius, source);
        if (kind != NoiseKind.FOOTSTEP && source instanceof ServerPlayer self) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> self),
                    new NoisePacket(kind, 0f, 1f, true));
        }
    }

    private static void alertMobs(ServerLevel level, Vec3 pos, double radius, @Nullable Entity source) {
        int max = NoiseConfig.MAX_MOBS_PER_NOISE.get();
        if (max <= 0) return;
        double radiusSq = radius * radius;
        AABB box = new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        List<? extends String> deaf = NoiseConfig.DEAF_MOBS.get();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, m -> m != source
                && m instanceof Enemy && m.isAlive() && !m.isNoAi() && m.getTarget() == null
                && m.distanceToSqr(pos) <= radiusSq && !isDeaf(m, deaf));
        if (mobs.isEmpty()) return;
        if (mobs.size() > max) {
            mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(pos)));
            mobs = mobs.subList(0, max);
        }
        long until = level.getGameTime() + NoiseConfig.INVESTIGATE_SECONDS.get() * 20L;
        // Scatter the destination so a crowd spreads around the noise instead of stacking
        // on one block - and so they don't walk straight onto the shooter's exact position.
        double scatter = Math.min(4.0, radius * 0.15);
        for (Mob mob : mobs) {
            Vec3 target = pos.add((mob.getRandom().nextDouble() * 2 - 1) * scatter, 0,
                    (mob.getRandom().nextDouble() * 2 - 1) * scatter);
            INVESTIGATING.put(mob, new Investigation(target, until));
            mob.getNavigation().moveTo(target.x, target.y, target.z, INVESTIGATE_SPEED);
        }
    }

    private static boolean isDeaf(Mob mob, List<? extends String> deaf) {
        if (deaf.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null && deaf.contains(id.toString());
    }

    private static void notifyPlayers(ServerLevel level, Vec3 pos, NoiseKind kind, double radius, @Nullable Entity source) {
        double hearing = radius * NoiseConfig.PLAYER_HEARING.get();
        if (hearing <= 0) return;
        double hearingSq = hearing * hearing;
        for (ServerPlayer listener : level.players()) {
            if (listener == source) continue;
            double distSq = listener.distanceToSqr(pos);
            if (distSq > hearingSq) continue;
            float bearing = (float) (Mth.atan2(pos.z - listener.getZ(), pos.x - listener.getX())
                    * (180.0 / Math.PI)) - 90f;
            float closeness = (float) (1.0 - Math.sqrt(distSq) / hearing);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener),
                    new NoisePacket(kind, bearing, closeness, false));
        }
    }

    // ---- mobs walking to a noise ---------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || INVESTIGATING.isEmpty()) return;
        if (++serverTicks % INVESTIGATE_INTERVAL != 0) return;
        for (Iterator<Map.Entry<Mob, Investigation>> it = INVESTIGATING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Mob, Investigation> e = it.next();
            Mob mob = e.getKey();
            Investigation inv = e.getValue();
            if (mob == null || mob.isRemoved() || !mob.isAlive() || mob.getTarget() != null
                    || mob.level().getGameTime() > inv.until || mob.distanceToSqr(inv.target) < ARRIVED_SQ) {
                it.remove();
                continue;
            }
            // Re-issue the path only when the mob isn't already heading there - its own idle
            // goals (random strolling) can take the navigator over between checks.
            var nav = mob.getNavigation();
            var heading = nav.getTargetPos();
            if (nav.isDone() || heading == null
                    || heading.distToCenterSqr(inv.target.x, inv.target.y, inv.target.z) > ARRIVED_SQ) {
                nav.moveTo(inv.target.x, inv.target.y, inv.target.z, INVESTIGATE_SPEED);
            }
        }
    }

    // ---- footsteps -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % FOOTSTEP_INTERVAL != 0 || !NoiseConfig.ENABLED.get()) return;
        Vec3 now = player.position();
        Vec3 last = LAST_POS.put(player.getUUID(), now);
        if (last == null || player.isCreative() || player.isSpectator() || player.isPassenger()) return;
        double dx = now.x - last.x, dz = now.z - last.z;
        if (dx * dx + dz * dz < FOOTSTEP_MIN_MOVE * FOOTSTEP_MIN_MOVE) return;
        emit(player.serverLevel(), now, NoiseKind.FOOTSTEP, footstepRadius(player), player);
    }

    /**
     * How far this player's footsteps carry right now. Shared with the client's noise meter,
     * so the meter shows exactly what mobs hear.
     */
    public static double footstepRadius(Player player) {
        boolean crawling = player.getPose() == Pose.SWIMMING && !player.isInWater();
        if (player.isCrouching() || crawling) return NoiseConfig.CROUCH.get();
        if (player.isSprinting()) return NoiseConfig.SPRINT.get();
        return NoiseConfig.WALK.get();
    }

    // ---- other noises --------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var state = event.getState();
        if (!state.is(Tags.Blocks.GLASS) && !state.is(Tags.Blocks.GLASS_PANES)) return;
        if (event.getPlayer() != null && event.getPlayer().isCreative()) return;
        emit(level, Vec3.atCenterOf(event.getPos()), NoiseKind.GLASS, NoiseConfig.GLASS.get(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var explosion = event.getExplosion();
        emit(level, explosion.getPosition(), NoiseKind.EXPLOSION, NoiseConfig.EXPLOSION.get(),
                explosion.getIndirectSourceEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        emit(level, player.position(), NoiseKind.BOW, NoiseConfig.BOW.get(), player);
    }

    /** A TACZ gun went off. Rate-limited per shooter so full-auto doesn't flood anything. */
    static void onGunFired(LivingEntity shooter, ItemStack gun) {
        if (!(shooter.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long last = LAST_GUNSHOT.get(shooter.getUUID());
        if (last != null && now >= last && now - last < GUNSHOT_COOLDOWN) return;
        LAST_GUNSHOT.put(shooter.getUUID(), now);
        boolean suppressed = isSuppressed(gun);
        // Mobs hear a .50 farther than a pistol, on the same scale players do (GunLoudness).
        double scale = com.dayzhud.mod.gunfire.GunLoudness.of(gun).range();
        if (suppressed) scale = Math.min(scale, 1.5);
        emit(level, shooter.position(), suppressed ? NoiseKind.SUPPRESSED_GUNSHOT : NoiseKind.GUNSHOT,
                (suppressed ? NoiseConfig.SUPPRESSED_GUNSHOT.get() : NoiseConfig.GUNSHOT.get()) * scale, shooter);
    }

    /**
     * Whether a TACZ gun has a suppressor fitted: its muzzle attachment's id contains one of
     * suppressorKeywords. TACZ keeps each fitted attachment as an item stack under
     * "Attachment" + the slot's type name, so the muzzle is "AttachmentMUZZLE" - every
     * built-in TACZ suppressor's id contains "silencer". Read as text rather than parsed, so
     * a change to how that stack is nested inside the tag doesn't matter.
     */
    public static boolean isSuppressed(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag == null) return false;
        Tag muzzle = tag.get("AttachmentMUZZLE");
        if (muzzle == null) return false;
        String text = muzzle.toString().toLowerCase(Locale.ROOT);
        for (String keyword : NoiseConfig.SUPPRESSOR_KEYWORDS.get()) {
            if (!keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    // ---- TACZ, by reflection -------------------------------------------------------------

    /**
     * Listens for TACZ's gun events, TACZ being optional. GunFireEvent is posted once per
     * round actually fired; older TACZ builds only have GunShootEvent (once per trigger pull),
     * which is used instead when GunFireEvent isn't there. Both carry getShooter() and
     * getGunItemStack() and are posted on both sides - only the server's is used.
     *
     * Risk area: these names are TACZ 1.0.x/1.1.x's (com.tacz.guns.api.event.common). If a
     * later TACZ renames them this logs a warning and gunshots simply make no noise; nothing
     * else is affected.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerCompat() {
        if (!ModList.get().isLoaded("tacz")) return;
        Class eventClass = null;
        for (String name : new String[]{
                "com.tacz.guns.api.event.common.GunFireEvent",
                "com.tacz.guns.api.event.common.GunShootEvent"}) {
            try {
                eventClass = Class.forName(name);
                break;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next one
            }
        }
        if (eventClass == null) {
            DayzHudMod.LOGGER.warn("[dayzhud] TACZ is installed but neither GunFireEvent nor GunShootEvent "
                    + "was found - gunshots won't make noise.");
            return;
        }
        try {
            Method getShooter = eventClass.getMethod("getShooter");
            Method getGun = eventClass.getMethod("getGunItemStack");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, eventClass, (Consumer) event -> {
                try {
                    if (getShooter.invoke(event) instanceof LivingEntity shooter
                            && getGun.invoke(event) instanceof ItemStack gun) {
                        // Every round reaches distant ears; noise for mobs is rate-limited inside.
                        com.dayzhud.mod.gunfire.DistantGunfire.onShot(shooter, gun, isSuppressed(gun));
                        onGunFired(shooter, gun);
                    }
                } catch (ReflectiveOperationException e) {
                    DayzHudMod.LOGGER.debug("[dayzhud] TACZ gun event unreadable", e);
                }
            });
            DayzHudMod.LOGGER.info("[dayzhud] Listening for TACZ {} (gunshot noise).", eventClass.getSimpleName());
        } catch (ReflectiveOperationException | LinkageError e) {
            DayzHudMod.LOGGER.warn("[dayzhud] TACZ's {} has no getShooter/getGunItemStack - gunshots won't "
                    + "make noise.", eventClass.getSimpleName(), e);
        }
    }

    // ---- cleanup -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POS.remove(event.getEntity().getUUID());
        LAST_GUNSHOT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Otherwise the jump between dimensions reads as one huge footstep.
        LAST_POS.remove(event.getEntity().getUUID());
    }
}
