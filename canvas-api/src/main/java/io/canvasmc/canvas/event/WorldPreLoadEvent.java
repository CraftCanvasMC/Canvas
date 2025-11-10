package io.canvasmc.canvas.event;

import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;
import org.jetbrains.annotations.NotNull;

public class WorldPreLoadEvent extends WorldEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final @NotNull Stage stage;

    public WorldPreLoadEvent(@NotNull World world, @NotNull Stage stage) {
        super(world);
        this.stage = stage;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    public Stage getStage() {
        return stage;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
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
