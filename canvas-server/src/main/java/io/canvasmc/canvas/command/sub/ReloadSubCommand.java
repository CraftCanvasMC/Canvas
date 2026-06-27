package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.WorldConfig;
import io.canvasmc.canvas.command.SubCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ReloadSubCommand implements SubCommand {
    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public @Nullable String getDescription() {
        return "Reloads the CanvasMC configuration";
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
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base.executes(context -> {
            context.getSource().sendSystemMessage(
                Component.literal("Some configuration options cannot be changed at runtime or may work incorrectly after reloading.")
                    .withColor(CommonColors.RED)
            );
            context.getSource().sendSystemMessage(
                Component.literal("This command is unsupported. If you encounter issues, please run /stop")
                    .withColor(CommonColors.RED)
            );
            long start = System.nanoTime();
            GlobalConfiguration.reload();
            WorldConfig.reload();
            GlobalConfiguration.broadcast("Reloaded all Canvas solid and patch configurations in " + String.format("%.2f", ((System.nanoTime() - start) / 1e+6)) + "ms", GlobalConfiguration.INFO);
            return Command.SINGLE_SUCCESS;
        });
    }
}
