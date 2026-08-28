package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.dayzhud.mod.skill.SkillStatePacket;
import com.dayzhud.mod.skill.SpendSkillPacket;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DayzHudMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++,
                OpenTarkovInventoryPacket.class,
                OpenTarkovInventoryPacket::encode,
                OpenTarkovInventoryPacket::decode,
                OpenTarkovInventoryPacket::handle);

        CHANNEL.registerMessage(packetId++,
                BackpackScrollPacket.class,
                BackpackScrollPacket::encode,
                BackpackScrollPacket::decode,
                BackpackScrollPacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenCraftingPacket.class,
                OpenCraftingPacket::encode,
                OpenCraftingPacket::decode,
                OpenCraftingPacket::handle);

        // Skills. Packet IDs here are positional, so new entries go on the END of this list:
        // inserting one in the middle renumbers everything after it, and a client on the
        // previous build would then decode every later packet as the wrong type.
        CHANNEL.registerMessage(packetId++,
                SkillStatePacket.class,
                SkillStatePacket::encode,
                SkillStatePacket::decode,
                SkillStatePacket::handle);

        CHANNEL.registerMessage(packetId++,
                SpendSkillPacket.class,
                SpendSkillPacket::encode,
                SpendSkillPacket::decode,
                SpendSkillPacket::handle);
    }
}
