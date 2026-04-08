package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.configuration.jankson.Jankson;
import io.canvasmc.canvas.configuration.jankson.JsonObject;
import io.canvasmc.canvas.configuration.writer.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ReloadCommand implements Command {
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
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base) {
        return base.executes(context -> {
            context.getSource().sendSystemMessage(
                Component.literal("Some configuration options cannot be changed at runtime or may work incorrectly after reloading.")
                    .withColor(CommonColors.RED)
            );
            context.getSource().sendSystemMessage(
                Component.literal("This command is unsupported. If you encounter issues, please run /stop")
                    .withColor(CommonColors.RED)
            );
            final Config old = Config.INSTANCE;
            Config.reload();
            final Config newConfig = Config.INSTANCE;
            Jankson jankson = Jankson.builder().build();
            JsonObject oldObj = (JsonObject) jankson.toJson(old);
            JsonObject newObj = (JsonObject) jankson.toJson(newConfig);
            Util.Diff diff = Util.diffWithValues(oldObj, newObj);
            Config.GLOBAL_BROADCAST.accept("Applied " + diff.changed().size() + " changes");
            return 1;
        });
    }
}
