package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Similar to a {@link java.util.function.BiFunction}, but throws a
 * {@link com.mojang.brigadier.exceptions.CommandSyntaxException} if execution fails
 *
 * @param <A>
 *     the input generic type
 * @param <B>
 *     the output generic type
 *
 * @author dueris
 */
@FunctionalInterface
public interface CSEBiConsumer<A, B> {
    /**
     * Runs the consumer with the given input
     *
     * @param a
     *     the first generic type
     * @param b
     *     the second generic type
     *
     * @throws com.mojang.brigadier.exceptions.CommandSyntaxException
     *     if execution fails
     */
    void accept(final A a, final B b) throws CommandSyntaxException;
}
