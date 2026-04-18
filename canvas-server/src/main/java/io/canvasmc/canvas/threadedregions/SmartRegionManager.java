package io.canvasmc.canvas.threadedregions;

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Predictive region management to optimize Folia's thread allocation.
 */
@NullMarked
public final class SmartRegionManager {

    private static final double MERGE_LOAD_THRESHOLD = 0.3; // 30% load
    private static final AtomicInteger ACTIVE_REGIONS = new AtomicInteger(0);

    /**
     * Evaluates if two adjacent regions should be merged based on predicted load.
     *
     * @param regionALoad predicted load for region A
     * @param regionBLoad predicted load for region B
     * @return true if regions should merge
     */
    public static boolean shouldMergeRegions(double regionALoad, double regionBLoad) {
        // Predictive logic: If combined load is significantly below single-thread capacity, merge
        return (regionALoad + regionBLoad) < MERGE_LOAD_THRESHOLD;
    }

    /**
     * Notifies the manager of a region split event.
     */
    public static void onRegionSplit() {
        ACTIVE_REGIONS.incrementAndGet();
    }

    /**
     * Notifies the manager of a region merge event.
     */
    public static void onRegionMerge() {
        ACTIVE_REGIONS.decrementAndGet();
    }

    public static int getActiveRegionCount() {
        return ACTIVE_REGIONS.get();
    }

    private SmartRegionManager() {}
}
