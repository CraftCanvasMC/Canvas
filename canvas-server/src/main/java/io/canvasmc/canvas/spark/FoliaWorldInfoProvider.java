package io.canvasmc.canvas.spark;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.spark.profiler.SparkRegionProfilerExtension;
import io.canvasmc.canvas.tick.COWLongArrayList;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import me.lucko.spark.paper.common.platform.world.AbstractChunkInfo;
import me.lucko.spark.paper.common.platform.world.CountMap;
import me.lucko.spark.paper.common.platform.world.WorldInfoProvider;
import net.minecraft.server.level.ServerLevel;
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

public class FoliaWorldInfoProvider implements WorldInfoProvider {
    private final FoliaSparkPlugin plugin;
    private final Server server;

    public FoliaWorldInfoProvider(FoliaSparkPlugin plugin) {
        this.plugin = plugin;
        this.server = Bukkit.getServer();
    }

    @Override
    public CountsResult pollCounts() {
        // Note: if we are ending a profiler, this will be cached, otherwise this is current
        Pair<ServerLevel, COWLongArrayList> profilerResultCache = SparkRegionProfilerExtension.PROFILING_RESULTS_CACHE.get();
        if (profilerResultCache != null) {
            ServerLevel world = profilerResultCache.getFirst();
            COWLongArrayList chunkKeys = profilerResultCache.getSecond();
            // use first entry, this shouldn't ever be null or out of bounds
            final int chunkX = CoordinateUtils.getChunkX(chunkKeys.getArray()[0]);
            final int chunkZ = CoordinateUtils.getChunkZ(chunkKeys.getArray()[0]);
            CompletableFuture<CountsResult> result = new CompletableFuture<>();

            // schedule to region we were profiling at
            RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                world, chunkX, chunkZ, () -> {
                    // we are scheduled to the region here, fetch localized information
                    RegionizedWorldData localWorldData = world.getCurrentWorldData();

                    int players = localWorldData.getPlayerCount();
                    int entities = localWorldData.getEntityCount();
                    int chunks = localWorldData.getChunkCount();
                    int tileEntities = 0;

                    // we only use block ticking chunks, matches with what spark does for global polling
                    for (final LevelChunk tickingChunk : localWorldData.getTickingChunks()) {
                        tileEntities += tickingChunk.canvas$getAllBlockEntities().length;
                    }

                    result.complete(new CountsResult(players, entities, tileEntities, chunks));
                }, Priority.BLOCKING // highest possible priority, we need this information now please
            );

            try {
                // timeout 5 seconds like FoliaChunkInfo#getEntityCounts
                return result.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                throw new RuntimeException("Couldn't fetch localized world data", e);
            }
        }
        int players = this.server.getOnlinePlayers().size();
        int entities = 0;
        int tileEntities = 0;
        int chunks = 0;

        for (World world : this.server.getWorlds()) {
            entities += world.getEntityCount();
            tileEntities += world.getTileEntityCount();
            chunks += world.getChunkCount();
        }

        return new CountsResult(players, entities, tileEntities, chunks);
    }

    @Override
    public ChunksResult<FoliaChunkInfo> pollChunks() {
        ChunksResult<FoliaChunkInfo> data = new ChunksResult<>();

        // Note: if we are ending a profiler, this will be cached, otherwise this is current
        Pair<ServerLevel, COWLongArrayList> profilerResultCache = SparkRegionProfilerExtension.PROFILING_RESULTS_CACHE.get();
        if (profilerResultCache != null) {
            ServerLevel world = profilerResultCache.getFirst();
            COWLongArrayList chunkKeys = profilerResultCache.getSecond();
            // use first entry, this shouldn't ever be null or out of bounds
            final int chunkX = CoordinateUtils.getChunkX(chunkKeys.getArray()[0]);
            final int chunkZ = CoordinateUtils.getChunkZ(chunkKeys.getArray()[0]);
            CompletableFuture<List<FoliaChunkInfo>> result = new CompletableFuture<>();

            // schedule to region we were profiling at
            RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                world, chunkX, chunkZ, () -> {
                    List<FoliaChunkInfo> retVal = new ArrayList<>();
                    RegionizedWorldData worldData = world.getCurrentWorldData();
                    for (final LevelChunk nmsChunk : worldData.getChunks()) {
                        Chunk chunk = new CraftChunk(nmsChunk);
                        retVal.add(new FoliaChunkInfo(chunk, world.getWorld(), this.plugin));
                    }
                    result.complete(retVal);
                }, Priority.BLOCKING // highest possible priority, we need this information now please
            );

            try {
                // timeout 5 seconds like FoliaChunkInfo#getEntityCounts
                data.put(world.getWorld().getName(), result.get(5, TimeUnit.SECONDS));
                return data;
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                throw new RuntimeException("Couldn't fetch localized world data", e);
            }
        }
        for (World world : this.server.getWorlds()) {
            Chunk[] chunks = world.getLoadedChunks();

            List<FoliaChunkInfo> list = new ArrayList<>(chunks.length);
            for (Chunk chunk : chunks) {
                if (chunk != null) {
                    list.add(new FoliaChunkInfo(chunk, world, this.plugin));
                }
            }

            data.put(world.getName(), list);
        }

        return data;
    }

    @Override
    public GameRulesResult pollGameRules() {
        GameRulesResult data = new GameRulesResult();

        boolean addDefaults = true; // add defaults in the first iteration
        for (World world : this.server.getWorlds()) {
            for (String gameRule : world.getGameRules()) {
                GameRule<?> ruleObj = GameRule.getByName(gameRule);
                if (ruleObj == null) {
                    continue;
                }

                if (addDefaults) {
                    Object defaultValue = world.getGameRuleDefault(ruleObj);
                    data.putDefault(gameRule, Objects.toString(defaultValue));
                }

                Object value = world.getGameRuleValue(ruleObj);
                data.put(gameRule, world.getName(), Objects.toString(value));
            }

            addDefaults = false;
        }

        return data;
    }

    @SuppressWarnings({"removal", "UnstableApiUsage"})
    @Override
    public Collection<DataPackInfo> pollDataPacks() {
        return this.server.getDataPackManager().getDataPacks().stream()
            .map(pack -> new DataPackInfo(
                pack.getTitle(),
                pack.getDescription(),
                pack.getSource().name().toLowerCase(Locale.ROOT).replace("_", "")
            ))
            .collect(Collectors.toList());
    }

    static final class FoliaChunkInfo extends AbstractChunkInfo<EntityType> {
        private final CompletableFuture<CountMap<EntityType>> entityCounts;

        FoliaChunkInfo(Chunk chunk, World world, FoliaSparkPlugin plugin) {
            super(chunk.getX(), chunk.getZ());

            Executor executor = task -> RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(((CraftWorld) world).getHandle(), getX(), getZ(), task, Priority.BLOCKING);
            this.entityCounts = CompletableFuture.supplyAsync(() -> calculate(chunk), executor);
        }

        private CountMap<EntityType> calculate(Chunk chunk) {
            CountMap<EntityType> entityCounts = new CountMap.EnumKeyed<>(EntityType.class);
            for (Entity entity : chunk.getEntities()) {
                if (entity != null) {
                    entityCounts.increment(entity.getType());
                }
            }
            return entityCounts;
        }

        @Override
        public CountMap<EntityType> getEntityCounts() {
            try {
                return this.entityCounts.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Exception reading statistics for chunk " + getX() + ", " + getZ(), e);
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out waiting for statistics for chunk " + getX() + ", " + getZ(), e);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public String entityTypeName(EntityType type) {
            return type.getName();
        }

    }

}
