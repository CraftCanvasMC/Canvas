package io.canvasmc.canvas;

/**
 * The result of the world unload async call
 */
public enum WorldUnloadResult {
    /**
     * World unload was successful
     */
    SUCCESS,
    /**
     * World unload failed because players are currently joining the world
     */
    FAIL_PLAYERS_JOINING,
    /**
     * World unload failed because players are currently present in the world
     */
    FAIL_PLAYERS_PRESENT,
    /**
     * World unload failed because the world is already unloading
     */
    FAIL_ALREADY_UNLOADING,
    /**
     * World unload failed because the overworld is attempting to be unloaded, which is not allowed
     */
    FAIL_IS_OVERWORLD,
    /**
     * World unload failed because the {@link org.bukkit.event.world.WorldUnloadEvent} canceled the event
     */
    FAIL_UNLOAD_EVENT,
    /**
     * World unload failed because we are in shutdown
     */
    FAIL_IS_SHUTDOWN,
    /**
     * World unload failed for some unknown reason
     */
    FAIL_UNKNOWN;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isFailure() {
        return this != SUCCESS;
    }
}
