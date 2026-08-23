package io.canvasmc.canvas.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.WorldConfig;
import io.canvasmc.canvas.commands.SubCommand;
import io.canvasmc.canvas.util.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

public class ReloadSubCommand implements SubCommand {

    @Override
    public String getDescription() {
        return "Reloads the CanvasMC configuration";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base.executes(context -> {
            final CommandSourceStack source = context.getSource();
            final long start = System.nanoTime();

            // warn the source, as technically this can cause issues and should
            // only be used for development purposes
            source.sendSystemMessage(
                Component.literal("Some configuration options cannot be changed at runtime or may work incorrectly after reloading.").withColor(CommonColors.RED)
            );
            source.sendSystemMessage(
                Component.literal("This command is unsupported. If you encounter issues, please run /stop").withColor(CommonColors.RED)
            );

            // reload global and world configs
            GlobalConfiguration.reload();
            WorldConfig.reload();

            GlobalConfiguration.broadcast(
                "Reloaded all Canvas solid and patch configurations in " + Util.formatNanosToLargestWholeUnit(System.nanoTime() - start),
                GlobalConfiguration.INFO
            );
            return Command.SINGLE_SUCCESS;
        });
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return false;
    }

    @Override
    public boolean hasExtraArgs() {
        return false;
    }

    @Override
    public String getName() {
        return "reload";
    }
}
