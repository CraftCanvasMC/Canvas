package io.canvasmc.canvas.event;

import io.canvasmc.canvas.ThreadedBukkitServer;
import io.canvasmc.canvas.scheduler.MultithreadedTickScheduler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.ServerEvent;
import org.jetbrains.annotations.NotNull;

public class TickSchedulerStartEvent extends ServerEvent {
    private static final HandlerList handlers = new HandlerList();

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public MultithreadedTickScheduler getScheduler() {
        return ThreadedBukkitServer.getInstance().getScheduler();
    }
}
