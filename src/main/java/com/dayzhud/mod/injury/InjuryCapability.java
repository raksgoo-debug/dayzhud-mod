package com.dayzhud.mod.injury;

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

/** Attaches {@link Injuries} to every player. Copied across a dimension change, not across
 *  death - respawning is a clean slate. Same two-bus shape as WalletCapability. */
public final class InjuryCapability {

    public static final Capability<Injuries> INJURIES = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = new ResourceLocation(DayzHudMod.MOD_ID, "injuries");

    private InjuryCapability() {}

    @Nullable
    public static Injuries of(Player player) {
        return player.getCapability(INJURIES).resolve().orElse(null);
    }

    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(Injuries.class);
        }
    }

    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) event.addCapability(ID, new Provider());
        }

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            if (event.isWasDeath()) return;
            event.getOriginal().reviveCaps();
            Injuries old = of(event.getOriginal());
            Injuries fresh = of(event.getEntity());
            if (old != null && fresh != null) fresh.deserializeNBT(old.serializeNBT());
            event.getOriginal().invalidateCaps();
        }
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final Injuries injuries = new Injuries();
        private final LazyOptional<Injuries> optional = LazyOptional.of(() -> injuries);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == INJURIES ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return injuries.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            injuries.deserializeNBT(nbt);
        }
    }
}
