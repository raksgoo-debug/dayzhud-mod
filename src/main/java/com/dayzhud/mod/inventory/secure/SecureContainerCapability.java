package com.dayzhud.mod.inventory.secure;

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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Attaches a {@link SecureContainer} to every player and carries it across death and
 * dimension changes. Same shape as WalletCapability, including the two-bus split
 * (RegisterCapabilitiesEvent on the mod bus, AttachCapabilitiesEvent on the forge bus).
 */
public final class SecureContainerCapability {

    public static final Capability<SecureContainer> SECURE =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = new ResourceLocation(DayzHudMod.MOD_ID, "secure_container");

    private SecureContainerCapability() {}

    /** This player's secure container; a detached empty one in the early login ticks before
     *  attachment, so a menu built then still has the right slot count on both sides. */
    public static SecureContainer of(Player player) {
        return player.getCapability(SECURE).resolve().orElseGet(SecureContainer::new);
    }

    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(SecureContainer.class);
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

        /**
         * Death and dimension change both build a new player entity. Capabilities are not
         * copied on their own, and the old entity's are invalidated first - so revive, copy,
         * invalidate, as WalletEvents does. This copy is what "kept on death" means.
         */
        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            event.getOriginal().reviveCaps();
            SecureContainer old = event.getOriginal().getCapability(SECURE).resolve().orElse(null);
            SecureContainer fresh = event.getEntity().getCapability(SECURE).resolve().orElse(null);
            if (old != null && fresh != null) fresh.deserializeNBT(old.serializeNBT());
            event.getOriginal().invalidateCaps();
        }
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private SecureContainer container;
        private final LazyOptional<SecureContainer> optional = LazyOptional.of(this::orCreate);

        private SecureContainer orCreate() {
            if (container == null) container = new SecureContainer();
            return container;
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == SECURE ? optional.cast() : LazyOptional.empty();
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
