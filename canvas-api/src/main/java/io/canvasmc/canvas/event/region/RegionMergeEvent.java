package io.canvasmc.canvas.event.region;

import io.canvasmc.canvas.region.Region;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.ServerEvent;
import org.jetbrains.annotations.NotNull;

public class RegionMergeEvent extends ServerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Region from;
    private final Region into;

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public RegionMergeEvent(Region from, Region into) {
        this.from = from;
        this.into = into;
    }

    public Region getFrom() {
        return from;
    }

    public Region getInto() {
        return into;
    }
}
