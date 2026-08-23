package io.canvasmc.canvas.spark.provider;

import ca.spottedleaf.concurrentutil.util.Priority;
import io.canvasmc.canvas.spark.plugin.AbstractFoliaChunkInfo;
import io.canvasmc.canvas.spark.plugin.ChunkRegionCenter;
import io.canvasmc.canvas.threadedregions.profiler.RegionProfiler;
import io.canvasmc.canvas.threadedregions.profiler.RegionScheduleHandlePinner;
import io.canvasmc.canvas.util.Util;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import me.lucko.spark.paper.common.platform.world.CountMap;
import me.lucko.spark.paper.common.platform.world.WorldInfoProvider;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public class FoliaWorldInfoProvider implements WorldInfoProvider {
    private final Server server;

    public FoliaWorldInfoProvider() {
        this.server = Bukkit.getServer();
    }

    @Override
    public CountsResult pollCounts() {
        final RegionProfiler.ProfilingState profilingState = RegionProfiler.STATE.get();
        if (profilingState != null) {
            return getCountsFromState(profilingState);
        }

        // not profiling, gather global data
        final int players = this.server.getOnlinePlayers().size();
        int entities = 0;
        int chunks = 0;

        // TODO - implement?
        // we don't provide tile entity data because it's not thread-safe
        int tileEntities = 0;

        for (final World world : this.server.getWorlds()) {
            entities += world.getEntityCount();
            chunks += world.getChunkCount();
        }

        return new CountsResult(players, entities, tileEntities, chunks);
    }

    @Override
    public ChunksResult<FoliaChunkInfo> pollChunks() {
        final ChunksResult<FoliaChunkInfo> data = new ChunksResult<>();

        final RegionProfiler.ProfilingState profilingState = RegionProfiler.STATE.get();
        if (profilingState != null) {
            return getChunksFromState(profilingState, data);
        }

        for (final World world : this.server.getWorlds()) {
            final ServerLevel level = ((CraftWorld) world).getHandle();
            final Map<Long, List<FoliaChunkInfo>> byRegion = new HashMap<>();
            final Map<Long, ChunkRegionCenter> centerByRegion = new HashMap<>();
            final String worldKey = Util.getWorldName(world);
            final String worldCapitalized = Util.capitalize(worldKey);

            for (final Chunk chunk : world.getLoadedChunks()) {
                final ChunkRegionCenter center = FoliaChunkInfo.resolveRegionCenter(level, chunk.getX(), chunk.getZ(), centerByRegion);
                final FoliaChunkInfo info = new FoliaChunkInfo(chunk, world, center);
                byRegion.computeIfAbsent(center.id(), _ -> new ArrayList<>()).add(info);
            }

            for (final Map.Entry<Long, List<FoliaChunkInfo>> entry : byRegion.entrySet()) {
                final ChunkRegionCenter center = centerByRegion.getOrDefault(entry.getKey(), ChunkRegionCenter.UNKNOWN);
                data.put(buildRegionKey(worldCapitalized, center), entry.getValue());
            }
        }

        return data;
    }

    @Override
    public GameRulesResult pollGameRules() {
        final GameRulesResult data = new GameRulesResult();
        boolean addDefaults = true; // add defaults in the first iteration

        for (final World world : this.server.getWorlds()) {
            for (final String gameRule : world.getGameRules()) {
                final GameRule<?> ruleObj = GameRule.getByName(gameRule);
                if (ruleObj == null) {
                    continue;
                }

                if (addDefaults) {
                    //noinspection deprecation
                    data.putDefault(gameRule, Objects.toString(world.getGameRuleDefault(ruleObj)));
                }

                data.put(gameRule, world.getName(), Objects.toString(world.getGameRuleValue(ruleObj)));
            }

            addDefaults = false;
        }

        return data;
    }

    @Override
    public Collection<DataPackInfo> pollDataPacks() {
        return this.server.getDatapackManager().getEnabledPacks().stream()
            .map(pack -> new DataPackInfo(
                pack.getName(),
                Component.text().append(pack.getDescription()).content(),
                pack.getSource().toString().toLowerCase(Locale.ROOT).replace("_", "")
            )).toList();
    }

    private ChunksResult<FoliaChunkInfo> getChunksFromState(final RegionProfiler.ProfilingState profilingState, final ChunksResult<FoliaChunkInfo> data) {
        if (profilingState.handlePinner() instanceof RegionScheduleHandlePinner.RegionPinner pinner) {
            final ServerLevel level = pinner.level();
            final ChunkPos target = pinner.getCenter();
            final CompletableFuture<List<FoliaChunkInfo>> result = new CompletableFuture<>();

            RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(
                // the target chunk should always be loaded due to the profiler tickets
                level, target.x(), target.z(), () -> {
                    final List<FoliaChunkInfo> chunks = new ArrayList<>();
                    final RegionizedWorldData worldData = level.getCurrentWorldData();

                    for (final LevelChunk chunk : worldData.getChunks()) {
                        chunks.add(new FoliaChunkInfo(new CraftChunk(chunk), level.getWorld()));
                    }

                    result.complete(chunks);
                }, Priority.BLOCKING
            );

            try {
                // timeout 5 seconds like FoliaChunkInfo#getEntityCounts
                data.put(level.getWorld().getName(), result.get(5, TimeUnit.SECONDS));
                return data;
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Couldn't fetch localized world data", ie);
            } catch (final Throwable thrown) {
                throw new RuntimeException("Couldn't fetch localized world data", thrown);
            }
        }
        // the global tick doesn't own any chunks, so we can return an empty result
        else return new ChunksResult<>();
    }

    private static CountsResult getCountsFromState(final RegionProfiler.ProfilingState profilingState) {
        // we need tile entities, chunks, entities, and players
        // if this isn't a region pinner, this is the global tick
        if (profilingState.handlePinner() instanceof RegionScheduleHandlePinner.RegionPinner pinner) {
            final ServerLevel level = pinner.level();
            final ChunkPos target = pinner.getCenter();

            final CompletableFuture<CountsResult> result = new CompletableFuture<>();

            // schedule to region we were profiling at
            RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(
                // the target chunk should always be loaded due to the profiler tickets
                level, target.x(), target.z(), () -> {
                    final RegionizedWorldData localWorldData = level.getCurrentWorldData();

                    final int players = localWorldData.getPlayerCount();
                    final int entities = localWorldData.getEntityCount();
                    final int chunks = localWorldData.getChunkCount();

                    // we can actually get all tile entities with this
                    int tileEntities = 0;

                    // we only use block ticking chunks, matches with what spark does for global polling
                    for (final LevelChunk tickingChunk : localWorldData.getTickingChunks()) {
                        tileEntities += tickingChunk.getBlockEntitiesCount();
                    }

                    result.complete(new CountsResult(players, entities, tileEntities, chunks));
                }, Priority.BLOCKING // highest possible priority, we need this information now please
            );

            try {
                // timeout 5 seconds like FoliaChunkInfo#getEntityCounts
                return result.get(5, TimeUnit.SECONDS);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Couldn't fetch localized world data", ie);
            } catch (final Throwable thrown) {
                throw new RuntimeException("Couldn't fetch localized world data", thrown);
            }
        }
        else {
            // the global tick owns no world data. TECHNICALLY it owns players
            // during specific states, like for example:
            // - end credits
            // - login phase
            // - config phase
            return new CountsResult(0, 0, 0, 0);
        }
    }

    private static String buildRegionKey(final String worldCapitalized, final ChunkRegionCenter center) {
        if (center.id() == -1L) {
            return worldCapitalized + " [region=unknown]";
        }

        return worldCapitalized + " region at " + center.centerBlockX() + ", " + center.centerBlockZ();
    }

    public static final class FoliaChunkInfo extends AbstractFoliaChunkInfo<EntityType> {
        private final CompletableFuture<CountMap<EntityType>> entityCounts;

        FoliaChunkInfo(final Chunk chunk, final World world) {
            this(chunk, world, resolveRegionCenter(
                ((CraftWorld) world).getHandle(),
                chunk.getX(),
                chunk.getZ(),
                new HashMap<>()
            ));
        }

        FoliaChunkInfo(final Chunk chunk, final World world, final ChunkRegionCenter center) {
            super(chunk.getX(), chunk.getZ(), center);
            this.entityCounts = CompletableFuture.supplyAsync(
                () -> calculate(chunk),
                task -> RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(
                    ((CraftWorld) world).getHandle(),
                    getX(),
                    getZ(),
                    task,
                    Priority.BLOCKING
                )
            );
        }

        private CountMap<EntityType> calculate(final Chunk chunk) {
            final CountMap<EntityType> entityCounts = new CountMap.EnumKeyed<>(EntityType.class);
            for (final Entity entity : chunk.getEntities()) {
                entityCounts.increment(entity.getType());
            }
            return entityCounts;
        }

        @Override
        public CountMap<EntityType> getEntityCounts() {
            try {
                return this.entityCounts.get(5, TimeUnit.SECONDS);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted when trying to get entity counts", ie);
            } catch (final ExecutionException ee) {
                throw new RuntimeException("Exception reading statistics for chunk " + getX() + ", " + getZ(), ee);
            } catch (final TimeoutException te) {
                throw new RuntimeException("Timed out waiting for statistics for chunk " + getX() + ", " + getZ(), te);
            }
        }

        @Nullable
        @Contract(pure = true)
        @SuppressWarnings("deprecation")
        @Override
        public String entityTypeName(final EntityType type) {
            return type.getName();
        }

        private static ChunkRegionCenter resolveRegionCenter(
            final ServerLevel level, final int chunkX, final int chunkZ, final Map<Long, ChunkRegionCenter> cache
        ) {
            final var region = level.regioniser.getRegionAtUnsynchronised(chunkX, chunkZ);
            if (region == null) {
                return ChunkRegionCenter.UNKNOWN;
            }

            final long id = region.id;
            final ChunkRegionCenter cached = cache.get(id);
            if (cached != null) {
                return cached;
            }

            final ChunkPos center = region.getCenterChunk();
            if (center == null) {
                return ChunkRegionCenter.UNKNOWN;
            }

            final ChunkRegionCenter regionCenter = new ChunkRegionCenter(id, center.getMiddleBlockX(), center.getMiddleBlockZ());
            cache.put(id, regionCenter);

            return regionCenter;
        }

    }

}
