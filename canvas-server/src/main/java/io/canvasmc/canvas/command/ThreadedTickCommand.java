package io.canvasmc.canvas.command;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Pair;

import static io.papermc.paper.adventure.PaperAdventure.asAdventure;
import static net.minecraft.server.commands.TickCommand.DEFAULT_TICKRATE;

public class ThreadedTickCommand extends AbstractCommand {
    public static final String UNCAPPED_TICKRATE = String.valueOf(-1);
    public static final String[] RATE_SUGGESTIONS = new String[]{DEFAULT_TICKRATE, UNCAPPED_TICKRATE};

    public ThreadedTickCommand() {
        super();
        this.requirePermission(3, "canvas.world.command.threadedtick");
        this.registerNode("diagnosis", new Node().executes((_, source) -> ThreadedTickDiagnosis.execute(source.getBukkitSender())));
        this.registerNode("threadedstate", new Node()
            .argument(multiArg(
                new ArgumentNode<>(StringArgumentType.string()).suggests(ThreadedTickCommand::getThreadStates),
                this::unbind,
                this::rebind,
                this::freeze,
                this::unfreeze,
                this::rate
            ))
        );
    }

    public static void execute(@NotNull String state, Consumer<ServerTickRateManager> action) throws CommandSyntaxException {
        TickRegionState tickState;

        try {
            tickState = TickRegionState.fromString(state);
        } catch (IllegalArgumentException e) {
            Message message = Component.literal(e.getMessage());
            throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
        }

        switch (tickState) {
            case GLOBAL -> {
                action.accept(MinecraftServer.getServer().tickRateManager());
                for (ServerLevel level : MinecraftServer.getThreadedServer().getAllLevels()) {
                    action.accept(level.tickRateManager());
                }
            }
            case MAIN -> action.accept(MinecraftServer.getServer().tickRateManager());
            case DIMENSION -> {
                ResourceLocation location = ResourceLocation.parse(state);
                ServerLevel level = MinecraftServer.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, location));
                if (level == null) {
                    Message message = Component.literal("Couldn't build level: " + state);
                    throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
                }
                action.accept(level.tickRateManager());
            }
        }
    }

    private static String @NotNull [] getThreadStates() {
        List<String> states = new ArrayList<>();
        states.add("global");
        states.add("main");
        states.addAll(MinecraftServer.getThreadedServer().getAllLevels()
                                     .stream()
                                     .map(ServerLevel::dimension)
                                     .map(ResourceKey::location)
                                     .map(ResourceLocation::toString).collect(Collectors.toSet()));
        return states.toArray(new String[0]);
    }

    private @NotNull Pair<String, Node> unbind() {
        Node node = new Node();
        node.executes((context, source) -> {
            String state = StringArgumentType.getString(context, "threadstate");
            execute(state, (serverTickRateManager) -> serverTickRateManager.toggleUnlockTickRate(true));
            source.sendSuccess(asAdventure(Component.literal("Toggled unbound tickrate on thread(s).")), true);
        });
        return new Pair<>("unbind", node);
    }

    private @NotNull Pair<String, Node> rebind() {
        Node node = new Node();
        node.executes((context, source) -> {
            String state = StringArgumentType.getString(context, "threadstate");
            execute(state, (serverTickRateManager) -> serverTickRateManager.toggleUnlockTickRate(false));
            source.sendSuccess(asAdventure(Component.literal("Rebound tickrate to thread(s).")), true);
        });
        return new Pair<>("rebind", node);
    }

    private @NotNull Pair<String, Node> freeze() {
        Node node = new Node();
        node.executes((context, source) -> execute(StringArgumentType.getString(context, "threadstate"), (serverTickRateManager) -> {
            if (serverTickRateManager.isSprinting()) {
                serverTickRateManager.stopSprinting();
            }

            if (serverTickRateManager.isSteppingForward()) {
                serverTickRateManager.stopStepping();
            }

            serverTickRateManager.setFrozen(true);
            source.sendSuccess(asAdventure(Component.translatable("commands.tick.status.frozen")), true);
        }));
        return new Pair<>("freeze", node);
    }

    private @NotNull Pair<String, Node> unfreeze() {
        Node node = new Node();
        node.executes((context, source) -> execute(StringArgumentType.getString(context, "threadstate"), (serverTickRateManager) -> {
            serverTickRateManager.setFrozen(false);
            source.sendSuccess(asAdventure(Component.translatable("commands.tick.status.running")), true);
        }));
        return new Pair<>("unfreeze", node);
    }

    private @NotNull Pair<String, Node> rate() {
        Node node = new Node();
        node.argument(new ArgumentNode<>(FloatArgumentType.floatArg(-1.0F, 10000.0F))
            .suggests(() -> RATE_SUGGESTIONS)
            .then(new Pair<>("rate", new Node()
                    .executes((context, source) -> {
                        float rate = FloatArgumentType.getFloat(context, "rate");
                        execute(StringArgumentType.getString(context, "threadstate"), (serverTickRateManager) -> {
                            if (rate == -1) {
                                serverTickRateManager.toggleUnlockTickRate(true);
                            } else if (rate > 0) {
                                serverTickRateManager.setTickRate(rate);
                            }
                        });
                        if (rate == -1) {
                            source.sendSuccess(asAdventure(Component.literal("Toggled unbound tickrate.")), true);
                        } else if (rate > 0) {
                            String string = String.format(Locale.ROOT, "%.1f", rate);
                            source.sendSuccess(asAdventure(Component.translatable("commands.tick.rate.success", string)), true);
                        }
                    })
                )
            )
        );
        return new Pair<>("rate", node);
    }

    public enum TickRegionState {
        GLOBAL, MAIN, DIMENSION;

        public static TickRegionState fromString(@NotNull String state) {
            if (state.equalsIgnoreCase("global")) return GLOBAL;
            if (state.equalsIgnoreCase("main")) return MAIN;
            if (state.contains(":")) return DIMENSION;
            throw new IllegalArgumentException("State isn't valid or known: " + state);
        }
    }
}
