package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface AbstractCommandFunction<A, B> {
    B act(final A t) throws CommandSyntaxException;
}
