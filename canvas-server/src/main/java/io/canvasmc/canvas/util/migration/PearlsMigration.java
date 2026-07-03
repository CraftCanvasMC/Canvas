package io.canvasmc.canvas.util.migration;

import io.papermc.paper.world.migration.WorldMigrationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelResource;

public class PearlsMigration implements Migration {

    public static final Path OLD_PATH = Path.of("pearls.dat").toAbsolutePath().normalize();

    @Override
    public void conduct(final WorldMigrationContext context) {
        // old data just needs to be moved to the new data folder, honestly not that bad tbh
        try {
            final Path newPath = resolveNewPath(context);
            // create directories first or else we throw
            Files.createDirectories(newPath.getParent());

            // now move
            Files.move(
                OLD_PATH,
                newPath
            );
        } catch (final IOException ioe) {
            throw new RuntimeException("Couldn't conduct pearl migration", ioe);
        }
    }

    @Override
    public boolean hasOldData(final WorldMigrationContext context) {
        return Files.exists(OLD_PATH); // pre-26.1 path
    }

    @Override
    public boolean hasNewData(final WorldMigrationContext context) {
        return Files.exists(resolveNewPath(context));
    }

    private static Path resolveNewPath(final WorldMigrationContext context) {
        return context.rootAccess().getLevelPath(LevelResource.DATA).resolve("canvas/pearls.dat").toAbsolutePath().normalize();
    }
}
