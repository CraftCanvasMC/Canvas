package io.canvasmc.canvas.util.migration;

import com.mojang.logging.LogUtils;
import io.papermc.paper.world.migration.WorldMigrationContext;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;

public class CanvasFileMigration {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static boolean MSG_SHOWN = false;

    private static void tryPause() {
        try {
            LOGGER.warn("If you do not have a backup of these files, interrupt the server now.");
            LOGGER.warn("Use Ctrl+C, your panel kill function, etc. Pausing for 8 seconds to wait");
            if (!Boolean.getBoolean("paper.disableMigrationDelay")) {
                Thread.sleep(8_000L);
            }
            LOGGER.info("Continuing with Canvas file migration, please wait");
        } catch (InterruptedException thrown) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting before startup Canvas file migration", thrown);
        } finally {
            MSG_SHOWN = true;
        }
    }

    // note: this runs 1 time per world, so we can have per-world stuff migrated
    public static void initMigration(
        final WorldMigrationContext migrationContext
    ) {
        Set<Types> todo = new HashSet<>();
        // we iterate over all migration types and check which need to be done
        for (final Types migrationType : Types.values()) {
            if (migrationType.migration.hasOldData(migrationContext) && !migrationType.migration.hasNewData(migrationContext)) {
                // has old data, not new data, this should be migrated
                todo.add(migrationType);
            }
        }

        if (!todo.isEmpty()) {
            LOGGER.info("Beginning migration of Canvas filesave features");
            LOGGER.info("{} migration types awaiting conduction: {}", todo.size(), todo.toArray());

            if (!MSG_SHOWN) {
                tryPause();
            }

            for (final Types type : todo) {
                LOGGER.info("Conducting {} migration", type.name());
                type.migration.conduct(migrationContext);
            }

            LOGGER.info("All Canvas features migrated successfully, continuing with startup");
        }
    }

    private enum Types {
        PEARLS(new PearlsMigration());

        private final Migration migration;

        Types(Migration migration) {
            this.migration = migration;
        }
    }
}
