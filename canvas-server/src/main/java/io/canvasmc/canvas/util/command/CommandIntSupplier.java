package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface CommandIntSupplier {
    int act() throws CommandSyntaxException;
}
