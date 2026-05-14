package io.canvasmc.canvas.event.world;

import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

/**
 * Called when a world is being unloaded via
 * {@link org.bukkit.Server#unloadWorldAsync(org.bukkit.World, boolean, java.util.function.Consumer)}
 *
 * @apiNote This is always called on the <b>global region</b>
 */
public class WorldUnloadAsyncEvent extends WorldEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled = false;

    public WorldUnloadAsyncEvent(final World world) {
        super(world);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        cancelled = cancel;
    }
}
