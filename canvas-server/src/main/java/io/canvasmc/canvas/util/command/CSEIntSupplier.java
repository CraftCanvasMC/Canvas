package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Similar to an {@link java.util.function.IntSupplier}, but throws a {@link CommandSyntaxException} if execution fails
 *
 * @author dueris
 */
@FunctionalInterface
public interface CSEIntSupplier {
    /**
     * Runs the function, supplying an {@code int} return value
     *
     * @return an integer
     *
     * @throws CommandSyntaxException
     *     if execution fails
     */
    int act() throws CommandSyntaxException;
}
