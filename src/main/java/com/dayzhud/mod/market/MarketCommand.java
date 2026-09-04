package com.dayzhud.mod.market;

import com.dayzhud.mod.DayzHudMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /money and /market. Zones live in world save data rather than config because a runtime
 * command cannot write back into a ForgeConfigSpec file, and because a hideout belongs to
 * the world it was built in.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class MarketCommand {

    private MarketCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("money")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    long balance = WalletCapability.balanceOf(player);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "command.dayzhud.money.balance", Money.withSymbol(balance)), false);
                    return 1;
                })
                .then(Commands.literal("pay")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> pay(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"))))))
                .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> grant(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"))))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> set(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount")))))));

        d.register(Commands.literal("market")
                .then(Commands.literal("open").requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            MarketAccess.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .executes(ctx -> debug(ctx.getSource())))
                .then(Commands.literal("rummage").requires(s -> s.hasPermission(2))
                        .executes(ctx -> rummage(ctx.getSource())))
                .then(Commands.literal("zone").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> addZone(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> removeZone(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listZones(ctx.getSource())))));
    }

    /** Reports what the catalogue actually contains, rather than what it looks like. */
    private static int debug(CommandSourceStack source) {
        var offers = MarketCatalog.offers();
        source.sendSuccess(() -> Component.literal(
                "Market: " + offers.size() + " offers, " + MarketPrices.all().size()
                        + " price rows, rev " + MarketCatalog.revision()), false);
        java.util.Map<String, Integer> byCat = new java.util.LinkedHashMap<>();
        for (MarketOffer o : offers) byCat.merge(o.category(), 1, Integer::sum);
        for (String cat : MarketCatalog.categories()) {
            source.sendSuccess(() -> Component.literal("  " + cat + ": " + byCat.get(cat)), false);
        }
        for (String line : TaczMarketCompat.debugReport()) {
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return 1;
    }

    /** Dumps Rummage's view of the menu the player currently has open. */
    private static int rummage(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        for (String line : RummageCompat.describe(player.containerMenu, player)) {
            source.sendSuccess(() -> Component.literal(line), false);
            DayzHudMod.LOGGER.info("[rummage] {}", line);
        }
        return 1;
    }

    private static int pay(CommandSourceStack source, ServerPlayer target, long amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer from = source.getPlayerOrException();
        if (from == target) return 0;
        Wallet mine = WalletCapability.of(from);
        Wallet theirs = WalletCapability.of(target);
        if (mine == null || theirs == null) return 0;
        // spend() is the single check-and-charge step, so there is no window where the
        // balance has been tested but not yet deducted.
        if (!mine.spend(amount)) {
            source.sendFailure(Component.translatable("command.dayzhud.money.poor"));
            return 0;
        }
        theirs.add(amount);
        MarketNetwork.syncWallet(from);
        MarketNetwork.syncWallet(target);
        source.sendSuccess(() -> Component.translatable("command.dayzhud.money.paid",
                Money.withSymbol(amount), target.getDisplayName()), false);
        target.displayClientMessage(Component.translatable("command.dayzhud.money.received",
                Money.withSymbol(amount), from.getDisplayName()), false);
        return 1;
    }

    private static int grant(CommandSourceStack source, ServerPlayer target, long amount) {
        Wallet wallet = WalletCapability.of(target);
        if (wallet == null) return 0;
        wallet.add(amount);
        MarketNetwork.syncWallet(target);
        source.sendSuccess(() -> Component.translatable("command.dayzhud.money.granted",
                Money.withSymbol(amount), target.getDisplayName()), true);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer target, long amount) {
        Wallet wallet = WalletCapability.of(target);
        if (wallet == null) return 0;
        wallet.setBalance(amount);
        MarketNetwork.syncWallet(target);
        source.sendSuccess(() -> Component.translatable("command.dayzhud.money.granted",
                Money.withSymbol(amount), target.getDisplayName()), true);
        return 1;
    }

    private static int addZone(CommandSourceStack source, String name, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        SafeZoneData.get(level).add(new SafeZoneData.Zone(name, pos.getX(), pos.getY(), pos.getZ(), radius));
        source.sendSuccess(() -> Component.translatable("command.dayzhud.zone.added", name, radius), true);
        return 1;
    }

    private static int removeZone(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getPlayerOrException().serverLevel();
        boolean removed = SafeZoneData.get(level).remove(name);
        if (removed) {
            source.sendSuccess(() -> Component.translatable("command.dayzhud.zone.removed", name), true);
            return 1;
        }
        source.sendFailure(Component.translatable("command.dayzhud.zone.missing", name));
        return 0;
    }

    private static int listZones(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getPlayerOrException().serverLevel();
        var zones = SafeZoneData.get(level).zones();
        if (zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dayzhud.zone.none"), false);
            return 0;
        }
        for (SafeZoneData.Zone z : zones) {
            source.sendSuccess(() -> Component.literal(
                    " - " + z.name() + " @ " + z.x() + ", " + z.z() + " r=" + z.radius()), false);
        }
        return zones.size();
    }
}
