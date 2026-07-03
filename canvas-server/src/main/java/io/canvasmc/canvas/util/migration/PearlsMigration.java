package io.canvasmc.canvas.util.migration;

import io.papermc.paper.world.migration.WorldMigrationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.LevelResource;

public class PearlsMigration implements Migration {

    public static final Path OLD_PATH = Path.of("pearls.dat").toAbsolutePath().normalize();

    @Override
    public void conduct(final WorldMigrationContext context) {
        try {
            final Path newPath = resolveNewPath(context);
            // create directories first or else we throw
            Files.createDirectories(newPath.getParent());

            final CompoundTag oldData = NbtIo.readCompressed(OLD_PATH, NbtAccounter.unlimitedHeap());
            NbtIo.writeCompressed(migratePearlsTag(oldData), newPath);
            Files.delete(OLD_PATH);
        } catch (final IOException ioe) {
            throw new RuntimeException("Couldn't conduct pearl migration", ioe);
        }
    }

    public static CompoundTag migratePearlsTag(final CompoundTag oldData) {
        if (oldData.contains("data")) {
            return oldData;
        }

        final CompoundTag wrappedData = new CompoundTag();
        for (final String key : new ArrayList<>(oldData.keySet())) {
            if ("DataVersion".equals(key)) {
                continue;
            }

            final Tag value = oldData.remove(key);
            if (value != null) {
                wrappedData.put(key, value);
            }
        }

        oldData.put("data", wrappedData);
        return oldData;
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
