package io.canvasmc.canvas.server.level.distance.command;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class CommandUtils {
    public static @NotNull Component getMessage(String format, Object... args) {
        return Component.literal(String.format(format, args));
    }

    public static @NotNull String getRegistryId(ServerLevel dim) {
        try {
            return dim.dimensionTypeRegistration().unwrapKey().orElseThrow().location().toString();
        } catch (Exception e) {
            return "<couldn't get dimension id due to exception: " + e + ">";
        }
    }
}
