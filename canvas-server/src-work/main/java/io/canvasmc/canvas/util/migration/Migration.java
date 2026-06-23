package io.canvasmc.canvas.util.migration;

import io.papermc.paper.world.migration.WorldMigrationContext;

/**
 * Abstract interface for a migration for Canvas file data for update from before 26.1 to post 26.1 data structure
 */
public interface Migration {
    /**
     * Conducts the migration type
     *
     * @param context
     *     the paper migration context
     */
    void conduct(final WorldMigrationContext context);

    /**
     * Gets if the old data is present
     *
     * @param context
     *     the paper migration context
     *
     * @return if the old data exists
     */
    boolean hasOldData(final WorldMigrationContext context);

    /**
     * Gets if the new data is present
     *
     * @param context
     *     the paper migration context
     *
     * @return if the new data exists
     */
    boolean hasNewData(final WorldMigrationContext context);
}
