package com.dayzhud.mod.skill;

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
 * Attaches {@link PlayerSkills} to every player and hands out access to it.
 *
 * The two @SubscribeEvent methods here live on DIFFERENT event buses, which is why they're
 * in separate inner classes: RegisterCapabilitiesEvent is a mod-lifecycle event (mod bus),
 * AttachCapabilitiesEvent is a game event (forge bus). Putting both on one bus is the usual
 * way this silently does nothing.
 */
public final class SkillCapability {

    public static final Capability<PlayerSkills> PLAYER_SKILLS =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = new ResourceLocation(DayzHudMod.MOD_ID, "skills");

    private SkillCapability() {}

    /**
     * This player's skills, or null if the capability isn't attached yet. Null is normal
     * during very early login ticks, so callers must handle it rather than assume.
     */
    @Nullable
    public static PlayerSkills of(Player player) {
        if (player == null) return null;
        return player.getCapability(PLAYER_SKILLS).resolve().orElse(null);
    }

    /** Level of one skill, or 0 when the capability isn't available. Safe everywhere. */
    public static int levelOf(Player player, Skill skill) {
        PlayerSkills skills = of(player);
        return skills == null ? 0 : skills.getLevel(skill);
    }

    /** Mod bus: declares the capability class so Forge can create the Capability token. */
    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerSkills.class);
        }
    }

    /** Forge bus: attaches a fresh provider to each player as they're constructed. */
    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(ID, new Provider());
            }
        }
    }

    /**
     * Standard lazy provider. The backing PlayerSkills is created on first access rather
     * than in the constructor so that a player who never touches the system costs nothing.
     */
    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private PlayerSkills skills;
        private final LazyOptional<PlayerSkills> optional = LazyOptional.of(this::orCreate);

        private PlayerSkills orCreate() {
            if (skills == null) skills = new PlayerSkills();
            return skills;
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == PLAYER_SKILLS ? optional.cast() : LazyOptional.empty();
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
