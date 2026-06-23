package io.canvasmc.canvas.region;

import org.bukkit.Chunk;

/**
 * The server tick rate manager, compatible with region threading
 */
public interface RegionThreadingTickManager {
    /**
     * Gets the tick rate for the server
     *
     * @return the current tick rate
     */
    float getTickRate();

    /**
     * Sets the tick rate for the server
     *
     * @param newRate
     *     the new rate
     */
    void setTickRate(float newRate);

    /**
     * Gets the region tick handle at the specified chunk. With that, you can post actions to modify the tick state
     *
     * @param chunk
     *     the target chunk that is used to locate the region
     *
     * @return the region tick handle
     *
     * @throws java.lang.NullPointerException
     *     if there is no region loaded at the provided chunk
     */
    RegionHandle getHandleAt(Chunk chunk);

    /**
     * Gets the global region tick handle With that, you can post actions to modify the tick state
     *
     * @return the global region tick handle
     */
    RegionHandle getGlobalRegionHandle();

    /**
     * Sends a packet to all players updating them of the current tick state
     */
    void sendUpdateToAllPlayers();

    /**
     * The region tick state
     */
    interface RegionHandle {
        /**
         * Posts the {@code PAUSE} action to the region. This action will be sent to the action queue and be processed
         * at the start of the next region tick.
         * <p>
         * This is equivalent to "freezing" the region, pausing the ticking of certain game elements
         */
        void pause();

        /**
         * Posts the {@code PLAY} action to the region. This action will be sent to the action queue and be processed at
         * the start of the next region tick
         * <p>
         * This is equivalent to "unfreezing" the region, allowing the ticking of game elements to run again
         */
        void play();

        /**
         * Posts the {@code WALK} action to the region. This action will be sent to the action queue and be processed at
         * the start of the next region tick
         * <p>
         * This is equivalent to telling the region to stop "sprinting", meaning it will run ticks at the normal tick
         * rate
         */
        void walk();

        /**
         * Posts the {@code SPRINT} action to the region. This action will be sent to the action queue and be processed
         * at the start of the next region tick
         * <p>
         * This is equivalent to telling the region to start "sprinting", meaning it will ignore the rate of which it
         * should be running ticks, and process the amount of ticks specified as much as possible
         *
         * @param ticks
         *     the amount of ticks to sprint
         */
        void sprint(int ticks);

        /**
         * Gets if the region should be running game elements currently.
         * <p>
         * This is equivalent to checking if the server is frozen or not
         *
         * @return if the region is running game elements
         *
         * @throws java.lang.IllegalStateException
         *     if the current tick runner does not own this region
         */
        boolean doesRunGameElements();

        /**
         * Gets if the region is currently sprinting.
         *
         * @return if the region is sprinting
         *
         * @throws java.lang.IllegalStateException
         *     if the current tick runner does not own this region
         */
        boolean isSprinting();
    }
}
