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

            if (Files.exists(newPath)) {
                migratePearlsFile(newPath);
            } else {
                final CompoundTag oldData = NbtIo.readCompressed(OLD_PATH, NbtAccounter.unlimitedHeap());
                NbtIo.writeCompressed(migratePearlsTag(oldData), newPath);
                Files.delete(OLD_PATH);
            }
        } catch (final IOException ioe) {
            throw new RuntimeException("Couldn't conduct pearl migration", ioe);
        }
    }

    public static void migratePearlsFile(final Path path) throws IOException {
        final CompoundTag oldData = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        NbtIo.writeCompressed(migratePearlsTag(oldData), path);
    }

    public static CompoundTag migratePearlsTag(final CompoundTag oldData) {
        if (isModernPearlsTag(oldData)) {
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

    public static boolean isModernPearlsTag(final CompoundTag tag) {
        return tag.get("data") instanceof CompoundTag;
    }

    public static boolean needsMigration(final Path path) {
        if (!Files.exists(path)) {
            return false;
        }

        try {
            return !isModernPearlsTag(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        } catch (final IOException ioe) {
            throw new RuntimeException("Couldn't read pearl data for migration", ioe);
        }
    }

    @Override
    public boolean hasOldData(final WorldMigrationContext context) {
        return Files.exists(OLD_PATH) || needsMigration(resolveNewPath(context)); // pre-26.1 path or pre-release 26.2 data shape
    }

    @Override
    public boolean hasNewData(final WorldMigrationContext context) {
        final Path newPath = resolveNewPath(context);
        return Files.exists(newPath) && !needsMigration(newPath);
    }

    private static Path resolveNewPath(final WorldMigrationContext context) {
        return context.rootAccess().getLevelPath(LevelResource.DATA).resolve("canvas/pearls.dat").toAbsolutePath().normalize();
    }
}
