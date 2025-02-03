package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.canvasmc.canvas.server.level.distance.WorldSpecificViewDistancePersistentState;
import io.canvasmc.canvas.server.level.distance.command.CommandUtils;
import io.canvasmc.canvas.server.level.distance.component.WorldSpecificViewDistanceComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ViewDistanceCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(
            literal("viewdistance")
                .then(literal("set")
                    .requires((src) -> src.hasPermission(2, "canvas.world.command.viewdistance"))
                    .then(literal("global")
                        .then(argument("viewDistance", IntegerArgumentType.integer(0, 255))
                            .executes(ViewDistanceCommand::setGlobalViewDistance)))
                    .then(argument("dimension", DimensionArgument.dimension())
                        .then(argument("viewDistance", IntegerArgumentType.integer(0, 255))
                            .executes(ViewDistanceCommand::setWorldViewDistance))))
                .then(literal("get")
                    .then(literal("global")
                        .executes(ViewDistanceCommand::getGlobalViewDistance))
                    .then(argument("dimension", DimensionArgument.dimension())
                        .executes(ViewDistanceCommand::getWorldViewDistance))));
    }

    public static int setWorldViewDistance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int viewdist = IntegerArgumentType.getInteger(ctx, "viewDistance");
        CommandSourceStack src = ctx.getSource();
        ServerLevel w = DimensionArgument.getDimension(ctx, "dimension");

        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(w);
        state.setLocalViewDistance(viewdist);

        for (ServerPlayer spe : w.players()) {
            spe.moonrise$getChunkLoader().updateClientChunkRadius(viewdist == 0 ? w.getServer().getPlayerList().getViewDistance() : viewdist - 1);
        }

        w.getChunkSource().setViewDistance(viewdist == 0 ? w.getServer().getPlayerList().getViewDistance() : viewdist - 1);

        src.sendSuccess(() -> CommandUtils.getMessage(
            "Set view distance of world %s to %d",
            CommandUtils.getRegistryId(w), viewdist), true);
        return 1;
    }

    public static int getWorldViewDistance(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel w = DimensionArgument.getDimension(ctx, "dimension");

        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(w);
        int viewDist = state.getLocalViewDistance();

        if (viewDist != 0) {
            src.sendSuccess(() -> CommandUtils.getMessage(
                "View distance of world %s is %d",
                CommandUtils.getRegistryId(w), viewDist), false);
        } else {
            src.sendSuccess(() -> CommandUtils.getMessage("View distance of world %s is unspecified (currently %d)",
                CommandUtils.getRegistryId(w), src.getServer().getPlayerList().getViewDistance() + 1), false);
        }

        return 1;
    }

    public static int getGlobalViewDistance(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int viewDist = src.getServer().getPlayerList().getViewDistance() + 1;

        src.sendSuccess(() -> CommandUtils.getMessage("Server-wide view distance is currently %d", viewDist), false);

        return 1;
    }

    public static int setGlobalViewDistance(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int viewDist = IntegerArgumentType.getInteger(ctx, "viewDistance");

        if (src.getServer().isDedicatedServer()) {
            src.getServer().getPlayerList().setViewDistance(viewDist - 1);

            src.sendSuccess(() -> CommandUtils.getMessage("Set server-wide view distance to %d", viewDist), true);
        } else {
            var component = WorldSpecificViewDistanceComponents.GLOBAL_DISTANCES.get(src.getServer().getWorldData());

            component.globalViewDistance = viewDist;

            if (viewDist != 0) {
                src.sendSuccess(() -> CommandUtils.getMessage("Set save view distance to %d", viewDist), true);
            } else {
                src.sendSuccess(() -> CommandUtils.getMessage("Unset save view distance"), true);
            }
        }
        return 1;
    }


}
