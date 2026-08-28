package com.dayzhud.mod.skill;

import com.dayzhud.mod.DayzHudMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@code /dayzhud skills ...} - inspect and reset skill progress.
 *
 *   query  [targets]                  what someone has (no argument = yourself, no permission
 *                                     needed; naming someone else needs op)
 *   reset  [targets]                  wipe all skills to 0. The XP is gone.
 *   respec [targets]                  wipe all skills AND refund every XP level spent
 *   set    &lt;skill&gt; &lt;level&gt; [targets]  force one skill to a level, for testing balance
 *
 * RESET vs RESPEC is the distinction worth keeping: reset is the blunt "undo this player's
 * progress" an admin wants after a bug, respec is the one a player would actually be given,
 * because it hands the XP back rather than deleting it. The refund is recomputed from the cost
 * curve, so it stays correct if the curve is retuned later.
 */
@Mod.EventBusSubscriber(modid = DayzHudMod.MOD_ID)
public final class SkillCommand {

    private static final int OP_LEVEL = 2;

    private SkillCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dayzhud")
                .then(Commands.literal("skills")

                        .then(Commands.literal("query")
                                .executes(ctx -> query(ctx.getSource(),
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(OP_LEVEL))
                                        .executes(ctx -> query(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets")))))

                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(OP_LEVEL))
                                .executes(ctx -> wipe(ctx.getSource(),
                                        List.of(ctx.getSource().getPlayerOrException()), false))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> wipe(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"), false))))

                        .then(Commands.literal("respec")
                                .requires(src -> src.hasPermission(OP_LEVEL))
                                .executes(ctx -> wipe(ctx.getSource(),
                                        List.of(ctx.getSource().getPlayerOrException()), true))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> wipe(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"), true))))

                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(OP_LEVEL))
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Skill.values()).map(Skill::id), builder))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> set(ctx,
                                                        List.of(ctx.getSource().getPlayerOrException())))
                                                .then(Commands.argument("targets", EntityArgument.players())
                                                        .executes(ctx -> set(ctx,
                                                                EntityArgument.getPlayers(ctx, "targets"))))))))
        );
    }

    private static int query(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            PlayerSkills skills = SkillCapability.of(player);
            if (skills == null) continue;

            source.sendSuccess(() -> Component.literal(
                    player.getGameProfile().getName() + " - " + skills.totalLevels()
                            + " levels, " + spentXp(skills) + " XP invested"), false);
            for (Skill skill : Skill.values()) {
                int level = skills.getLevel(skill);
                source.sendSuccess(() -> Component.literal("  " + skill.displayName()
                        + " " + level + "/" + skill.maxLevel()
                        + (level > 0 ? "  (" + skill.describe(level) + ")" : "")), false);
            }
        }
        return targets.size();
    }

    private static int wipe(CommandSourceStack source, Collection<ServerPlayer> targets, boolean refund) {
        for (ServerPlayer player : targets) {
            PlayerSkills skills = SkillCapability.of(player);
            if (skills == null) continue;

            int refunded = refund ? spentXp(skills) : 0;
            for (Skill skill : Skill.values()) skills.setLevel(skill, 0);
            if (refunded > 0) player.giveExperienceLevels(refunded);

            afterChange(player);

            source.sendSuccess(() -> Component.literal(refund
                    ? "Respecced " + player.getGameProfile().getName() + " (+" + refunded + " XP levels)"
                    : "Reset " + player.getGameProfile().getName() + "'s skills"), true);

            player.sendSystemMessage(Component.literal(refund
                    ? "Your skills were reset and " + refunded + " XP levels refunded."
                    : "Your skills were reset."));
        }
        return targets.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        String id = StringArgumentType.getString(ctx, "skill");
        Skill skill = Skill.byId(id);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill '" + id + "'"));
            return 0;
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");

        for (ServerPlayer player : targets) {
            PlayerSkills skills = SkillCapability.of(player);
            if (skills == null) continue;

            // setLevel clamps to the skill's own cap, so an out-of-range number here is
            // harmless rather than something that has to be validated up front.
            skills.setLevel(skill, level);
            afterChange(player);

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Set " + player.getGameProfile().getName() + "'s " + skill.displayName()
                            + " to " + skills.getLevel(skill)), true);
        }
        return targets.size();
    }

    /**
     * Everything that has to happen after a level changes by any route: re-derive attributes,
     * top the player back up (their stamina cap may just have shrunk), and push the new state.
     */
    private static void afterChange(ServerPlayer player) {
        SkillEffects.reapply(player);
        StaminaSystem.refill(player);
        SkillEffects.sync(player);
    }

    /** Total XP levels invested, recomputed from the cost curve rather than stored. */
    private static int spentXp(PlayerSkills skills) {
        int total = 0;
        for (Skill skill : Skill.values()) {
            for (int level = 1; level <= skills.getLevel(skill); level++) {
                total += skill.costFor(level);
            }
        }
        return total;
    }
}
