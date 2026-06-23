package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface CommandFunction<T> {
    int act(final T t) throws CommandSyntaxException;
}
