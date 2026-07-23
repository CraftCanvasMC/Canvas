package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Similar to a {@link java.util.function.Consumer}, but throws a
 * {@link com.mojang.brigadier.exceptions.CommandSyntaxException} if execution fails
 *
 * @param <A>
 *     the input generic type
 *
 * @author dueris
 */
@FunctionalInterface
public interface CSEConsumer<A> {
    /**
     * Applies the consumer to the given input
     *
     * @param in
     *     the input generic type
     *
     * @throws com.mojang.brigadier.exceptions.CommandSyntaxException
     *     if execution fails
     */
    void act(final A in) throws CommandSyntaxException;
}
