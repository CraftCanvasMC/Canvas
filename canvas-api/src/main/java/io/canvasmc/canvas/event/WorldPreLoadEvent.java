package io.canvasmc.canvas.event;

import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

public class WorldPreLoadEvent extends WorldEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Stage stage;

    public WorldPreLoadEvent(World world, Stage stage) {
        super(world);
        this.stage = stage;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public enum Stage {
        /**
         * Called after the world has been initialized, handled by the first region
         * ticking in that world
         */
        INIT_WORLD,
        /**
         * Called when the world object internally is finished constructing, the TAIL
         * of the init method
         */
        CONSTRUCTED
    }
}
