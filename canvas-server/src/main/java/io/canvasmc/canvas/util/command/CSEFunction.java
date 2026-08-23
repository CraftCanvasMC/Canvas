package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Similar to a {@link java.util.function.Function}, but throws a {@link CommandSyntaxException} if execution fails
 *
 * @param <A>
 *     the input generic type
 * @param <B>
 *     the output generic type
 *
 * @author dueris
 */
@FunctionalInterface
public interface CSEFunction<A, B> {
    /**
     * Applies the function to the given input
     *
     * @param in
     *     the input generic type
     *
     * @return the output generic type
     *
     * @throws CommandSyntaxException
     *     if execution fails
     */
    B act(final A in) throws CommandSyntaxException;
}
