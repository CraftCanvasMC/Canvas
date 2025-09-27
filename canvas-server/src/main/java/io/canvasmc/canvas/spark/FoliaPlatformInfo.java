package io.canvasmc.canvas.spark;

import me.lucko.spark.paper.common.platform.PlatformInfo;
import org.bukkit.Server;

public record FoliaPlatformInfo(Server server) implements PlatformInfo {

    @Override
    public Type getType() {
        return Type.SERVER;
    }

    @Override
    public String getName() {
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
