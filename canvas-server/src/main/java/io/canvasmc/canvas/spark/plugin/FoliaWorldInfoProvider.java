package io.canvasmc.canvas.spark.plugin;

import ca.spottedleaf.concurrentutil.util.Priority;
import io.canvasmc.canvas.spark.FoliaSparkPlugin;
import io.canvasmc.canvas.spark.profiler.RegionProfiler;
import io.canvasmc.canvas.spark.profiler.RegionScheduleHandlePinner;
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
import org.jspecify.annotations.NonNull;

public class FoliaWorldInfoProvider implements WorldInfoProvider {
    private final FoliaSparkPlugin plugin;
    private final Server server;

    public FoliaWorldInfoProvider(FoliaSparkPlugin plugin) {
        this.plugin = plugin;
        this.server = Bukkit.getServer();
    }

    @Override
    public CountsResult pollCounts() {
        if (RegionProfiler.isProfiling()) {
            // we need tile entities, chunks, entities, and players
            // if this isn't a region pinner, this is the global tick
            if (RegionProfiler.STATE.get().handlePinner() instanceof RegionScheduleHandlePinner.RegionPinner pinner) {
                final ServerLevel world = pinner.world();
                final ChunkPos target = pinner.getCenter();

                final CompletableFuture<CountsResult> result = new CompletableFuture<>();

                // schedule to region we were profiling at
                RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    world, target.x(), target.z(), () -> {
                        // we are scheduled to the region here, fetch localized information
                        RegionizedWorldData localWorldData = world.getCurrentWorldData();

                        int players = localWorldData.getPlayerCount();
                        int entities = localWorldData.getEntityCount();
                        int chunks = localWorldData.getChunkCount();
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
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    throw new RuntimeException("Couldn't fetch localized world data", e);
                }
            }
            else {
                // global tick owns nothing. we *could* have players be populated with connections it's
                // handling, but that isn't entirely accurate, especially at the interval it polls at
                return new CountsResult(0, 0, 0, 0);
            }
        }
        int players = this.server.getOnlinePlayers().size();
        int entities = 0;
        // TODO - implement?
        // we don't provide tile entity data because it's not thread-safe
        int tileEntities = 0;
        int chunks = 0;

        for (World world : this.server.getWorlds()) {
            entities += world.getEntityCount();
            chunks += world.getChunkCount();
        }

        return new CountsResult(players, entities, tileEntities, chunks);
    }

    @Override
    public ChunksResult<FoliaChunkInfo> pollChunks() {
        ChunksResult<FoliaChunkInfo> data = new ChunksResult<>();

        // Note: if we are ending a profiler, this will be cached, otherwise this is current
        if (RegionProfiler.isProfiling()) {
            if (RegionProfiler.STATE.get().handlePinner() instanceof RegionScheduleHandlePinner.RegionPinner pinner) {
                final ServerLevel world = pinner.world();
                final ChunkPos target = pinner.getCenter();
                final CompletableFuture<List<FoliaChunkInfo>> result = new CompletableFuture<>();

                RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    world, target.x(), target.z(), () -> {
                        List<FoliaChunkInfo> chunks = new ArrayList<>();
                        RegionizedWorldData worldData = world.getCurrentWorldData();
                        for (final LevelChunk nms : worldData.getChunks()) {
                            chunks.add(new FoliaChunkInfo(new CraftChunk(nms), world.getWorld(), this.plugin));
                        }
                        result.complete(chunks);
                    }, Priority.BLOCKING
                );

                try {
                    // timeout 5 seconds like FoliaChunkInfo#getEntityCounts
                    data.put(world.getWorld().getName(), result.get(5, TimeUnit.SECONDS));
                    return data;
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    throw new RuntimeException("Couldn't fetch localized world data", e);
                }
            }
            else return new ChunksResult<>(); // global tick, doesn't own chunks
        }

        // non-specific region profiler, just fetch everything (god I hate this)
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

    @SuppressWarnings({"removal"})
    @Override
    public Collection<DataPackInfo> pollDataPacks() {
        return this.server.getDatapackManager().getEnabledPacks().stream()
            .map(pack -> new DataPackInfo(
                pack.getTitle().examinableName(),
                pack.getDescription().examinableName(),
                pack.getSource().toString().toLowerCase(Locale.ROOT).replace("_", "")
            ))
            .collect(Collectors.toList());
    }

    public static final class FoliaChunkInfo extends AbstractChunkInfo<EntityType> {
        private final CompletableFuture<CountMap<EntityType>> entityCounts;

        FoliaChunkInfo(@NonNull Chunk chunk, World world, FoliaSparkPlugin plugin) {
            super(chunk.getX(), chunk.getZ());

            Executor executor = task -> RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(((CraftWorld) world).getHandle(), getX(), getZ(), task, Priority.BLOCKING);
            this.entityCounts = CompletableFuture.supplyAsync(() -> calculate(chunk), executor);
        }

        private @NonNull CountMap<EntityType> calculate(@NonNull Chunk chunk) {
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

        @Contract(pure = true)
        @SuppressWarnings("deprecation")
        @Override
        public String entityTypeName(@NonNull EntityType type) {
            return type.getName();
        }

    }

}
