package com.dayzhud.mod.quest;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A player's quests (2.15.0): the active ones with a progress count per objective, and the
 * completed ones. A capability, carried across death and dimension changes - your quests
 * are your career, not your loadout.
 */
public class QuestLog implements INBTSerializable<CompoundTag> {

    public static final Capability<QuestLog> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = new ResourceLocation(DayzHudMod.MOD_ID, "quests");

    final Map<ResourceLocation, int[]> active = new LinkedHashMap<>();
    final Set<ResourceLocation> done = new HashSet<>();

    @Nullable
    public static QuestLog of(Player player) {
        return player.getCapability(CAP).resolve().orElse(null);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag act = new CompoundTag();
        active.forEach((id, progress) -> act.putIntArray(id.toString(), progress));
        tag.put("Active", act);
        ListTag list = new ListTag();
        for (ResourceLocation id : done) list.add(StringTag.valueOf(id.toString()));
        tag.put("Done", list);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        active.clear();
        done.clear();
        CompoundTag act = tag.getCompound("Active");
        for (String key : act.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) active.put(id, act.getIntArray(key));
        }
        ListTag list = tag.getList("Done", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) done.add(id);
        }
    }

    @Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(QuestLog.class);
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
            event.getOriginal().reviveCaps();
            QuestLog old = of(event.getOriginal());
            QuestLog fresh = of(event.getEntity());
            if (old != null && fresh != null) fresh.deserializeNBT(old.serializeNBT());
            event.getOriginal().invalidateCaps();
        }
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final QuestLog log = new QuestLog();
        private final LazyOptional<QuestLog> optional = LazyOptional.of(() -> log);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return log.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            log.deserializeNBT(nbt);
        }
    }
}
