package io.canvasmc.canvas.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.level.MinecraftServerWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.thread.BlockableEventLoop;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class TickCommand {
    private static final float MAX_TICKRATE = 1_000_000F; // 1mil
    private static final String DEFAULT_TICKRATE = String.valueOf(20);
    private static final DynamicCommandExceptionType ERROR_INVALID_THREAD_STATE = new DynamicCommandExceptionType(
        (name) -> Component.literal("Unknown tickloop '" + name + "'")
    );

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("threadedtick").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.server.command.threadedtick"))
                .then(CommandNodes.RATE.node())
                .then(CommandNodes.SPRINT.node())
                .then(CommandNodes.UNFREEZE.node())
                .then(CommandNodes.FREEZE.node())
                .then(CommandNodes.DUMP.node())
        );
    }

    public static void dumpDiagnosis(CommandSender sender) {
        ThreadedTickDiagnosis.dump(sender);
    }

    private static int consume(@NotNull CommandContext<CommandSourceStack> context, Consumer<ServerTickRateManager> commandProcessor, Supplier<Integer> finalizer) throws CommandSyntaxException {
        String providedState = context.getArgument("state", String.class);

        switch (providedState) {
            case "all" -> {
                commandProcessor.accept(MinecraftServer.getServer().tickRateManager());
                for (final AbstractTickLoop<?, ?> tickLoop : MinecraftServer.getThreadedServer().getTickLoops()) {
                    if (!tickLoop.tickRateManager().equals(MinecraftServer.getServer().tickRateManager())) {
                        commandProcessor.accept(tickLoop.tickRateManager());
                    }
                }
            }
            case "main" -> commandProcessor.accept(MinecraftServer.getServer().tickRateManager());
            case "worlds" -> {
                for (final AbstractTickLoop<?, ?> tickLoop : MinecraftServer.getThreadedServer().getTickLoops()) {
                    if (tickLoop instanceof MinecraftServerWorld) {
                        commandProcessor.accept(tickLoop.tickRateManager());
                    }
                }
            }
            default -> {
                try {
                    AbstractTickLoop<?, ?> tickLoop = AbstractTickLoop.getByName(providedState);
                    commandProcessor.accept(tickLoop.tickRateManager());
                } catch (IllegalArgumentException argumentException) {
                    throw ERROR_INVALID_THREAD_STATE.create(providedState);
                }
            }
        }

        return finalizer.get();
    }

    private static String @NotNull [] getAllTickLoops(Predicate<AbstractTickLoop<?, ?>> filter) {
        List<String> allStates = new ArrayList<>(MinecraftServer.getThreadedServer().getTickLoops().stream().filter(filter).map(BlockableEventLoop::name).toList());
        allStates.add("main");
        allStates.add("worlds");
        allStates.add("all");
        return allStates.toArray(new String[0]);
    }

    private static void freeze(boolean frozen, ServerTickRateManager serverTickRateManager) {
        if (frozen) {
            if (serverTickRateManager.isSprinting()) {
                serverTickRateManager.stopSprinting();
            }

            if (serverTickRateManager.isSteppingForward()) {
                serverTickRateManager.stopStepping();
            }
        }

        serverTickRateManager.setFrozen(frozen);
    }

    private enum CommandNodes {
        RATE(() -> literal("rate")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.server.command.threadedtick.rate"))
            .then(
                argument("state", StringArgumentType.string())
                    .suggests((_, suggestionsBuilder) -> SharedSuggestionProvider.suggest(getAllTickLoops((loop) -> !loop.tickRateManager().equals(MinecraftServer.getServer().tickRateManager())), suggestionsBuilder))
                    .then(
                        argument("rate", FloatArgumentType.floatArg(1.0F, TickCommand.MAX_TICKRATE))
                            .suggests((_, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{DEFAULT_TICKRATE}, suggestionsBuilder))
                            .executes((context) -> {
                                float tickRate = context.getArgument("rate", Float.class);
                                return consume(
                                    context,
                                    (tickRateManager -> tickRateManager.setTickRate(tickRate)),
                                    () -> {
                                        String string = String.format(Locale.ROOT, "%.1f", tickRate);
                                        context.getSource().sendSuccess(() -> Component.translatable("commands.tick.rate.success", string), true);
                                        return 1;
                                    }
                                );
                            })
                    ))
        ),
        SPRINT(() -> literal("sprint")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.server.command.threadedtick.sprint"))
            .then(
                argument("state", StringArgumentType.string())
                    .suggests((_, suggestionsBuilder) -> SharedSuggestionProvider.suggest(getAllTickLoops((loop) -> !loop.tickRateManager().equals(MinecraftServer.getServer().tickRateManager())), suggestionsBuilder))
                    .then(literal("stop").executes(context -> {
                            final AtomicBoolean flag = new AtomicBoolean(false);
                            CommandSourceStack source = context.getSource();
                            return consume(
                                context,
                                (tickRateManager -> flag.set(tickRateManager.stopSprinting())),
                                () -> {
                                    if (flag.get()) {
                                        source.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
                                        return 1;
                                    } else {
                                        source.sendFailure(Component.translatable("commands.tick.sprint.stop.fail"));
                                        return 0;
                                    }
                                }
                            );
                        })
                    ).then(
                        argument("time", TimeArgument.time(1))
                            .suggests((_, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"60s", "1d", "3d"}, suggestionsBuilder))
                            .executes(context -> {
                                int sprintTime = IntegerArgumentType.getInteger(context, "time");
                                final AtomicBoolean flag = new AtomicBoolean(false);
                                CommandSourceStack source = context.getSource();
                                return consume(
                                    context,
                                    (tickRateManager -> flag.set(tickRateManager.requestGameToSprint(sprintTime))),
                                    () -> {
                                        if (flag.get()) {
                                            source.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
                                        }

                                        source.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), true);
                                        return 1;
                                    }
                                );
                            })
                    ))
        ),
        UNFREEZE(() -> literal("unfreeze")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.server.command.threadedtick.unfreeze"))
            .then(
                argument("state", StringArgumentType.string())
                    .suggests((_, suggestionsBuilder) -> SharedSuggestionProvider.suggest(getAllTickLoops((loop) -> !loop.tickRateManager().equals(MinecraftServer.getServer().tickRateManager())), suggestionsBuilder))
                    .executes(context -> {
                        boolean frozen = false;
                        CommandSourceStack source = context.getSource();
                        return consume(
                            context,
                            (tickRateManager -> freeze(frozen, tickRateManager)),
                            () -> {
                                source.sendSuccess(() -> Component.translatable("commands.tick.status.running"), true);
                                return 0;
                            }
                        );
                    })
            )
        ),
        FREEZE(() -> literal("freeze")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.server.command.threadedtick.freeze"))
            .then(
                argument("state", StringArgumentType.string())
                    .suggests((_, suggestionsBuilder) -> SharedSuggestionProvider.suggest(getAllTickLoops((loop) -> !loop.tickRateManager().equals(MinecraftServer.getServer().tickRateManager())), suggestionsBuilder))
                    .executes(context -> {
                        boolean frozen = true;
                        CommandSourceStack source = context.getSource();
                        return consume(
                            context,
                            (tickRateManager -> freeze(frozen, tickRateManager)),
                            () -> {
                                source.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), true);
                                return 1;
                            }
                        );
                    })
            )
        ),
        DUMP(() -> literal("diagnosis")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.server.command.threadedtick.diagnosis"))
            .executes(context -> {
                dumpDiagnosis(context.getSource().getBukkitSender());
                return 1;
            })
        );
        private final Supplier<LiteralArgumentBuilder<CommandSourceStack>> builder;

        CommandNodes(Supplier<LiteralArgumentBuilder<CommandSourceStack>> builder) {
            this.builder = builder;
        }

        public LiteralArgumentBuilder<CommandSourceStack> node() {
            return builder.get();
        }
    }
}
