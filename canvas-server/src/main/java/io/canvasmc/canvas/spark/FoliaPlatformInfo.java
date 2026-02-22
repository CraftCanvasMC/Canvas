package io.canvasmc.canvas.spark;

import me.lucko.spark.paper.common.platform.PlatformInfo;
import org.bukkit.Server;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public record FoliaPlatformInfo(Server server) implements PlatformInfo {

    @Override
    public Type getType() {
        return Type.SERVER;
    }

    @Contract(pure = true)
    @Override
    public @NonNull String getName() {
        return "Folia";
    }

    @Override
    public @NonNull String getBrand() {
        return this.server.getName();
    }

    @Override
    public @NonNull String getVersion() {
        return this.server.getVersion();
    }

    @Override
    public @NonNull String getMinecraftVersion() {
        return this.server.getMinecraftVersion();
    }
}
