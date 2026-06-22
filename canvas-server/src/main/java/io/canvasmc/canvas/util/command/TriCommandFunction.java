package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface TriCommandFunction<A, B, C> {
    int act(final A a, B b, C c) throws CommandSyntaxException;
}
