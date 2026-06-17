package io.canvasmc.canvas.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface CommandFunction<T> {
    int act(final T t) throws CommandSyntaxException;
}
