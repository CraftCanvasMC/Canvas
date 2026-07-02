package io.canvasmc.canvas.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.Nullable;

/**
 * Represents a subcommand that can be registered under the Canvas command system.
 * <p>
 * Implementations define their name, description, and Brigadier command structure via the
 * {@link #construct(LiteralArgumentBuilder, CommandBuildContext)} method.
 * </p>
 */
public interface SubCommand {

    /**
     * Gets a short, human-readable description of this command.
     * <p>
     * This may be used in help menus or documentation.
     * </p>
     *
     * @return the description of the command, or {@code null} if not provided
     */
    @Nullable
    String getDescription();

    /**
     * Constructs and returns the Brigadier command structure for this subcommand.
     * <p>
     * Implementations should attach arguments and execution logic to the provided {@code base} literal. The base
     * literal is already initialized with the command’s name and basic permission requirements.
     * </p>
     *
     * @param base
     *     the base literal builder to append arguments and execution logic to
     *
     * @return the fully constructed {@link LiteralArgumentBuilder} for registration
     */
    LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext);

    /**
     * Gets if the command can be registered on its own and not as a subcommand of `/canvas`
     *
     * @return if it can have a self-command
     */
    default boolean isAllowedSelfCommand() {
        return true;
    }

    /**
     * Gets if the command has extra arguments or not or if it's just executed from the literal base of this sub
     * command
     *
     * @return If the command can be executed from just the literal base
     */
    default boolean hasExtraArgs() {
        return true;
    }

    /**
     * Returns the permission predicate for if the source stack in context has the permission node
     * {@code canvas.command.{sub command name}.{node arg}}
     *
     * @param node
     *     the node arg
     *
     * @return the constructed predicate
     */
    default Predicate<CommandSourceStack> check(final String node) {
        return CanvasCommands.permission(getName() + "." + node);
    }

    /**
     * Gets the unique name of this subcommand.
     * <p>
     * This is used as the command literal, e.g. {@code /canvas <name>}.
     * </p>
     *
     * @return the subcommand name (never {@code null})
     */
    String getName();
}
