package io.canvasmc.canvas.spark;

import me.lucko.spark.paper.common.platform.PlatformInfo;
import org.bukkit.Server;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record FoliaPlatformInfo(Server server) implements PlatformInfo {

    @Override
    public Type getType() {
        return Type.SERVER;
    }

    @Contract(pure = true)
    @Override
    public String getName() {
        // TODO - should we change this to "Canvas"? We have an entirely custom implementation, may be worth it
        return "Folia";
    }

    @Override
    public String getBrand() {
        return this.server.getName();
    }

    @Override
    public String getVersion() {
        return this.server.getVersion();
    }

    @Override
    public String getMinecraftVersion() {
        return this.server.getMinecraftVersion();
    }
}
