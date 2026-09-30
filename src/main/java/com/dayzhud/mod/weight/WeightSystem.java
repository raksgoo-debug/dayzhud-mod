package com.dayzhud.mod.weight;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.inventory.BackCurioItemHandler;
import com.dayzhud.mod.inventory.NetworkHandler;
import com.dayzhud.mod.inventory.secure.SecureContainerCapability;
import com.dayzhud.mod.skill.Skill;
import com.dayzhud.mod.skill.SkillCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carried weight (2.15.0), Tarkov-style. Recomputed on the server twice a second from
 * everything the player carries; above the limits (WeightConfig, raised by Endurance):
 * <ul>
 *   <li>overweight - walking slows from 100% to 85% and sprint/jump stamina drain rises to 1.6x;</li>
 *   <li>heavy - no sprinting, walking 85% down to 60%, drain up to 2.5x;</li>
 *   <li>critical - walking 60% down to a 35% floor.</li>
 * </ul>
 * Speed is an attribute modifier (synced to the client by vanilla); the weight and limits go
 * to the client for the inventory readout and the sprint block (ClientWeight).
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class WeightSystem {

    private static final UUID SPEED_ID = UUID.fromString("5f1b3c0e-7d2a-4b8e-9a61-2c4d7e9f0b13");
    private static final int RECOMPUTE_TICKS = 10;

    /** Last computed weight (kg) per player. */
    private static final Map<UUID, Float> WEIGHT = new HashMap<>();
    private static final Map<UUID, float[]> SENT = new HashMap<>();

    private WeightSystem() {}

    public record Limits(float overweight, float heavy, float critical) {}

    public static Limits limitsOf(Player player) {
        float bonus = (float) (SkillCapability.levelOf(player, Skill.ENDURANCE) * WeightConfig.ENDURANCE_BONUS.get());
        return new Limits(WeightConfig.OVERWEIGHT.get().floatValue() + bonus,
                WeightConfig.HEAVY.get().floatValue() + bonus,
                WeightConfig.CRITICAL.get().floatValue() + bonus);
    }

    public static float weightOf(Player player) {
        return WEIGHT.getOrDefault(player.getUUID(), 0f);
    }

    /** Walking speed factor for this weight, 1 = unburdened. */
    public static float speedFactor(float kg, Limits l) {
        if (kg <= l.overweight()) return 1f;
        if (kg <= l.heavy()) return lerp(1f, 0.85f, (kg - l.overweight()) / (l.heavy() - l.overweight()));
        if (kg <= l.critical()) return lerp(0.85f, 0.6f, (kg - l.heavy()) / (l.critical() - l.heavy()));
        return Math.max(0.35f, lerp(0.6f, 0.35f, (kg - l.critical()) / Math.max(1f, l.critical() * 0.5f)));
    }

    /** Stamina drain multiplier for sprinting and jumping. */
    public static float staminaFactor(Player player) {
        if (!WeightConfig.ENABLED.get()) return 1f;
        float kg = weightOf(player);
        Limits l = limitsOf(player);
        if (kg <= l.overweight()) return 1f;
        if (kg <= l.heavy()) return lerp(1f, 1.6f, (kg - l.overweight()) / (l.heavy() - l.overweight()));
        return lerp(1.6f, 2.5f, Math.min(1f, (kg - l.heavy()) / Math.max(1f, l.critical() - l.heavy())));
    }

    public static boolean canSprint(Player player) {
        return !WeightConfig.ENABLED.get() || weightOf(player) <= limitsOf(player).heavy();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!WeightConfig.ENABLED.get()) {
            clearModifier(player);
            return;
        }
        if (player.tickCount % RECOMPUTE_TICKS == 0) {
            float kg = compute(player);
            WEIGHT.put(player.getUUID(), kg);
            Limits limits = limitsOf(player);
            applySpeed(player, speedFactor(kg, limits));
            sync(player, kg, limits);
        }
        if (player.isSprinting() && !canSprint(player)) player.setSprinting(false);
    }

    /** Everything carried: inventory, hotbar, armour, offhand, worn curios, the worn bag's
     *  contents, the secure container, and whatever is on the cursor. */
    private static float compute(ServerPlayer player) {
        float kg = 0f;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) kg += ItemWeights.weightOf(s);
        for (ItemStack s : inv.armor) kg += ItemWeights.weightOf(s);
        for (ItemStack s : inv.offhand) kg += ItemWeights.weightOf(s);
        kg += ItemWeights.weightOf(player.containerMenu.getCarried());
        try {
            var curios = CuriosApi.getCuriosInventory(player).resolve();
            if (curios.isPresent()) {
                for (var handler : curios.get().getCurios().values()) kg += sum(handler.getStacks());
            }
        } catch (Throwable ignored) {
            // Curios API shape changed - worn gear just doesn't count.
        }
        kg += sum(new BackCurioItemHandler(player));
        kg += sum(SecureContainerCapability.of(player));
        return kg;
    }

    private static float sum(IItemHandler handler) {
        float kg = 0f;
        for (int i = 0; i < handler.getSlots(); i++) kg += ItemWeights.weightOf(handler.getStackInSlot(i));
        return kg;
    }

    private static void applySpeed(ServerPlayer player, float factor) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier current = speed.getModifier(SPEED_ID);
        double amount = factor - 1.0;
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-4) return;
        if (current != null) speed.removeModifier(SPEED_ID);
        if (amount < 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "dayzhud carried weight", amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void clearModifier(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_ID) != null) speed.removeModifier(SPEED_ID);
    }

    private static void sync(ServerPlayer player, float kg, Limits l) {
        float[] now = {kg, l.overweight(), l.heavy(), l.critical()};
        float[] last = SENT.get(player.getUUID());
        if (last != null && Math.abs(last[0] - kg) < 0.05f && last[1] == now[1] && last[2] == now[2]) return;
        SENT.put(player.getUUID(), now);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WeightSyncPacket(now));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WEIGHT.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}
