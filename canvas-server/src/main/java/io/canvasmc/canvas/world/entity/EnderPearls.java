package io.canvasmc.canvas.world.entity;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.util.Codecs;
import io.canvasmc.canvas.util.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

@NullMarked
public final class EnderPearls extends SavedData {
    public static final Codec<Pearl> PEARL_CODEC = CompoundTag.CODEC.comapFlatMap(
        (Function<CompoundTag, DataResult<Pearl>>) compoundTag -> DataResult.success(new Pearl(compoundTag)), pearl -> pearl.serialized
    );
    public static final Codec<EnderPearls> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(
            Codecs.UUID_CODEC, Codecs.copyOnWriteArrayListCodec(PEARL_CODEC.listOf())
        ).optionalFieldOf("Data", new ConcurrentHashMap<>()).forGetter(EnderPearls::pearls)
    ).apply(instance, EnderPearls::new));

    public static final SavedDataType<EnderPearls> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("canvas", "pearls"),
        EnderPearls::new,
        CODEC,
        DataFixTypes.NONE
    );

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final Map<UUID, List<Pearl>> pearls;

    public EnderPearls() {
        this(new ConcurrentHashMap<>());
    }

    public EnderPearls(final Map<UUID, List<Pearl>> pearls) {
        this.pearls = new ConcurrentHashMap<>(pearls);
    }

    @Override
    public boolean isDirty() {
        // we must assume always dirty
        return true;
    }

    public void spawnPearls(final ServerPlayer player) {
        pearls.computeIfPresent(player.getUUID(), (_, enderPearls) -> {
            for (final Pearl enderPearl : new ArrayList<>(enderPearls)) {
                enderPearl.spawn();
                enderPearls.remove(enderPearl);
            }
            return null;
        });
    }

    public void addPearl(final UUID uuid, final ThrownEnderpearl thrownEnderpearl) {
        if (thrownEnderpearl.hasNullCallback()) {
            LOGGER.warn("Trying to add pearl with null callback, skipping", new Throwable());
            return;
        }
        else if (thrownEnderpearl.isRemoved()) {
            LOGGER.warn("Trying to add removed ({}) ender pearl, skipping", thrownEnderpearl.getRemovalReason(), new Throwable());
            return;
        }
        List<Pearl> pearls = pearls().computeIfAbsent(uuid, (ignored) -> new CopyOnWriteArrayList<>());
        Pearl encoded = Pearl.of(thrownEnderpearl);
        pearls.remove(encoded); // remove if it's already in the list, we don't want duplicates
        pearls.add(encoded);
        if (GlobalConfiguration.getInstance().logs.logEnderPearlRewriteActions) {
            LOGGER.info("Saved pearl at [{}] for {}", thrownEnderpearl.blockPosition().toShortString(), uuid);
        }
    }

    public Map<UUID, List<Pearl>> pearls() {
        return pearls;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj == this || obj instanceof EnderPearls other && this.pearls.equals(other.pearls);
    }

    @Override
    public int hashCode() {
        return pearls.hashCode();
    }

    @Override
    public String toString() {
        return "EnderPearls[" +
            "pearls=" + pearls + ']';
    }


    /**
     * The serializable ender pearl instance, containing a {@link net.minecraft.nbt.CompoundTag} which is the main save
     * data, formatted as:
     * <pre>{@code
     * {
     *     "uuid": <the entity uuid>,
     *     "data": <the entity data, without the id>,
     *     "level": <dimension>
     * }
     * }</pre>
     *
     * @param uuid
     *     the UUID of the ender pearl
     * @param serialized
     *     the serialized save data
     *
     * @author dueris
     */
    public record Pearl(UUID uuid, CompoundTag serialized) {

        public Pearl(final CompoundTag tag) {
            this(tag.read("uuid", Codecs.UUID_CODEC).orElseThrow(), tag);
        }

        @Contract("_ -> new")
        public static Pearl of(final ThrownEnderpearl pearl) {
            final CompoundTag tag;
            try (final ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(
                () -> "pearl-serialize", LOGGER
            )) {
                final TagValueOutput tagValueOutput = TagValueOutput.createWithContext(
                    problemReporter,
                    pearl.registryAccess()
                );

                tagValueOutput.store("uuid", Codecs.UUID_CODEC, pearl.getUUID());
                tagValueOutput.store("level", Level.RESOURCE_KEY_CODEC, pearl.level().dimension());
                pearl.save(tagValueOutput.child("data"));

                tag = tagValueOutput.buildResult();
            }
            return new Pearl(pearl.getUUID(), tag);
        }

        public void spawn() {
            final CompoundTag data = serialized.getCompound("data").orElseThrow();
            final ServerLevel level = MinecraftServer.getServer().getLevel(serialized.read("level", Level.RESOURCE_KEY_CODEC).orElseThrow());
            if (level == null) {
                LOGGER.error("Level ({}) did not exist, skipping pearl spawn", serialized.getString("level"));
                return;
            }

            final String levelName = Util.getLevelName(level);
            try (final ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(
                () -> "pearl-spawn", LOGGER
            )) {
                final ValueInput tagValueInput = TagValueInput.create(
                    problemReporter,
                    MinecraftServer.getServer().registryAccess(),
                    data
                );
                final Entity loadedEntity = EntityType.loadEntityRecursive(tagValueInput, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);

                if (loadedEntity != null) {
                    level.canvas$loadOrRunAtChunksAsync(loadedEntity.blockPosition(), 16, Priority.NORMAL, () -> {
                        if (level.tryAddFreshEntityWithPassengers(loadedEntity)) {
                            ServerPlayer.placeEnderPearlTicket(level, loadedEntity.chunkPosition());
                            if (GlobalConfiguration.getInstance().logs.logEnderPearlRewriteActions) {
                                LOGGER.info("Spawned saved pearl [{}] in level ({})", loadedEntity.blockPosition().toShortString(), levelName);
                            }
                        }
                        else {
                            LOGGER.warn("Unable to spawn saved pearl in level ({})", levelName);
                        }
                    });
                }
                else {
                    LOGGER.warn("Failed to spawn player ender pearl in level ({}), skipping", levelName);
                }
            } catch (final Throwable thrown) {
                LOGGER.error("Failed to spawn player pearl in level ({})", levelName, thrown);
            }
        }

        @Override
        public boolean equals(final Object o) {
            // if uuids match, same pearl
            return o == this || o instanceof Pearl(UUID otherUuid, _) &&
                uuid.equals(otherUuid);
        }

        @Override
        public int hashCode() {
            return uuid.hashCode();
        }
    }
}
