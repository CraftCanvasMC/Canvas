package io.canvasmc.canvas.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.commands.SubCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

public class RegionDataCommand implements SubCommand {

    @Override
    public String getDescription() {
        return "Allows accessing and profiling region data";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base; // TODO - entity list(with filters), player list, region profile, tile entities, mob caps, scheduling info, tick scheduling
    }

    @Override
    public String getName() {
        return "regiondata";
    }
}
