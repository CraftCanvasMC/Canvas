package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.command.sub.RegionBarSubCommand;
import io.canvasmc.canvas.command.sub.RegionTickSubCommand;
import io.canvasmc.canvas.command.sub.ReloadSubCommand;
import io.canvasmc.canvas.command.sub.SetMaxPlayersSubCommand;
import io.canvasmc.canvas.command.sub.WorldDistanceSubCommand;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.NullMarked;

import static net.minecraft.commands.Commands.literal;

@NullMarked
public class CanvasCommands {
    public static final CanvasCommands INSTANCE;

    static final TextColor HEADER = TextColor.color(160, 90, 245);
    static final TextColor PRIMARY = TextColor.color(118, 52, 212);
    static final TextColor SECONDARY = TextColor.color(182, 118, 248);
    static final TextColor INFORMATION = TextColor.color(208, 168, 252);
    static final TextColor LIST = TextColor.color(62, 18, 148);
    static final TextColor ACCENT = TextColor.color(230, 200, 255);
    static final TextColor MUTED = TextColor.color(98, 52, 172);

    static {
        INSTANCE = new CanvasCommands();
        registerCommand(SetMaxPlayersSubCommand.class);
        registerCommand(RegionBarSubCommand.class);
        registerCommand(WorldDistanceSubCommand.class);
        registerCommand(ReloadSubCommand.class);
        registerCommand(RegionTickSubCommand.class);
    }

    private final List<SubCommand> subCommands = new LinkedList<>();

    public static void registerCommand(final Class<? extends SubCommand> command) {
        INSTANCE.register(command);
    }

    public void register(final Class<? extends SubCommand> command) {
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

    public void build(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext context) {
        final LiteralArgumentBuilder<CommandSourceStack> base = literal("canvas")
            .requires(source -> {
                // the "base" is just the root command, "help" is builtin, so we need these hard coded
                boolean canUse = permission("help").test(source);

                // if we have sub commands, they should also allow usage of the base canvas command
                // since it's a way to access said sub command

                for (final SubCommand subCommand : this.subCommands) {
                    if (canUse) break;

                    if (permission(subCommand.getName()).test(source)) {
                        canUse = true;
                    }
                }

                return canUse;
            });

        for (final SubCommand sub : subCommands) {
            final String name = sub.getName();

            base.then(sub.construct(literal(name)
                .requires(permission(name)), context));

            if (sub.isAllowedSelfCommand()) {
                dispatcher.register(sub.construct(literal(name)
                    .requires(permission(name)), context));

                dispatcher.register(sub.construct(literal("canvas:" + name)
                    .requires(permission(name)), context));
            }
        }

        HelpCommand.constructHelpSystem(base, () -> this.subCommands);
        dispatcher.register(base);
    }

    public static Predicate<CommandSourceStack> permission(final String node) {
        return source -> source.hasPermission(Permissions.COMMANDS_ADMIN, "canvas.command." + node);
    }
}
