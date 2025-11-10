package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.command.sub.ReloadCommand;
import io.canvasmc.canvas.command.sub.SetMaxPlayersCommand;
import io.canvasmc.canvas.command.sub.TpsBarCommand;
import io.canvasmc.canvas.command.sub.WorldDistanceCommand;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class RootCommandTree {
    public static final RootCommandTree INSTANCE;

    static {
        INSTANCE = new RootCommandTree();
        INSTANCE.register(SetMaxPlayersCommand.class);
        INSTANCE.register(TpsBarCommand.class);
        INSTANCE.register(WorldDistanceCommand.class);
        INSTANCE.register(ReloadCommand.class);
    }

    private final List<Command> subCommands = new LinkedList<>();

    public void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("canvas")
            .requires(source -> source.getSender().isOp() || source.getSender().hasPermission("canvas.command"));

        for (Command subCommand : subCommands) {
            String name = subCommand.getName();

            root.then(subCommand.construct(literal(name)
                .requires(source -> source.getSender().isOp() || source.getSender().hasPermission("canvas.command." + name))));

            if (subCommand.isAllowedSelfCommand()) {
                dispatcher.register(subCommand.construct(literal(name)
                    .requires(source -> source.getSender().isOp() || source.getSender().hasPermission("canvas.command." + name))));

                dispatcher.register(subCommand.construct(literal("canvas:" + name)
                    .requires(source -> source.getSender().isOp() || source.getSender().hasPermission("canvas.command." + name))));
            }
        }

        dispatcher.register(root);
    }

    public void register(@NotNull Class<? extends Command> command) {
        try {
            if (command.getDeclaredConstructor().getParameterCount() != 0) {
                throw new IllegalArgumentException("Command must have no-arg constructor");
            }
            this.subCommands.add(
                command.getDeclaredConstructor().newInstance()
            );
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
