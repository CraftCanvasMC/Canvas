package io.canvasmc.canvas.world.entity;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.util.Codecs;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record EnderPearls(Map<UUID, List<Pearl>> pearls) {
    private static final AtomicLong LAST_AUTOSAVE = new AtomicLong(System.nanoTime());
    public static final String SAVE_NAME = "canvas/pearls.dat";
    public static final Codec<Pearl> PEARL_CODEC = CompoundTag.CODEC.comapFlatMap(
        (Function<CompoundTag, DataResult<Pearl>>) compoundTag -> DataResult.success(new Pearl(compoundTag)), pearl -> pearl.serialized
    );
    public static final Codec<EnderPearls> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.unboundedMap(
                Codecs.UUID_CODEC, Codecs.copyOnWriteArrayListCodec(PEARL_CODEC.listOf())
            ).optionalFieldOf("Data", new ConcurrentHashMap<>()).forGetter(EnderPearls::pearls)
        ).apply(instance, EnderPearls::new)
    );

    public EnderPearls(final Map<UUID, List<Pearl>> pearls) {
        this.pearls = new ConcurrentHashMap<>(pearls);
    }

    public static EnderPearls read(final Path worldSavePath) {
        final Path resolved = worldSavePath.resolve(SAVE_NAME);
        if (Config.INSTANCE.restoreVanillaEnderPearlBehavior && Files.exists(resolved)) {
            try {
                CompoundTag tag = Objects.requireNonNull(NbtIo.readCompressed(resolved, NbtAccounter.unlimitedHeap()), "NBT cannot be null")
                    .asCompound().orElseThrow(UnknownError::new);
                return CODEC.decode(NbtOps.INSTANCE, tag).getOrThrow().getFirst();
            } catch (Throwable e) {
                throw new RuntimeException("Couldn't read pearl save data", e);
            }
        }
        return new EnderPearls(new ConcurrentHashMap<>());
    }

    public CompletableFuture<Boolean> save(@Nullable BooleanConsumer callback) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Util.ioPool().execute(() -> {
            try {
                // create directories first or else we fail to save
                Path resolved = MinecraftServer.getServer().storageSource.getLevelPath(LevelResource.DATA).resolve(SAVE_NAME);
                Files.createDirectories(resolved.getParent());
                Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
                NbtIo.writeCompressed((CompoundTag) tag, resolved);
                future.complete(true);
            } catch (Throwable thrown) {
                future.completeExceptionally(thrown);
            }
        });
        return future.handle((result, thrown) -> {
            if (result == null || !result) {
                Config.LOGGER.warn("Could not save to pearls.dat", thrown);
            }
            if (callback != null) callback.accept(thrown == null);
            return result;
        });
    }

    public void spawnPearls(final ServerPlayer player) {
        pearls.computeIfPresent(player.getUUID(), (uuid, enderPearls) -> {
            for (final Pearl enderPearl : new ArrayList<>(enderPearls)) {
                enderPearl.spawn();
                enderPearls.remove(enderPearl);
            }
            return null;
        });
    }

    public void addPearl(final UUID uuid, final ThrownEnderpearl thrownEnderpearl) {
        if (thrownEnderpearl.isRemoved()) {
            Config.LOGGER.warn("Trying to add removed ({}) ender pearl, skipping", thrownEnderpearl.getRemovalReason(), new Throwable());
            return;
        }
        List<Pearl> pearls = pearls().computeIfAbsent(uuid, (ignored) -> new CopyOnWriteArrayList<>());
        Pearl encoded = Pearl.of(thrownEnderpearl);
        pearls.remove(encoded); // remove if it's already in the list, we don't want duplicates
        pearls.add(encoded);
    }

    public void autosave() {
        final int autoSavePeriod = MinecraftServer.getServer().autosavePeriod;
        if (autoSavePeriod <= 0) return;
        final long periodNanos = autoSavePeriod * TickRegionScheduler.getTimeBetweenTicks();
        final long currTime = System.nanoTime();
        final long last = LAST_AUTOSAVE.get();
        if ((currTime - last) > periodNanos && LAST_AUTOSAVE.compareAndSet(last, currTime)) {
            save(null);
        }
    }

    /**
     * The serializable ender pearl instance, containing a {@link net.minecraft.nbt.CompoundTag} which is the main save
     * data, formatted as:
     * <pre>{@code
     * {
     *     "uuid": <the entity uuid>,
     *     "data": <the entity data, without the id>,
     *     "world": <dimension>
     * }
     * }</pre>
     *
     * @param serialized
     *     the serialized save data
     *
     * @author dueris
     */
    public record Pearl(CompoundTag serialized) {

        @Contract("_ -> new")
        public static Pearl of(ThrownEnderpearl pearl) {
            final CompoundTag tag;
            try (final ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(
                () -> "pearl-serialize", Config.LOGGER
            )) {
                final TagValueOutput tagValueOutput = TagValueOutput.createWithContext(
                    problemReporter,
                    pearl.registryAccess()
                );

                tagValueOutput.store("uuid", Codecs.UUID_CODEC, pearl.getUUID());
                tagValueOutput.store("world", Level.RESOURCE_KEY_CODEC, pearl.level().dimension());
                pearl.save(tagValueOutput.child("data"));

                tag = tagValueOutput.buildResult();
            }
            return new Pearl(tag);
        }

        public void spawn() {
            final CompoundTag data = serialized.getCompound("data").orElseThrow();
            final ServerLevel world = MinecraftServer.getServer().getLevel(serialized.read("world", Level.RESOURCE_KEY_CODEC).orElseThrow());
            if (world == null) {
                Config.LOGGER.error("World ({}) did not exist, skipping pearl spawn", serialized.getString("world"));
                return;
            }
            Entity entity = EntityType.loadEntityRecursive(data, world, EntitySpawnReason.LOAD, EntityProcessor.NOP);
            if (entity != null) {
                world.canvas$loadOrRunAtChunksAsync(entity.blockPosition, 16, Priority.NORMAL, () -> {
                    world.addFreshEntityWithPassengers(entity);
                    ServerPlayer.placeEnderPearlTicket(world, entity.chunkPosition());
                    Config.LOGGER.debug("Spawned saved pearl in world ({})", world.dimension().identifier());
                });
            }
            else {
                Config.LOGGER.warn("Failed to spawn player ender pearl in world ({}), skipping", world.dimension().identifier().toDebugFileName());
            }
        }

        @Override
        public boolean equals(final Object o) {
            // if uuids match, same pearl
            return o instanceof Pearl(CompoundTag otherSerialized) &&
                otherSerialized.read("uuid", Codecs.UUID_CODEC).orElseThrow().equals(serialized.read("uuid", Codecs.UUID_CODEC).orElseThrow());
        }
    }
}
