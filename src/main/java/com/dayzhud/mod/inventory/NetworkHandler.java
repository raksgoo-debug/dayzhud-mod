package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

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

        CHANNEL.registerMessage(packetId++,
                LoadoutClickPacket.class,
                LoadoutClickPacket::encode,
                LoadoutClickPacket::decode,
                LoadoutClickPacket::handle);
    }
}
