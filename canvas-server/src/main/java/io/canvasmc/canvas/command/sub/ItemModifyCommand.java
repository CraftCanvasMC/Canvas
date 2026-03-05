package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ItemModifyCommand implements Command {

    static {
        Config.LOGGER.debug("Registered {} data components", ComponentType.ids().count());
    }

    @Override
    public @NotNull String getName() {
        return "itemmodify";
    }

    @Override
    public @Nullable String getDescription() {
        return "Modifies an item in the player inventory with data components";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final @NonNull LiteralArgumentBuilder<CommandSourceStack> base) {
        return base
            .then(argument("players", EntityArgument.players())
                .then(argument("slot", SlotArgument.slot())
                    .then(literal("remove").then(argument("component", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ComponentType.ids(), builder))
                        .executes(context -> {
                            final Identifier identifier = context.getArgument("component", Identifier.class);
                            final ComponentType type = ComponentType.get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier));
                            final int slot = SlotArgument.getSlot(context, "slot");
                            Objects.requireNonNull(type, "Unregistered component type " + identifier);
                            for (final ServerPlayer player : context.getArgument("players", EntitySelector.class).findPlayers(context.getSource())) {
                                player.scheduleToOrRun(() -> {
                                    SlotAccess slotAccess = player.getSlot(slot);
                                    if (slotAccess == null) return;
                                    ItemStack stack = slotAccess.get();
                                    if (stack.isEmpty()) return;

                                    stack.remove(type.nms());
                                    slotAccess.set(stack);
                                    context.getSource().sendSuccess(() -> Component.literal("Successfully removed component \"" + type.identifier() + "\" on slot " + slot + " for player " + player.getPlainTextName()), true);
                                });
                            }
                            return 0;
                        })
                    ))
                    .then(literal("set")
                        .then(argument("component", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ComponentType.ids(), builder))
                            .then(argument("value", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    final Identifier identifier = context.getArgument("component", Identifier.class);
                                    ComponentType<?> type = ComponentType.get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier));
                                    return Objects.requireNonNull(type, "Unregistered component type " + identifier).suggestions(context, builder);
                                }).executes(context -> {
                                    final ComponentType type = ComponentType.get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(context.getArgument("component", Identifier.class)));
                                    final Object value = type.parse(context.getArgument("value", String.class));
                                    final int slot = SlotArgument.getSlot(context, "slot");
                                    for (final ServerPlayer player : context.getArgument("players", EntitySelector.class).findPlayers(context.getSource())) {
                                        player.scheduleToOrRun(() -> {
                                            SlotAccess slotAccess = player.getSlot(slot);
                                            if (slotAccess == null) return;
                                            ItemStack stack = slotAccess.get();
                                            if (stack.isEmpty()) return;

                                            type.apply(stack, value);
                                            slotAccess.set(stack);
                                            context.getSource().sendSuccess(() -> Component.literal("Successfully modified component \"" + type.identifier() + "\" on slot " + slot + " for player " + player.getPlainTextName()), true);
                                        });
                                    }
                                    return 0;
                                })))
                    )));
    }
}
