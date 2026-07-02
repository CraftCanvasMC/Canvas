package io.canvasmc.canvas.command;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.canvasmc.canvas.util.Util;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import static io.canvasmc.canvas.command.CanvasCommands.ACCENT;
import static io.canvasmc.canvas.command.CanvasCommands.HEADER;
import static io.canvasmc.canvas.command.CanvasCommands.INFORMATION;
import static io.canvasmc.canvas.command.CanvasCommands.LIST;
import static io.canvasmc.canvas.command.CanvasCommands.MUTED;
import static io.canvasmc.canvas.command.CanvasCommands.PRIMARY;
import static io.canvasmc.canvas.command.CanvasCommands.SECONDARY;
import static net.minecraft.commands.Commands.literal;

public class HelpCommand {

    public static final ClickCallback<Audience> GO_BACK_ACTION = (audience) -> {
        if (
            audience instanceof CraftPlayer craftPlayer && CanvasCommands.permission("help").test(craftPlayer.getHandle().createCommandSourceStack())
        ) {
            // audience is a player and has "help" permissions
            craftPlayer.performCommand("canvas help");
        }
        else {
            audience.sendMessage(Component.text("You do not have the required permissions to run this command", NamedTextColor.RED));
        }
    };

    // odd workaround for when the server gives permission to the help command but doesn't give
    // full "admin"/"operator" status. when the player doesn't have that, then Minecraft asks the
    // player "are you sure?" every time the "Go back" button is clicked, which is annoying
    private static final Boolean USE_LEGACY = Boolean.getBoolean("Canvas.LegacyHelpCommand");

    static void constructHelpSystem(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final Supplier<List<SubCommand>> commands
    ) {
        base.then(literal("help").requires(CanvasCommands.permission("help"))
            .executes(context -> executeHelp(commands, context)));
    }

    @SuppressWarnings("SameReturnValue")
    private static int executeHelp(
        final Supplier<List<SubCommand>> commands,
        final CommandContext<CommandSourceStack> context
    ) {
        final CommandSender bukkitCommandSender = context.getSource().getBukkitSender();

        // if sender isn't a player, we use raw text, same if we do legacy output
        if (!(bukkitCommandSender instanceof Player bukkitPlayer) || USE_LEGACY) {
            TextComponent.Builder builder = Component.text();
            appendConsoleOutput(commands, bukkitCommandSender, builder);
            bukkitCommandSender.sendMessage(builder.build());
            return Command.SINGLE_SUCCESS;
        }

        ((CraftPlayer) bukkitPlayer).taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
            final CraftPlayer craftPlayer = entityPlayer.getBukkitEntity();
            final Dialog dialog = constructMainMenu(commands.get(), context);

            // show main menu
            craftPlayer.showDialog(dialog);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static void appendConsoleOutput(
        final Supplier<List<SubCommand>> commands,
        final CommandSender bukkitSender,
        final TextComponent.Builder builder
    ) {
        builder.append(Component.text("----", SECONDARY, TextDecoration.BOLD))
            .append(Component.text("Canvas Commands", HEADER, TextDecoration.BOLD))
            .append(Component.text("----", SECONDARY, TextDecoration.BOLD))
            .appendNewline();

        for (final SubCommand subCommand : commands.get()) {
            final String name = subCommand.getName();
            if (!bukkitSender.hasPermission("canvas.command." + name)) {
                continue;
            }

            final Component hoverText = Component.text()
                .append(Component.text("Click to view further details", INFORMATION))
                .build();

            final Component detailComponent = buildTextOut(subCommand);

            final Component entry = Component.text()
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
    }

    private static Dialog constructMainMenu(
        final List<SubCommand> subCommands,
        final CommandContext<CommandSourceStack> context
    ) {
        final List<ActionButton> subCommandsAsButtons = buildSubCommandsButtons(subCommands, context);

        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(getGradientForCanvasTitled("Help")).build())
            .type(
                DialogType.multiAction(
                    Lists.asList(
                        ActionButton.create(
                            Component.text("Useful links"),
                            Component.text("A list of useful links for CanvasMC"),
                            155,
                            DialogAction.staticAction(ClickEvent.showDialog(Dialog.create(builder1 -> builder1.empty()
                                .base(
                                    DialogBase.builder(
                                        getGradientForCanvasTitled("Links")
                                    ).body(List.of(DialogBody.plainMessage(
                                        Component.text("This is a list of all CanvasMCs important links. If you need support, join the CanvasMC discord server, linked below, and ask there. Do not ask in Paper's discord server!")
                                    ))).build()
                                ).type(
                                    DialogType.multiAction(
                                        List.of(
                                            getLink("Discord", "https://canvasmc.io/discord/", "Click to open the discord server invite"),
                                            getLink("Website", "https://canvasmc.io/", "Click to open our website"),
                                            getLink("Source", "https://github.com/CraftCanvasMC/Canvas/", "Click to open the CanvasMC source repository"),
                                            getLink("Issues", "https://github.com/CraftCanvasMC/Canvas/issues/", "Click to open our issues page on our repository"),
                                            getLink("Docs", "https://docs.canvasmc.io/canvas/introduction/", "Click to open Canvas' documentation"),
                                            getLink("Modrinth", "https://modrinth.com/organization/canvasmc/", "Click to open Canvas' Modrinth org"),
                                            ActionButton.builder(Component.text("Go back"))
                                                .width(100)
                                                .action(DialogAction.staticAction(ClickEvent.callback(GO_BACK_ACTION)))
                                                .build(),
                                            ActionButton.builder(Component.text("Exit menu"))
                                                .width(100)
                                                .build()
                                        )
                                    ).columns(2).build()
                                )
                            )))
                        ),
                        subCommandsAsButtons.toArray(new ActionButton[0])
                    )
                ).columns(1).exitAction(ActionButton.builder(
                    Component.text("Exit menu")
                ).build()).build()
            )
        );
    }

    private static Component buildTextOut(final SubCommand subCommand) {
        final String name = subCommand.getName();
        final String description = subCommand.getDescription();
        final boolean selfCmd = subCommand.isAllowedSelfCommand();

        final TextComponent.Builder builder = Component.text()
            .append(Component.text("----", SECONDARY, TextDecoration.BOLD))
            .append(Component.text("/canvas " + name, HEADER, TextDecoration.BOLD))
            .append(Component.text("----", SECONDARY, TextDecoration.BOLD))
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

        builder.append(Component.text("-".repeat(16 + name.length() - 1), SECONDARY, TextDecoration.BOLD));

        return builder.append(Component.text("-----------------------", SECONDARY, TextDecoration.BOLD)).build();
    }

    @Unmodifiable
    private static List<ActionButton> buildSubCommandsButtons(
        final List<SubCommand> subCommands,
        final CommandContext<CommandSourceStack> context
    ) {
        return subCommands.stream().map((subCommand) -> {
            final String description = subCommand.getDescription();
            final String name = subCommand.getName();
            final String baseCommand = subCommand.isAllowedSelfCommand() ? "/" + name : "/canvas " + name;
            final String permission = "canvas.command." + name;

            // if source doesn't have permission, don't show
            if (!CanvasCommands.permission(name).test(context.getSource())) {
                return null;
            }

            return ActionButton.create(
                Component.text(name),
                description == null ? null : Component.text(description),
                140,
                DialogAction.staticAction(ClickEvent.showDialog(
                    Dialog.create(builder -> builder.empty()
                        .base(DialogBase.create(
                            getTitleForCommand(subCommand).color(PRIMARY).decorate(TextDecoration.BOLD),
                            null,
                            true,
                            false,
                            DialogBase.DialogAfterAction.CLOSE,
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text(description != null ? description : "No description provided for this sub command.", ACCENT)
                                ),
                                DialogBody.plainMessage(
                                    Component.text().append(Component.text("PERMISSION: ", MUTED).decorate(TextDecoration.BOLD))
                                        .append(Component.text(permission, ACCENT, TextDecoration.UNDERLINED)).build()
                                ),
                                DialogBody.plainMessage(
                                    Component.text(
                                        subCommand.isAllowedSelfCommand() ? "You are able to use this outside of \"/canvas\"" : "This is strictly only a Canvas sub command",
                                        ACCENT
                                    )
                                )
                            ),
                            List.of() // we have no inputs we need
                        )).type(
                            DialogType.multiAction(
                                createCommandActions(
                                    baseCommand,
                                    subCommand.hasExtraArgs(),
                                    CanvasCommands.permission(name).test(context.getSource()),
                                    permission
                                )
                            ).columns(2).build()
                        )
                    )
                ))
            );
        }).filter(Objects::nonNull).toList();
    }

    private static Component getGradientForCanvasTitled(final String textContent) {
        return Util.gradient(
            "CanvasMC - " + textContent,
            style -> style.decorate(TextDecoration.BOLD),
            TextColor.color(0xFF635BFF), TextColor.color(0xFFFF5CCF)
        );
    }

    private static ActionButton getLink(final String name, final String link, final String description) {
        return ActionButton.builder(Component.text(name, TextColor.color(0xFF82E5FF), TextDecoration.UNDERLINED))
            .width(80)
            .action(DialogAction.staticAction(ClickEvent.openUrl(link)))
            .tooltip(Component.text(description))
            .build();
    }

    @Contract("_ -> new")
    private static Component getTitleForCommand(final SubCommand subCommand) {
        return Component.text(Util.capitalize(Util.snakeToCamel(subCommand.getName())) + " Command");
    }

    @Unmodifiable
    @Contract("_,_,_,_ -> new")
    private static List<ActionButton> createCommandActions(
        final String baseCommand,
        final boolean hasExtraArgs,
        final boolean sourceHasPermission,
        final String permission
    ) {
        final ArrayList<ActionButton> buttons = new ArrayList<>(List.of(
            ActionButton.create(
                Component.text("Copy command"),
                Component.text("Copies the base command to the clipboard"),
                120,
                DialogAction.staticAction(ClickEvent.copyToClipboard(baseCommand))
            ),
            ActionButton.create(
                Component.text("Copy permission node"),
                Component.text("Copies the permission node to the clipboard"),
                120,
                DialogAction.staticAction(ClickEvent.copyToClipboard(permission))
            ),
            ActionButton.create(
                Component.text("Go back"),
                Component.text("Go back to the main menu"),
                120,
                DialogAction.staticAction(ClickEvent.callback(GO_BACK_ACTION))
            ),
            ActionButton.builder(Component.text("Exit menu")).width(120).build()
        ));

        if (!hasExtraArgs && sourceHasPermission) {
            buttons.add(
                ActionButton.create(
                    Component.text("Execute command"),
                    Component.text("Executes the sub command"),
                    120,
                    DialogAction.staticAction(ClickEvent.runCommand(baseCommand))
                )
            );
        }

        return buttons;
    }
}
