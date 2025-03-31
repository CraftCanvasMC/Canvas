package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

public final class CanvasCommands {

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        SimulationDistanceCommand.register(dispatcher);
        ViewDistanceCommand.register(dispatcher);
        ResendChunksCommand.register(dispatcher);
        SenderInfoCommand.register(dispatcher);
    }
}
