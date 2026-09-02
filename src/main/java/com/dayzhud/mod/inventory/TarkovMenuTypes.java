package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.MarketMenu;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TarkovMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DayzHudMod.MOD_ID);

    /**
     * One menu type covers both the plain inventory screen and the inventory-plus-container
     * view. The open packet carries the opened container's size (0 for none) so the client
     * can build a dummy SimpleContainer of matching size - the slot COUNT has to agree on
     * both sides or the sync packets won't line up, but the contents arrive from the server.
     *
     * PAYLOAD, written by every opener (OpenTarkovInventoryPacket, ContainerOpenRedirect,
     * CorpseOpenRedirect) and read here. All three fields are ALWAYS present, in this order:
     *
     *   varint  containerSize   0 = no container, just the inventory screen
     *   boolean isCorpse        corpse layout (paperdoll + gear column) vs plain grid
     *   varint  curioCount      how many curio slots the corpse has; 0 is normal and legal
     *
     * isCorpse is its own field rather than being inferred from curioCount > 0. Inferring it
     * meant a corpse wearing no curios - a bare NPC - opened as an ordinary chest. Writing
     * all three unconditionally also retires the old isReadable() probe, which would silently
     * build a different menu shape if an opener ever forgot to write a field.
     */
    public static final RegistryObject<MenuType<TarkovInventoryMenu>> TARKOV_INVENTORY =
            MENU_TYPES.register("tarkov_inventory",
                    () -> IForgeMenuType.create((windowId, inv, buf) -> {
                        int containerSize = buf.readVarInt();
                        boolean isCorpse = buf.readBoolean();
                        int curioCount = buf.readVarInt();
                        if (containerSize <= 0) {
                            return new TarkovInventoryMenu(windowId, inv, null);
                        }
                        List<String> curioIds = new ArrayList<>();
                        for (int i = 0; i < curioCount; i++) {
                            // Only the COUNT matters client-side - identifiers are used for
                            // layout decisions the server already made. Placeholders keep the
                            // slot count identical on both sides, which is what must match.
                            curioIds.add("curio" + i);
                        }
                        return new TarkovInventoryMenu(windowId, inv,
                                new SimpleContainer(containerSize), curioIds, isCorpse);
                    }));

    /**
     * The trader screen. Nothing is written into the open buffer: the shop is a list rather
     * than slots, so the only container involved is the client's own scratch sell tray, and
     * the catalogue arrives afterwards on its own packet.
     */
    public static final RegistryObject<MenuType<MarketMenu>> MARKET =
            MENU_TYPES.register("market",
                    () -> IForgeMenuType.create((windowId, inv, buf) -> new MarketMenu(windowId, inv)));
}
