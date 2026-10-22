package com.dayzhud.mod.recap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: the recap of the death that just happened (2.19.0). Everything the
 * death screen's panel shows, already worked out on the server.
 *
 * Names and weapons travel as Components, not strings, so a TACZ gun's translated name (or
 * a renamed item) shows in the dying player's own language.
 */
public class DeathRecapPacket {

    /** One row of the damage list: everything one attacker (with one weapon) or one cause did. */
    public record Line(Component who, @Nullable Component weapon, int hits, float damage) {}

    /**
     * Enough to draw whoever killed you on the death screen (2.19.0): their entity id, so the
     * client can hold on to the real entity while it's still loaded, and a snapshot to rebuild
     * them from if it isn't. The snapshot is what a client normally gets when an entity comes
     * into view - its type, Forge's additional spawn data, its synced entity data (skins and
     * variants usually live in one of those two) and its equipment, in EquipmentSlot.values()
     * order - plus its saved NBT for mods that keep the skin there. A real player instead
     * carries their profile, so the skin comes from the tab list.
     */
    public record KillerModel(int entityId, ResourceLocation type, @Nullable UUID profileId, String profileName,
                              List<ItemStack> gear, byte[] spawnData, byte[] syncedData,
                              @Nullable CompoundTag nbt) {}

    /** Vanilla's death message ("X was shot by Y"), or null. */
    @Nullable public final Component deathMessage;
    @Nullable public final KillerModel killerModel;

    @Nullable public final Component killer;
    public final boolean killerIsPlayer;
    /** Short cause ("Bled out", "Fall") when the killing blow had no attacker, else null. */
    @Nullable public final Component cause;
    @Nullable public final Component weapon;
    /** Blocks between killer and victim at the moment of death; negative = no killer. */
    public final float distance;
    public final boolean headshot;
    public final List<Line> lines;
    public final float totalDamage;
    public final int windowSeconds;
    public final int survivedSeconds;
    public final int playerKills;
    public final int mobKills;
    public final int lightWounds;
    public final int heavyWounds;

    public DeathRecapPacket(@Nullable Component deathMessage, @Nullable KillerModel killerModel,
                            @Nullable Component killer, boolean killerIsPlayer, @Nullable Component cause,
                            @Nullable Component weapon, float distance, boolean headshot, List<Line> lines,
                            float totalDamage, int windowSeconds, int survivedSeconds, int playerKills,
                            int mobKills, int lightWounds, int heavyWounds) {
        this.deathMessage = deathMessage;
        this.killerModel = killerModel;
        this.killer = killer;
        this.killerIsPlayer = killerIsPlayer;
        this.cause = cause;
        this.weapon = weapon;
        this.distance = distance;
        this.headshot = headshot;
        this.lines = lines;
        this.totalDamage = totalDamage;
        this.windowSeconds = windowSeconds;
        this.survivedSeconds = survivedSeconds;
        this.playerKills = playerKills;
        this.mobKills = mobKills;
        this.lightWounds = lightWounds;
        this.heavyWounds = heavyWounds;
    }

    public static void encode(DeathRecapPacket p, FriendlyByteBuf buf) {
        writeOptional(buf, p.deathMessage);
        buf.writeBoolean(p.killerModel != null);
        if (p.killerModel != null) {
            KillerModel m = p.killerModel;
            buf.writeVarInt(m.entityId());
            buf.writeResourceLocation(m.type());
            buf.writeBoolean(m.profileId() != null);
            if (m.profileId() != null) buf.writeUUID(m.profileId());
            buf.writeUtf(m.profileName(), 64);
            buf.writeVarInt(m.gear().size());
            for (ItemStack stack : m.gear()) buf.writeItem(stack);
            buf.writeByteArray(m.spawnData());
            buf.writeByteArray(m.syncedData());
            buf.writeNbt(m.nbt());
        }
        writeOptional(buf, p.killer);
        buf.writeBoolean(p.killerIsPlayer);
        writeOptional(buf, p.cause);
        writeOptional(buf, p.weapon);
        buf.writeFloat(p.distance);
        buf.writeBoolean(p.headshot);
        buf.writeVarInt(p.lines.size());
        for (Line line : p.lines) {
            buf.writeComponent(line.who());
            writeOptional(buf, line.weapon());
            buf.writeVarInt(line.hits());
            buf.writeFloat(line.damage());
        }
        buf.writeFloat(p.totalDamage);
        buf.writeVarInt(p.windowSeconds);
        buf.writeVarInt(p.survivedSeconds);
        buf.writeVarInt(p.playerKills);
        buf.writeVarInt(p.mobKills);
        buf.writeVarInt(p.lightWounds);
        buf.writeVarInt(p.heavyWounds);
    }

    public static DeathRecapPacket decode(FriendlyByteBuf buf) {
        Component deathMessage = readOptional(buf);
        KillerModel model = null;
        if (buf.readBoolean()) {
            int entityId = buf.readVarInt();
            ResourceLocation type = buf.readResourceLocation();
            UUID profileId = buf.readBoolean() ? buf.readUUID() : null;
            String profileName = buf.readUtf(64);
            int gearCount = Math.min(buf.readVarInt(), 16);
            List<ItemStack> gear = new ArrayList<>(gearCount);
            for (int i = 0; i < gearCount; i++) gear.add(buf.readItem());
            byte[] spawnData = buf.readByteArray();
            byte[] syncedData = buf.readByteArray();
            CompoundTag nbt = buf.readNbt();
            model = new KillerModel(entityId, type, profileId, profileName, gear, spawnData, syncedData, nbt);
        }
        Component killer = readOptional(buf);
        boolean killerIsPlayer = buf.readBoolean();
        Component cause = readOptional(buf);
        Component weapon = readOptional(buf);
        float distance = buf.readFloat();
        boolean headshot = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), 64);
        List<Line> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(new Line(buf.readComponent(), readOptional(buf), buf.readVarInt(), buf.readFloat()));
        }
        return new DeathRecapPacket(deathMessage, model, killer, killerIsPlayer, cause, weapon, distance, headshot, lines,
                buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(DeathRecapPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDeathRecap.set(p));
        ctx.get().setPacketHandled(true);
    }

    private static void writeOptional(FriendlyByteBuf buf, @Nullable Component c) {
        buf.writeBoolean(c != null);
        if (c != null) buf.writeComponent(c);
    }

    @Nullable
    private static Component readOptional(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readComponent() : null;
    }
}
