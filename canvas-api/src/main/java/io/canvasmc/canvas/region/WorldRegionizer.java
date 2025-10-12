package io.canvasmc.canvas.region;

import java.util.function.Consumer;
import org.bukkit.World;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents an interface for the Folia regionizer.
 */
public interface WorldRegionizer {

    /**
     * Returns the {@link World} associated with this regionizer.
     *
     * @return The world this regionizer manages.
     */
    @NonNull
    World getWorld();

    /**
     * Iterates over all active regions, calling the specified consumer for each one.
     *
     * <p><b>Note: This method is synchronized</b></p>
     *
     * @param forEach The consumer to apply to each {@link ChunkRegion}.
     */
    void computeForAllChunkRegions(Consumer<ChunkRegion> forEach);

    /**
     * Iterates over all chunk regions without synchronization.
     * <br><br>
     * This is primarily the method used internally to iterate over
     * regions unless we need to ensure that regions will not change
     * while we operate, like merge, split, add, or remove chunks
     *
     * @param forEach The consumer to apply to each {@link ChunkRegion}.
     */
    void computeForAllChunkRegionsUnsynchronized(Consumer<ChunkRegion> forEach);

    /**
     * Returns the bit shift used to map chunk coordinates to section (region) coordinates.
     *
     * <p>Typically, this corresponds to the number of bits by which a chunk X or Z
     * coordinate is shifted to find the corresponding region section coordinate.</p>
     *
     * @return The section coordinate shift.
     */
    int getSectionShift();

    /**
     * Returns the merge radius (in section units) used to determine adjacency for merging regions.
     *
     * @return The radius in section units for merging regions.
     */
    int getRegionSectionMergeRadius();

    /**
     * Returns the radius (in section units) around each region section in which new empty
     * sections are automatically created to maintain region continuity.
     *
     * @return The radius in section units for creating new empty sections.
     */
    int getEmptySectionCreateRadius();

    /**
     * Returns the maximum fraction (as a percentage between 0 and 1) of dead sections
     * allowed within a region before it is considered for recalculation or splitting.
     *
     * @return The maximum dead section percentage threshold.
     */
    double getMaxDeadRegionPercent();

    /**
     * Retrieves the {@link ChunkRegion} that owns the specified chunk position,
     * without acquiring synchronization locks.
     *
     * @param chunkX The chunk X coordinate.
     * @param chunkZ The chunk Z coordinate.
     * @return The region containing the specified chunk, or {@code null} if none exists.
     */
    @Nullable
    ChunkRegion getRegionAtUnsynchronised(final int chunkX, final int chunkZ);

    /**
     * Retrieves the {@link ChunkRegion} that owns the specified chunk position,
     * performing a synchronized read.
     *
     * @param chunkX The chunk X coordinate.
     * @param chunkZ The chunk Z coordinate.
     * @return The region containing the specified chunk, or {@code null} if none exists.
     */
    @Nullable
    ChunkRegion getRegionAtSynchronised(final int chunkX, final int chunkZ);

    /**
     * Represents a logical region within a {@link WorldRegionizer}.
     *
     * <p>A {@link ChunkRegion} groups one or more chunk sections and can represent
     * a dynamically merging and splitting area of the world. Regions may transition
     * through several lifecycle states defined by the {@link State}.</p>
     */
    interface ChunkRegion {

        /**
         * Returns whether this region currently contains no "alive" sections.
         *
         * <p>A section is considered alive if it contains or neighbors at least one
         * non-empty chunk. When all sections are dead, the region may be considered
         * for merging or removal.</p>
         *
         * @return {@code true} if all sections are dead, {@code false} otherwise.
         */
        boolean hasNoAliveSections();

        /**
         * Returns the fraction of sections within this region that are marked as dead.
         *
         * @return The ratio of dead sections to total sections, between 0.0 and 1.0.
         */
        double getDeadSectionPercent();

        /**
         * Returns an array of all packed chunk positions owned by this region.
         *
         * <p>Each entry in the returned array is a <em>packed</em> or <em>encoded</em>
         * chunk coordinate, represented as a single {@code long} value. This packed form
         * combines the chunk's {@code x} and {@code z} coordinates into one 64-bit long.
         *
         * <pre>
         *   long packed = ( (long) z & 0xFFFFFFFFL ) << 32 | ( (long) x & 0xFFFFFFFFL );
         * </pre>
         * <p>
         * This means:
         * <ul>
         *   <li>The lower 32 bits store the chunk’s {@code x} coordinate.</li>
         *   <li>The upper 32 bits store the chunk’s {@code z} coordinate.</li>
         * </ul>
         *
         * <p>To unpack a position:</p>
         *
         * <pre>
         *   int x = (int) (packed & 0xFFFFFFFFL);
         *   int z = (int) (packed >>> 32);
         * </pre>
         *
         * @return An array of packed chunk coordinates ({@code long}) owned by this region.
         */
        long[] getOwnedPackedChunkPositions();

        /**
         * Returns the current {@link State} of this region.
         *
         * @return The region's state.
         */
        State getState();

        /**
         * Returns an interface for the region tick data of this region
         * <br>
         * This contains regionized data, the tick handle, etc.
         *
         * @return the region data
         */
        RegionTickData getTickData();

        /**
         * Returns the {@link WorldRegionizer} that owns this region.
         *
         * @return The regionizer managing this region.
         */
        WorldRegionizer getRegionizer();

        /**
         * Represents the lifecycle state of a {@link ChunkRegion}.
         *
         * <ul>
         *     <li>{@link #TRANSIENT} – Region is being created or modified and is not yet stable.</li>
         *     <li>{@link #READY} – Region is active, consistent, and ready for ticking.</li>
         *     <li>{@link #TICKING} – Region is currently being ticked on a tick thread.</li>
         *     <li>{@link #DEAD} – Region is destroyed or scheduled for removal.</li>
         * </ul>
         */
        enum State {
            TRANSIENT,
            READY,
            TICKING,
            DEAD
        }
    }
}
