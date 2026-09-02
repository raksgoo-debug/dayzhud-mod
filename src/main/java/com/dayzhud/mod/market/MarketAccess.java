package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How you reach a trader: a terminal block you built a hideout around, or a laptop used
 * inside a registered safe zone.
 *
 * Neither is a block this mod registers. The terminal is whatever the pack already has -
 * tarkovdayz's desktop PC by default - which means no new model or texture, and no broken
 * model if the pack that owns it is not installed. It also lets a server point the config
 * at any block it likes without a code change.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class MarketAccess {

    private static Set<ResourceLocation> blockIds;
    private static Set<ResourceLocation> itemIds;

    private MarketAccess() {}

    public static void invalidate() {
        blockIds = null;
        itemIds = null;
    }

    private static Set<ResourceLocation> parse(List<? extends String> raw) {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : raw) {
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id != null) out.add(id);
        }
        return out;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!MarketConfig.ENABLED.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (blockIds == null) blockIds = parse(MarketConfig.TERMINAL_BLOCKS.get());
        if (blockIds.isEmpty()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !blockIds.contains(id)) {
            // Not a terminal block - but the player may be holding a terminal ITEM while
            // aiming at one. RightClickBlock fires INSTEAD of RightClickItem whenever the
            // crosshair is on a block, so without this the laptop only worked pointed at
            // open air, which in play just reads as the laptop being broken.
            tryTerminalItem(event, player, event.getItemStack());
            return;
        }

        // Sneaking is how you place a block against the terminal instead of using it.
        if (player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()) return;

        if (MarketConfig.BLOCK_REQUIRES_SAFE_ZONE.get() && !inSafeZone(player)) {
            refuse(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        open(player);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!MarketConfig.ENABLED.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (itemIds == null) itemIds = parse(MarketConfig.TERMINAL_ITEMS.get());
        if (itemIds.isEmpty()) return;

        tryTerminalItem(event, player, event.getItemStack());
    }

    /** Shared by both interaction events, so the laptop behaves the same either way. */
    private static void tryTerminalItem(PlayerInteractEvent event, ServerPlayer player, ItemStack stack) {
        if (itemIds == null) itemIds = parse(MarketConfig.TERMINAL_ITEMS.get());
        if (itemIds.isEmpty() || stack.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !itemIds.contains(id)) return;

        if (MarketConfig.ITEM_REQUIRES_SAFE_ZONE.get() && !inSafeZone(player)) {
            refuse(player);
            event.setCanceled(true);
            return;
        }
        open(player);
        event.setCanceled(true);
    }

    public static boolean inSafeZone(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        BlockPos pos = player.blockPosition();
        return SafeZoneData.get(level).isInside(pos);
    }

    private static void refuse(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.dayzhud.market.no_safezone"), true);
    }

    /** Opens the trader screen and immediately pushes the catalogue and balance after it. */
    public static void open(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new MarketMenu(windowId, inv),
                Component.translatable("gui.dayzhud.market.title")));
        MarketNetwork.sendPrices(player);
        MarketNetwork.sendCatalogue(player);
        MarketNetwork.syncWallet(player);
    }
}
