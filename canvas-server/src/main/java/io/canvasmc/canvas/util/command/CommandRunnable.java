package io.canvasmc.canvas.util.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface CommandRunnable {
    void act() throws CommandSyntaxException;
}
