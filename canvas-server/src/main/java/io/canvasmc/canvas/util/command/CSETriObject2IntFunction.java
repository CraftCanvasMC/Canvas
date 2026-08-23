package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Similar to a {@link org.apache.commons.lang3.function.TriFunction}, but always returns an {@code int}, and throws a
 * {@link CommandSyntaxException} if execution fails
 *
 * @param <A>
 *     the first generic input type passed
 * @param <B>
 *     the second generic input type passed
 * @param <C>
 *     the third generic input type passed
 *
 * @author dueris
 */
@FunctionalInterface
public interface CSETriObject2IntFunction<A, B, C> {
    /**
     * Executes the function, returning an {@code int} on completion
     *
     * @param first
     *     the first generic input
     * @param second
     *     the second generic input
     * @param third
     *     the third generic input
     *
     * @return an integer
     *
     * @throws CommandSyntaxException
     *     if execution fails
     */
    int act(final A first, final B second, final C third) throws CommandSyntaxException;
}
