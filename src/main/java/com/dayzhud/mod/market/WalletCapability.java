package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Attaches a {@link Wallet} to every player. Same shape as SkillCapability, including the
 * two-bus split: RegisterCapabilitiesEvent is a mod-bus lifecycle event and
 * AttachCapabilitiesEvent is a forge-bus game event, and putting both on one bus is the
 * usual way this silently does nothing.
 */
public final class WalletCapability {

    public static final Capability<Wallet> WALLET =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = new ResourceLocation(DayzHudMod.MOD_ID, "wallet");

    private WalletCapability() {}

    /** This player's wallet, or null during the early login ticks before attachment. */
    @Nullable
    public static Wallet of(Player player) {
        if (player == null) return null;
        return player.getCapability(WALLET).resolve().orElse(null);
    }

    /** Balance, or 0 when the capability isn't available yet. Safe everywhere. */
    public static long balanceOf(Player player) {
        Wallet wallet = of(player);
        return wallet == null ? 0L : wallet.getBalance();
    }

    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(Wallet.class);
        }
    }

    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(ID, new Provider());
            }
        }
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private Wallet wallet;
        private final LazyOptional<Wallet> optional = LazyOptional.of(this::orCreate);

        private Wallet orCreate() {
            if (wallet == null) wallet = new Wallet();
            return wallet;
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == WALLET ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return orCreate().serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            orCreate().deserializeNBT(nbt);
        }
    }
}
