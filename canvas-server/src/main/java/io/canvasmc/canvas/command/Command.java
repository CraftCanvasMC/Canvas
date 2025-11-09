package io.canvasmc.canvas.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a subcommand that can be registered under the Canvas command system.
 * <p>
 * Implementations define their name, description, and Brigadier command structure
 * via the {@link #construct(LiteralArgumentBuilder)} method.
 * </p>
 */
public interface Command {

    /**
     * Gets the unique name of this subcommand.
     * <p>
     * This is used as the command literal, e.g. {@code /canvas <name>}.
     * </p>
     *
     * @return the subcommand name (never {@code null})
     */
    @NotNull
    String getName();

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
     * Implementations should attach arguments and execution logic to the provided
     * {@code base} literal. The base literal is already initialized with the
     * command’s name and basic permission requirements.
     * </p>
     *
     * @param base the base literal builder to append arguments and execution logic to
     * @return the fully constructed {@link LiteralArgumentBuilder} for registration
     */
    LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base);
}
