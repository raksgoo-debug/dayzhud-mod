package com.dayzhud.mod.inventory;

import com.dayzhud.mod.DayzHudMod;
import com.dayzhud.mod.market.RummageCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the Ragdollified corpse looting screen into the merged view: your own full loadout
 * panel on the left, the corpse's gear and inventory laid out to the right.
 *
 * WHICH MOD OWNS THE CORPSE
 * -------------------------
 * As of Ragdollified 1.0.0-RELEASE the corpse feature was split out of the core mod into a
 * separate addon, "Ragdollified Player Corpse" (modid {@code ragdollifiedpc}). The menu class
 * moved with it:
 *
 *   old: ragdollified   -> com.raiiiden.ragdollified.menu.CorpseMenu     (0.9.x-BETA and earlier)
 *   new: ragdollifiedpc -> com.raiiiden.ragdollifiedpc.menu.CorpseMenu   (0.1.0-BETA and later)
 *
 * Both are matched below, so this works whether the pack ships the old all-in-one build or
 * the new core + addon pair. Matching on class NAME (rather than compiling against either)
 * keeps Ragdollified an entirely optional dependency.
 *
 * LAYOUT OF THE CORPSE CONTAINER (verified by disassembling CorpseMenu's constructor in
 * ragdollifiedpc-1.20.1-0.1.0-BETA and, before it, ragdollified-1.20.1-0.9.0-BETA - NOT
 * guessed; getting this wrong would misplace or lose items. The two builds agree exactly,
 * which is why the split needed no layout changes here):
 *
 *   corpseSlots = 41 + curioCount
 *     0..35   main inventory + hotbar  (0-8 hotbar, 9-35 main, i.e. vanilla Inventory order)
 *     36..39  armor, vanilla order     (36 feet, 37 legs, 38 chest, 39 head)
 *     40      offhand
 *     41..    one slot per entry in the menu's curioIds list
 *
 * The addon exposes that count as CorpseEntity.VANILLA_SLOTS = 41 and as the menu's own
 * {@code corpseSlots} field; the field is preferred below when readable, so a future change
 * to the fixed-slot count is picked up rather than tripping the guard. If the size still
 * doesn't line up, the redirect stands down rather than silently laying slots out wrongly.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public class CorpseOpenRedirect {

    /** Corpse-owning mods, newest first. Only one of these is ever installed at a time. */
    private static final List<String> CORPSE_MODIDS = List.of("ragdollifiedpc", "ragdollified");

    /** Menu classes to redirect, matched by name so neither mod is a compile dependency. */
    private static final List<String> CORPSE_MENU_CLASSES = List.of(
            "com.raiiiden.ragdollifiedpc.menu.CorpseMenu",   // 1.0.0-RELEASE and later (addon)
            "com.raiiiden.ragdollified.menu.CorpseMenu");    // 0.9.x-BETA and earlier (core)

    /** Corpse entity classes, used only to recover the corpse's name for the header. */
    private static final List<String> CORPSE_ENTITY_CLASSES = List.of(
            "com.raiiiden.ragdollifiedpc.entity.CorpseEntity",
            "com.raiiiden.ragdollified.entity.CorpseEntity");

    /** Fixed (non-curio) slot count: inventory + hotbar + armor + offhand. */
    private static final int VANILLA_CORPSE_SLOTS = 41;

    /** How far to look for the corpse entity backing this menu, in blocks. */
    private static final double CORPSE_SEARCH_RADIUS = 8.0;

    private static final Set<ServerPlayer> REDIRECTING = new LinkedHashSet<>();

    private static Boolean corpseModPresent = null;

    private static boolean corpseModInstalled() {
        if (corpseModPresent == null) {
            corpseModPresent = CORPSE_MODIDS.stream().anyMatch(id -> ModList.get().isLoaded(id));
        }
        return corpseModPresent;
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!corpseModInstalled()) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        AbstractContainerMenu menu = event.getContainer();
        if (menu instanceof TarkovInventoryMenu) return;
        if (REDIRECTING.contains(serverPlayer)) return;

        String menuClass = menu.getClass().getName();
        if (!CORPSE_MENU_CLASSES.contains(menuClass)) {
            // Loud about the one failure mode that would otherwise be invisible: a corpse
            // mod IS installed, something corpse-shaped just opened, and we didn't know the
            // name. That's what the 1.0.0 package move looked like from here. Anything else
            // (chests, machines) doesn't match and stays silent.
            if (menuClass.toLowerCase(Locale.ROOT).contains("corpse")) {
                DayzHudMod.LOGGER.warn("[dayzhud] Saw an unrecognised corpse menu '{}' - the "
                        + "merged corpse view will NOT open for it. If Ragdollified moved its "
                        + "classes again, add this name to CORPSE_MENU_CLASSES.", menuClass);
            }
            return;
        }

        Container corpse = readField(menu, "corpse", Container.class);
        List<String> curioIds = readCurioIds(menu);
        if (corpse == null) return;

        // NOTE: no Rummage stand-down here, unlike ContainerOpenRedirect.
        //
        // A corpse's gear, curios, inventory and hotbar are plain Slots on the corpse's own
        // Container - the same shape as a chest, which is confirmed working in the merged
        // view. Standing down would cost the merged view for nothing. Only the corpse's worn
        // BACKPACK cannot be masked (item-handler slots, see TarkovInventoryMenu), and that
        // is handled by keeping it shut until the body is fully searched.

        int expected = expectedSlots(menu, curioIds.size());
        if (corpse.getContainerSize() != expected) {
            DayzHudMod.LOGGER.warn("[dayzhud] Corpse container is {} slots but the known layout "
                            + "expects {} - leaving {}'s own screen in place rather than "
                            + "risking misplaced items.",
                    corpse.getContainerSize(), expected, menu.getClass().getName());
            return;
        }

        Component title = corpseTitle(serverPlayer, corpse, menu);

        REDIRECTING.add(serverPlayer);
        try {
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return title;
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                    return new TarkovInventoryMenu(windowId, inv, corpse, curioIds, true);
                }
            }, buf -> {
                // Payload order is fixed by TarkovMenuTypes - see its class notes.
                buf.writeVarInt(corpse.getContainerSize());
                buf.writeBoolean(true);              // this IS a corpse, curios or not
                buf.writeVarInt(curioIds.size());    // 0 is normal for a bare NPC
            });
            DayzHudMod.LOGGER.info("[dayzhud] Opened the merged corpse view for {} ({} slots, "
                    + "{} curios, from {}).", serverPlayer.getGameProfile().getName(),
                    corpse.getContainerSize(), curioIds.size(), menuClass);
        } catch (Exception e) {
            DayzHudMod.LOGGER.warn("[dayzhud] Failed to open merged corpse view; "
                    + "leaving Ragdollified's screen in place.", e);
        } finally {
            REDIRECTING.remove(serverPlayer);
            // Drop whatever mask Rummage computed for the menu we just replaced; it does
            // not clear it itself. Sent after the open packet, so it cannot arrive first.
            com.dayzhud.mod.market.MarketNetwork.clearRummageMask(serverPlayer);
            // Snapshot Rummage's view of the menu we just opened. Taken here because the
            // command form cannot see it - opening chat closes the container first.
            RummageCompat.capture(serverPlayer.containerMenu, serverPlayer);
            // Drop whatever mask Rummage computed for the menu we just replaced; it will not
            // clear it itself. Sent after the open packet, so it cannot arrive first.
            com.dayzhud.mod.market.MarketNetwork.clearRummageMask(serverPlayer);
            // Snapshot Rummage's view of the menu we just opened. Taken here because the
            // command form cannot see it - opening chat closes the container first.
            RummageCompat.capture(serverPlayer.containerMenu, serverPlayer);
        }
    }

    /**
     * Total slots this corpse should have. The addon's menu keeps the number in its own
     * {@code corpseSlots} field, which is authoritative; the constant is the fallback for
     * builds that don't carry the field.
     */
    private static int expectedSlots(AbstractContainerMenu menu, int curioCount) {
        Integer declared = readField(menu, "corpseSlots", Integer.class);
        if (declared != null && declared > 0) return declared;
        return VANILLA_CORPSE_SLOTS + curioCount;
    }

    /**
     * The corpse's own name ("Steve's Corpse"), for the header over the loot column.
     *
     * PlayerContainerEvent.Open hands us the menu, not the MenuProvider that opened it, so
     * the title has to be recovered: find the nearby corpse entity whose inventory IS this
     * container (identity, not equality - two corpses can hold identical loot) and ask it.
     * Falls back to a plain "Corpse" if the entity has already gone or can't be read, which
     * is exactly what this screen showed before.
     */
    private static Component corpseTitle(ServerPlayer player, Container corpse,
                                         AbstractContainerMenu menu) {
        if (menu instanceof MenuProvider provider) {
            Component name = provider.getDisplayName();
            if (name != null) return name;
        }
        AABB box = player.getBoundingBox().inflate(CORPSE_SEARCH_RADIUS);
        for (Entity entity : player.level().getEntities(player, box)) {
            if (!CORPSE_ENTITY_CLASSES.contains(entity.getClass().getName())) continue;
            // Per-entity try: one corpse we can't read shouldn't stop us checking the rest.
            try {
                Method getInventory = entity.getClass().getMethod("getInventory");
                getInventory.setAccessible(true);
                if (getInventory.invoke(entity) == corpse) {
                    return entity.getDisplayName();
                }
            } catch (Exception e) {
                DayzHudMod.LOGGER.debug("[dayzhud] Couldn't read a corpse entity's inventory "
                        + "while looking for the header name.", e);
            }
        }
        return Component.literal("Corpse");
    }

    @SuppressWarnings("unchecked")
    private static List<String> readCurioIds(AbstractContainerMenu menu) {
        List<String> ids = readField(menu, "curioIds", List.class);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    /**
     * Reads a private field by name, walking up the class hierarchy. Boxed primitives are
     * handled too, so an {@code int} field can be requested as {@link Integer}.
     */
    private static <T> T readField(Object target, String name, Class<T> type) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(target);
                return type.isInstance(value) ? type.cast(value) : null;
            } catch (NoSuchFieldException ignored) {
                // Not declared here - keep walking up the hierarchy.
            } catch (Exception e) {
                DayzHudMod.LOGGER.debug("[dayzhud] Couldn't read '{}' from the corpse menu.", name, e);
                return null;
            }
        }
        DayzHudMod.LOGGER.debug("[dayzhud] Corpse menu has no field '{}'.", name);
        return null;
    }
}
