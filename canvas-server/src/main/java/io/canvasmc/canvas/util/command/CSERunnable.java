package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Similar to a {@link java.lang.Runnable}, but throws a {@link CommandSyntaxException} if execution fails
 *
 * @author dueris
 */
@FunctionalInterface
public interface CSERunnable {
    /**
     * Executes the runnable
     *
     * @throws CommandSyntaxException
     *     if execution fails
     */
    void act() throws CommandSyntaxException;
}
