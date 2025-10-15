package io.canvasmc.canvas.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.bukkit.World;
import org.jetbrains.annotations.ApiStatus;

/**
 * An interface for the region tick data of a {@link io.canvasmc.canvas.region.WorldRegionizer.ChunkRegion}
 * <br>
 * This contains regionized data, the tick handle, etc.
 */
public interface RegionTickData {
    /**
     * Returns the {@link WorldRegionizer.ChunkRegion} associated with this tick data.
     *
     * <p>This identifies the region in the underlying regionizer that this tick
     * data corresponds to.</p>
     *
     * @return The region backing this tick data.
     */
    WorldRegionizer.ChunkRegion getRegion();

    /**
     * Returns the {@link org.bukkit.World} that owns this region.
     *
     * <p>This is equivalent to {@code getRegion().getWorld()}, but provided for
     * convenience when only the world context is needed.</p>
     *
     * @return The Bukkit world this region belongs to.
     */
    World getWorld();

    /**
     * Retrieves or creates the value associated with the given regionized data definition.
     *
     * <p>If no value has yet been created for this region, the provided
     * {@link IRegionizedData} instance will be used to construct it via
     * {@link IRegionizedData#createValue(RegionTickData)}. Once created, the
     * value will remain attached to this region until killed or removed from splits or merges</p>
     *
     * <p>This operation can be called on any thread, but should always be called
     * on the owning thread to prevent concurrency issues and crashes</p>
     *
     * @param regionizedData The regionized data definition to retrieve or create.
     * @param <T>            The type of the data value stored.
     * @return the fetched or created regionized data
     */
    <T> T getOrCreateFromIRegionizedData(IRegionizedData<T> regionizedData);

    /**
     * Represents a type of region-local data that can be attached to a region
     * through the {@link RegionTickData} system.
     *
     * <p>Each {@code IRegionizedData} instance defines how values of type {@code T}
     * are created, as well as how they should be handled when regions merge or split.
     * The associated data instance can be retrieved through
     * {@link RegionTickData#getOrCreateFromIRegionizedData(IRegionizedData)}.</p>
     *
     * @param <T> The type of object that this regionized data stores.
     * @see IRegionizedCallback
     */
    @ApiStatus.NonExtendable
    interface IRegionizedData<T> {
        /**
         * Creates a new instance of this data type, called to initially create
         * the instance that is added to the region tick data
         *
         * @param tickData The region tick data requesting a new instance.
         * @return A new instance of the data for the specified region.
         */
        T createValue(RegionTickData tickData);

        /**
         * Returns the merge/split callback
         * <p>The callback must not block or perform long-running operations, as it
         * is executed under critical locks, so operations must be completed in a quick
         * and timely manner</p>
         *
         * @return The callback handling merge and split behavior for this data type.
         */
        IRegionizedCallback<T> getCallback();

        interface IRegionizedCallback<T> {

            /**
             * Completely merges the data in {@code from} to {@code into}.
             * <p>
             * <b>Calculating Tick Offsets:</b>
             * Sometimes data stores absolute tick deadlines, and since regions tick independently, absolute deadlines
             * are not comparable across regions. Consider absolute deadlines {@code deadlineFrom, deadlineTo} in
             * regions {@code from} and {@code into} respectively. We can calculate the relative deadline for the from
             * region with {@code relFrom = deadlineFrom - currentTickFrom}. Then, we can use the same equation for
             * computing the absolute deadline in region {@code into} that has the same relative deadline as {@code from}
             * as {@code deadlineTo = relFrom + currentTickTo}. By substituting {@code relFrom} as {@code deadlineFrom - currentTickFrom},
             * we finally have that {@code deadlineTo = deadlineFrom + (currentTickTo - currentTickFrom)} and
             * that we can use an offset {@code fromTickOffset = currentTickTo - currentTickFrom} to calculate
             * {@code deadlineTo} as {@code deadlineTo = deadlineFrom + fromTickOffset}.
             * </p>
             * <p>
             * <b>Critical Notes:</b>
             * <li>
             *         <ul>
             *             This function is called while the region lock is held, so any blocking operations may
             *             deadlock the entire server and as such the function should be completely non-blocking and must complete
             *             in a timely manner.
             *         </ul>
             *         <ul>
             *             This function may not throw any exceptions, or the server will be left in an unrecoverable state.
             *         </ul>
             *     </li>
             * </p>
             *
             * @param from           The data to merge from.
             * @param into           The data to merge into.
             * @param fromTickOffset The addend to absolute tick deadlines stored in the {@code from} region to adjust to the into region.
             */
            void merge(final T from, final T into, final long fromTickOffset);

            /**
             * Splits the data in {@code from} into {@code dataSet}.
             * <p>
             * The chunk coordinate to region section coordinate bit shift amount is provided in {@code chunkToRegionShift}.
             * To convert from chunk coordinates to region coordinates and keys, see the code below:
             * <pre>
             *         {@code
             *         int chunkX = ...;
             *         int chunkZ = ...;
             *
             *         int regionSectionX = chunkX >> chunkToRegionShift;
             *         int regionSectionZ = chunkZ >> chunkToRegionShift;
             *         long regionSectionKey = ((long)regionSectionZ << 32) | (regionSectionX & 0xFFFFFFFFL);
             *         }
             *     </pre>
             * </p>
             * <p>
             * The {@code regionToData} hashtable provides a lookup from {@code regionSectionKey} (see above) to the
             * data that is owned by the region which occupies the region section.
             * </p>
             * <p>
             * Unlike {@link #merge(Object, Object, long)}, there is no absolute tick offset provided. This is because
             * the new regions formed from the split will start at the same tick number, and so no adjustment is required.
             * </p>
             *
             * @param from               The data to split from.
             * @param chunkToRegionShift The signed right-shift value used to convert chunk coordinates into region section coordinates.
             * @param regionToData       Lookup hash table from region section key to .
             * @param dataSet            The data set to split into.
             */
            void split(
                final T from, final int chunkToRegionShift,
                final Long2ReferenceOpenHashMap<T> regionToData, final ReferenceOpenHashSet<T> dataSet
            );
        }
    }
}
