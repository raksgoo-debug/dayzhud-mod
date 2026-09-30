package com.dayzhud.mod.injury;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.market.NbtVariants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bleeding and pain (2.15.0), on top of First Aid's per-limb health.
 *
 * <b>Wounds.</b> A hit from a mob, a player, a projectile or an explosion can open one:
 * chance = damage x bleedChancePerDamage (capped at 90%); a hit of heavyBleedDamage or more
 * opens a heavy wound instead. Each wound drains health every second through the
 * "dayzhud:bleeding" damage type (bypasses armour, no knockback; First Aid spreads it over
 * the body). Light wounds clot on their own after a while; heavy ones need treatment.
 *
 * <b>Pain.</b> 0-100, raised by damage taken and by heavy bleeding, fading by itself. 30+
 * slows stamina recovery; 60+ also weakens you (Weakness, Mining Fatigue). A painkiller
 * suppresses the effects - not the pain - for its duration.
 *
 * <b>Treatment</b> happens when a listed med (InjuryConfig) finishes being used, so the med's
 * own effect (healing, First Aid, ...) still happens exactly as before.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class InjurySystem {

    public static final ResourceKey<DamageType> BLEEDING =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DayzHudMod.MOD_ID, "bleeding"));

    private static final float MAX_BLEED_CHANCE = 0.9f;
    private static final float PAIN_DECAY_PER_SECOND = 0.5f;
    private static final float PAIN_PER_HEAVY_WOUND_SECOND = 0.3f;
    public static final float PAIN_MILD = 30f, PAIN_SEVERE = 60f;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.0f);

    private static Map<String, Boolean> bleedTreatments;   // key -> stops heavy too
    private static Map<String, Integer> painTreatments;    // key -> seconds
    private static final Map<UUID, int[]> SENT = new HashMap<>();

    private InjurySystem() {}

    // ---- wounds and pain from damage -----------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!InjuryConfig.ENABLED.get() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;
        DamageSource source = event.getSource();
        if (source.is(BLEEDING)) return;
        Injuries inj = InjuryCapability.of(player);
        if (inj == null) return;
        float damage = event.getAmount();

        inj.pain = Math.min(100f, inj.pain + damage * InjuryConfig.PAIN_PER_DAMAGE.get().floatValue());

        boolean wounding = source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION);
        if (!wounding) return;
        float chance = Math.min(MAX_BLEED_CHANCE, damage * InjuryConfig.BLEED_CHANCE.get().floatValue());
        if (player.getRandom().nextFloat() >= chance) return;
        if (damage >= InjuryConfig.HEAVY_BLEED_DAMAGE.get()) {
            inj.heavyWounds++;
            player.displayClientMessage(Component.translatable("message.dayzhud.injury.heavy_bleed")
                    .withStyle(ChatFormatting.DARK_RED), true);
        } else {
            int clot = InjuryConfig.LIGHT_CLOT_SECONDS.get();
            inj.lightWounds.add(clot <= 0 ? -1 : (int) (clot * 20 * (0.5 + player.getRandom().nextFloat())));
            player.displayClientMessage(Component.translatable("message.dayzhud.injury.bleed")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    // ---- once a second: bleed, clot, pain, effects -----------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0 || !InjuryConfig.ENABLED.get()) return;
        Injuries inj = InjuryCapability.of(player);
        if (inj == null) return;
        if (player.isDeadOrDying()) return;

        // Light wounds clot.
        for (int i = inj.lightWounds.size() - 1; i >= 0; i--) {
            int left = inj.lightWounds.get(i);
            if (left < 0) continue;
            if (left <= 20) inj.lightWounds.remove(i);
            else inj.lightWounds.set(i, left - 20);
        }

        // Bleed.
        if (inj.light() + inj.heavy() > 0 && !player.isCreative()) {
            inj.bleedDebt += inj.light() * InjuryConfig.LIGHT_BLEED_HP.get().floatValue()
                    + inj.heavy() * InjuryConfig.HEAVY_BLEED_HP.get().floatValue();
            if (inj.bleedDebt >= 1f) {
                float dealt = (float) Math.floor(inj.bleedDebt);
                inj.bleedDebt -= dealt;
                ServerLevel level = player.serverLevel();
                player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(BLEEDING)), dealt);
            }
            if (player.tickCount % 40 == 0) {
                player.serverLevel().sendParticles(BLOOD, player.getX(), player.getY() + 0.8, player.getZ(),
                        inj.light() + inj.heavy() * 3, 0.2, 0.3, 0.2, 0.0);
            }
        }

        // Pain.
        if (inj.heavy() > 0) {
            inj.pain = Math.min(100f, inj.pain + inj.heavy() * PAIN_PER_HEAVY_WOUND_SECOND);
        } else {
            inj.pain = Math.max(0f, inj.pain - PAIN_DECAY_PER_SECOND);
        }
        if (inj.reliefTicks > 0) inj.reliefTicks = Math.max(0, inj.reliefTicks - 20);
        if (!inj.relieved() && inj.pain >= PAIN_SEVERE) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 50, 0, true, false, true));
        }

        sync(player, inj);
    }

    /** Stamina recovery factor from pain: 0.7 in noticeable pain, 1 otherwise. */
    public static float staminaRegenFactor(Player player) {
        if (!InjuryConfig.ENABLED.get()) return 1f;
        Injuries inj = InjuryCapability.of(player);
        return inj != null && !inj.relieved() && inj.pain >= PAIN_MILD ? 0.7f : 1f;
    }

    // ---- treatment ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onItemUsed(LivingEntityUseItemEvent.Finish event) {
        if (!InjuryConfig.ENABLED.get() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Injuries inj = InjuryCapability.of(player);
        if (inj == null) return;
        ItemStack used = event.getItem();
        loadTreatments();
        String variant = NbtVariants.keyOf(used);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(used.getItem());
        String plain = id == null ? null : id.toString();

        Boolean heavy = variant != null && bleedTreatments.containsKey(variant) ? bleedTreatments.get(variant)
                : plain != null ? bleedTreatments.get(plain) : null;
        if (heavy != null && inj.light() + inj.heavy() > 0) {
            inj.lightWounds.clear();
            if (heavy) inj.heavyWounds = 0;
            player.displayClientMessage(Component.translatable(inj.heavy() > 0
                    ? "message.dayzhud.injury.still_heavy" : "message.dayzhud.injury.bleed_stopped")
                    .withStyle(inj.heavy() > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        }
        Integer relief = variant != null && painTreatments.containsKey(variant) ? painTreatments.get(variant)
                : plain != null ? painTreatments.get(plain) : null;
        if (relief != null) inj.reliefTicks = Math.max(inj.reliefTicks, relief * 20);
        if (heavy != null || relief != null) sync(player, inj);
    }

    private static synchronized void loadTreatments() {
        if (bleedTreatments != null) return;
        Map<String, Boolean> bleed = new HashMap<>();
        for (String line : InjuryConfig.BLEED_TREATMENTS.get()) {
            int eq = line.lastIndexOf('=');
            bleed.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim().equalsIgnoreCase("heavy"));
        }
        Map<String, Integer> pain = new HashMap<>();
        for (String line : InjuryConfig.PAIN_TREATMENTS.get()) {
            int eq = line.lastIndexOf('=');
            try {
                pain.put(line.substring(0, eq).trim(), Integer.parseInt(line.substring(eq + 1).trim()));
            } catch (NumberFormatException e) {
                DayzHudMod.LOGGER.warn("dayzhud injuries config: ignoring '{}' (expected id=seconds)", line);
            }
        }
        painTreatments = pain;
        bleedTreatments = bleed;
    }

    private static void sync(ServerPlayer player, Injuries inj) {
        int[] now = {inj.light(), inj.heavy(), Math.round(inj.pain), inj.reliefTicks / 20};
        int[] last = SENT.get(player.getUUID());
        if (last != null && java.util.Arrays.equals(last, now)) return;
        SENT.put(player.getUUID(), now);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new InjurySyncPacket(now));
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
