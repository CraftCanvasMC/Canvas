package io.canvasmc.canvas.event.region;

import io.canvasmc.canvas.region.Region;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.ServerEvent;
import org.jetbrains.annotations.NotNull;

public class RegionCreateEvent extends ServerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Region region;

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public RegionCreateEvent(Region region) {
        this.region = region;
    }

    public Region getRegion() {
        return region;
    }
}
