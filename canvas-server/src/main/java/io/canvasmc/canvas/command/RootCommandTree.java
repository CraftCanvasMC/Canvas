package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.command.sub.RambarCommand;
import io.canvasmc.canvas.command.sub.RegionTickCommand;
import io.canvasmc.canvas.command.sub.ReloadCommand;
import io.canvasmc.canvas.command.sub.SetMaxPlayersCommand;
import io.canvasmc.canvas.command.sub.TpsBarCommand;
import io.canvasmc.canvas.command.sub.WorldDistanceCommand;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import static net.minecraft.commands.Commands.literal;

@NullMarked
public class RootCommandTree {
    private static final TextColor HEADER = TextColor.color(79, 164, 240);
    private static final TextColor PRIMARY = TextColor.color(48, 145, 237);
    private static final TextColor SECONDARY = TextColor.color(104, 177, 240);
    private static final TextColor INFORMATION = TextColor.color(145, 198, 243);
    private static final TextColor LIST = TextColor.color(33, 97, 188);
    private static final TextColor ACCENT = TextColor.color(173, 216, 255);
    private static final TextColor MUTED = TextColor.color(80, 120, 170);

    public static final RootCommandTree INSTANCE;

    static {
        INSTANCE = new RootCommandTree();
        INSTANCE.register(SetMaxPlayersCommand.class);
        INSTANCE.register(TpsBarCommand.class);
        INSTANCE.register(RambarCommand.class);
        INSTANCE.register(WorldDistanceCommand.class);
        INSTANCE.register(ReloadCommand.class);
        INSTANCE.register(RegionTickCommand.class);
    }

    private final List<Command> subCommands = new LinkedList<>();

    private Component buildDetailComponent(Command subCommand) {
        String name = subCommand.getName();
        String description = subCommand.getDescription();
        boolean selfCmd = subCommand.isAllowedSelfCommand();

        TextComponent.Builder builder = Component.text()
            .append(Component.text("----", SECONDARY))
            .append(Component.text("/canvas " + name, HEADER).decorate(TextDecoration.BOLD))
            .append(Component.text("----", SECONDARY))
            .appendNewline()
            .appendNewline();

        builder.append(Component.text("  Description  ", MUTED).decorate(TextDecoration.BOLD))
            .append(Component.text(description != null ? description : "No description provided.", ACCENT))
            .appendNewline();

        builder.append(Component.text("  Permission   ", MUTED).decorate(TextDecoration.BOLD))
            .append(Component.text("canvas.command." + name, ACCENT))
            .appendNewline();

        builder.append(Component.text("  Standalone   ", MUTED).decorate(TextDecoration.BOLD))
            .append(selfCmd
                ? Component.text("Yes ", TextColor.color(100, 220, 140)).append(Component.text("(/" + name + ", /canvas:" + name + ")", INFORMATION))
                : Component.text("No", TextColor.color(220, 100, 100)))
            .appendNewline()
            .appendNewline();

        builder.append(Component.text("-----------------------", SECONDARY));

        return builder.build();
    }

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

        root.then(literal("help")
            .requires(source -> source.getSender().isOp() || source.getSender().hasPermission("canvas.command.help"))
            .executes(context -> {
                CommandSender bukkitSender = context.getSource().getBukkitSender();

                TextComponent.Builder builder = Component.text()
                    .append(Component.text("----", SECONDARY))
                    .append(Component.text("Canvas Commands", HEADER).decorate(TextDecoration.BOLD))
                    .append(Component.text("----", SECONDARY))
                    .appendNewline();

                for (Command subCommand : subCommands) {
                    String name = subCommand.getName();
                    if (!bukkitSender.hasPermission("canvas.command." + name)) {
                        continue;
                    }

                    Component hoverText = Component.text()
                        .append(Component.text("Click to view further details", INFORMATION))
                        .build();

                    Component detailComponent = buildDetailComponent(subCommand);

                    Component entry = Component.text()
                        .append(Component.text("- ").color(LIST))
                        .append(Component.text("/").color(SECONDARY))
                        .append(Component.text(name, PRIMARY)
                            .decorate(TextDecoration.UNDERLINED)
                            .hoverEvent(HoverEvent.showText(hoverText))
                            .clickEvent(ClickEvent.callback((audience) -> audience.sendMessage(detailComponent))))
                        .appendNewline()
                        .build();

                    builder.append(entry);
                }

                builder.append(Component.text("-----------------------", SECONDARY));

                bukkitSender.sendMessage(builder.build());
                return 1;
            }));
        dispatcher.register(root);
    }

    public void register(Class<? extends Command> command) {
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
