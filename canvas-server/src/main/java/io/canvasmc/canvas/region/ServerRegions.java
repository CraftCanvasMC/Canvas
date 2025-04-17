package io.canvasmc.canvas.region;

import alternate.current.wire.WireHandler;
import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import com.google.common.collect.Sets;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.event.region.RegionCreateEvent;
import io.canvasmc.canvas.event.region.RegionDestroyEvent;
import io.canvasmc.canvas.event.region.RegionMergeEvent;
import io.canvasmc.canvas.event.region.RegionSplitEvent;
import io.canvasmc.canvas.scheduler.CanvasRegionScheduler;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import io.canvasmc.canvas.util.ConcurrentSet;
import io.canvasmc.canvas.util.TPSCalculator;
import io.papermc.paper.redstone.RedstoneWireTurbo;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.ticks.LevelTicks;
import org.bukkit.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ServerRegions {

    public static final class TickRegionSectionData implements ThreadedRegionizer.ThreadedRegionSectionData {}

    public static final class TickRegionData implements ThreadedRegionizer.ThreadedRegionData<TickRegionData, TickRegionSectionData>, Region {

        private final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region;
        public final ChunkRegion tickHandle;
        public final WorldTickData tickData;
        public final ServerLevel world;

        public TickRegionData(ThreadedRegionizer.@NotNull ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            this.region = region;
            this.world = region.regioniser.world;
            this.tickHandle = new ChunkRegion(region, (DedicatedServer) MinecraftServer.getServer());
            this.tickData = new WorldTickData(this.world, region);
        }

        @Override
        public void split(final @NotNull ThreadedRegionizer<TickRegionData, TickRegionSectionData> regioniser, final @NotNull Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> into, final @NotNull ReferenceOpenHashSet<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> regions) {
            final int shift = regioniser.sectionChunkShift;
            // copy current tick
            for (final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region : regions) {
                region.getData().tickData.currentTick = this.tickData.currentTick;
            }
            // generic
            {
                final ReferenceOpenHashSet<WorldTickData> dataSet = new ReferenceOpenHashSet<>(regions.size(), 0.75f);

                for (final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region : regions) {
                    dataSet.add(region.getData().tickData);
                }
                final Long2ReferenceOpenHashMap<WorldTickData> regionToData = new Long2ReferenceOpenHashMap<>(into.size(), 0.75f);

                for (final Iterator<Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>>> regionIterator = into.long2ReferenceEntrySet().fastIterator();
                     regionIterator.hasNext(); ) {
                    final Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> entry = regionIterator.next();
                    final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region = entry.getValue();

                    regionToData.put(entry.getLongKey(), region.getData().tickData);
                }
                splitRegion(regionToData, regioniser.sectionChunkShift, dataSet);
            }
            // chunk holder manager data
            {
                final ReferenceOpenHashSet<ChunkHolderManager.HolderManagerRegionData> dataSet = new ReferenceOpenHashSet<>(regions.size(), 0.75f);

                for (final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region : regions) {
                    dataSet.add(region.getData().tickData.holderManagerRegionData);
                }

                final Long2ReferenceOpenHashMap<ChunkHolderManager.HolderManagerRegionData> regionToData = new Long2ReferenceOpenHashMap<>(into.size(), 0.75f);

                for (final Iterator<Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>>> regionIterator = into.long2ReferenceEntrySet().fastIterator();
                     regionIterator.hasNext();) {
                    final Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> entry = regionIterator.next();
                    final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region = entry.getValue();
                    final ChunkHolderManager.HolderManagerRegionData to = region.getData().tickData.holderManagerRegionData;

                    regionToData.put(entry.getLongKey(), to);
                }

                this.tickData.holderManagerRegionData.split(shift, regionToData, dataSet);
            }
            this.tickData.taskQueueData.split(regioniser, into);
        }

        private void splitRegion(@NotNull Long2ReferenceOpenHashMap<WorldTickData> regionToData, int chunkToRegionShift, final @NotNull ReferenceOpenHashSet<WorldTickData> dataSet) {
            final WorldTickData from = this.tickData;
            // entities
            for (final ServerPlayer player : from.localPlayers) {
                final ChunkPos pos = player.chunkPosition();
                // Note: It is impossible for an entity in the world to _not_ be in an entity chunk, which means
                // the chunk holder must _exist_, and so the region section exists.
                final WorldTickData into = regionToData.get(CoordinateUtils.getChunkKey(pos.x >> chunkToRegionShift, pos.z >> chunkToRegionShift));
                if (into == null) {
                    // most likely teleported out
                    player.level().scheduleOnThread(() -> {
                        player.serverLevel().getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                        ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> threadedRegion = player.serverLevel().regioniser.getRegionAtUnsynchronised(pos.x, pos.z);
                        threadedRegion.getData().tickData.addEntity(player);
                    });
                    continue;
                }
                into.localPlayers.add(player);
                into.nearbyPlayers.addPlayer(player);
            }
            for (final Entity entity : from.allEntities) {
                final ChunkPos pos = entity.chunkPosition();
                // Note: It is impossible for an entity in the world to _not_ be in an entity chunk, which means
                // the chunk holder must _exist_, and so the region section exists.
                final WorldTickData into = regionToData.get(CoordinateUtils.getChunkKey(pos.x >> chunkToRegionShift, pos.z >> chunkToRegionShift));
                if (into == null) {
                    // if this was a player, we are already scheduled from when we ran local players, so we are fine
                    continue;
                }
                into.allEntities.add(entity);
                // Note: entityTickList is a subset of allEntities
                if (from.entityTickList.contains(entity)) {
                    into.entityTickList.add(entity);
                }
                // Note: loadedEntities is a subset of allEntities
                if (from.loadedEntities.contains(entity)) {
                    into.loadedEntities.add(entity);
                }
                // Note: navigatingMobs is a subset of allEntities
                if (entity instanceof Mob mob && from.navigatingMobs.contains(mob)) {
                    into.navigatingMobs.add(mob);
                }
                if (from.trackerEntities.contains(entity)) {
                    into.trackerEntities.add(entity);
                }
                if (from.trackerUnloadedEntities.contains(entity)) {
                    into.trackerUnloadedEntities.add(entity);
                }
            }
            // block ticking
            for (final BlockEventData blockEventData : from.blockEvents) {
                final BlockPos pos = blockEventData.pos();
                final int chunkX = pos.getX() >> 4;
                final int chunkZ = pos.getZ() >> 4;

                final WorldTickData into = regionToData.get(CoordinateUtils.getChunkKey(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift));
                // Unlike entities, the chunk holder is not guaranteed to exist for block events, because the block events
                // is just some list. So if it unloads, I guess it's just lost.
                if (into != null) {
                    into.blockEvents.add(blockEventData);
                }
            }

            final Long2ReferenceOpenHashMap<LevelTicks<Block>> levelTicksBlockRegionData = new Long2ReferenceOpenHashMap<>(regionToData.size(), 0.75f);
            final Long2ReferenceOpenHashMap<LevelTicks<Fluid>> levelTicksFluidRegionData = new Long2ReferenceOpenHashMap<>(regionToData.size(), 0.75f);

            for (final Iterator<Long2ReferenceMap.Entry<WorldTickData>> iterator = regionToData.long2ReferenceEntrySet().fastIterator();
                 iterator.hasNext();) {
                final Long2ReferenceMap.Entry<WorldTickData> entry = iterator.next();
                final long key = entry.getLongKey();
                final WorldTickData worldData = entry.getValue();

                levelTicksBlockRegionData.put(key, worldData.blockLevelTicks);
                levelTicksFluidRegionData.put(key, worldData.fluidLevelTicks);
            }

            from.blockLevelTicks.split(chunkToRegionShift, levelTicksBlockRegionData);
            from.fluidLevelTicks.split(chunkToRegionShift, levelTicksFluidRegionData);
            // tile entity ticking
            for (final TickingBlockEntity tileEntity : from.pendingBlockEntityTickers) {
                final BlockPos pos = tileEntity.getPos();
                final int chunkX = pos.getX() >> 4;
                final int chunkZ = pos.getZ() >> 4;

                final WorldTickData into = regionToData.get(CoordinateUtils.getChunkKey(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift));
                if (into != null) {
                    into.pendingBlockEntityTickers.add(tileEntity);
                } // else: when a chunk unloads, it does not actually _remove_ the tile entity from the list, it just gets
                //       marked as removed. So if there is no section, it's probably removed!
            }
            for (final TickingBlockEntity tileEntity : from.blockEntityTickers) {
                final BlockPos pos = tileEntity.getPos();
                final int chunkX = pos.getX() >> 4;
                final int chunkZ = pos.getZ() >> 4;

                final WorldTickData into = regionToData.get(CoordinateUtils.getChunkKey(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift));
                if (into != null) {
                    into.blockEntityTickers.add(tileEntity);
                } // else: when a chunk unloads, it does not actually _remove_ the tile entity from the list, it just gets
                //       marked as removed. So if there is no section, it's probably removed!
            }
            // time
            for (final WorldTickData regionizedWorldData : dataSet) {
                regionizedWorldData.redstoneTime = from.redstoneTime;
            }
            // ticking chunks
            for (final Iterator<ServerChunkCache.ChunkAndHolder> iterator = from.entityTickingChunks.iterator(); iterator.hasNext();) {
                final ServerChunkCache.ChunkAndHolder holder = iterator.next();
                final ChunkPos pos = holder.chunk().getPos();

                final WorldTickData data = regionToData.get(CoordinateUtils.getChunkKey(pos.x >> chunkToRegionShift, pos.z >> chunkToRegionShift));
                if (data == null) continue;
                data.entityTickingChunks.add(holder);
            }
            for (final Iterator<ServerChunkCache.ChunkAndHolder> iterator = from.tickingChunks.iterator(); iterator.hasNext();) {
                final ServerChunkCache.ChunkAndHolder holder = iterator.next();
                final ChunkPos pos = holder.chunk().getPos();

                final WorldTickData data = regionToData.get(CoordinateUtils.getChunkKey(pos.x >> chunkToRegionShift, pos.z >> chunkToRegionShift));
                if (data == null) continue;
                data.tickingChunks.add(holder);
            }
            for (final Iterator<ServerChunkCache.ChunkAndHolder> iterator = from.chunks.iterator(); iterator.hasNext();) {
                final ServerChunkCache.ChunkAndHolder holder = iterator.next();
                final ChunkPos pos = holder.chunk().getPos();

                final WorldTickData data = regionToData.get(CoordinateUtils.getChunkKey(pos.x >> chunkToRegionShift, pos.z >> chunkToRegionShift));
                if (data == null) continue;
                data.chunks.add(holder);
            }

            // redstone torches
            if (from.redstoneUpdateInfos != null && !from.redstoneUpdateInfos.isEmpty()) {
                for (final net.minecraft.world.level.block.RedstoneTorchBlock.Toggle info : from.redstoneUpdateInfos) {
                    final BlockPos pos = info.pos;

                    final WorldTickData worldData = regionToData.get(CoordinateUtils.getChunkKey((pos.getX() >> 4) >> chunkToRegionShift, (pos.getZ() >> 4) >> chunkToRegionShift));
                    if (worldData != null) {
                        if (worldData.redstoneUpdateInfos == null) {
                            worldData.redstoneUpdateInfos = new ArrayDeque<>();
                        }
                        worldData.redstoneUpdateInfos.add(info);
                    } // else: chunk unloaded
                }
            }
            // mob spawning
            for (final WorldTickData regionizedWorldData : dataSet) {
                regionizedWorldData.catSpawnerNextTick = from.catSpawnerNextTick;
                regionizedWorldData.patrolSpawnerNextTick = from.patrolSpawnerNextTick;
                regionizedWorldData.phantomSpawnerNextTick = from.phantomSpawnerNextTick;
                regionizedWorldData.wanderingTraderTickDelay = from.wanderingTraderTickDelay;
                regionizedWorldData.wanderingTraderSpawnChance = from.wanderingTraderSpawnChance;
                regionizedWorldData.wanderingTraderSpawnDelay = from.wanderingTraderSpawnDelay;
                regionizedWorldData.villageSiegeState = new WorldTickData.VillageSiegeState(); // just re set it, as the spawn pos will be invalid
            }
            // chunkHoldersToBroadcast
            for (final ChunkHolder chunkHolder : from.chunkHoldersToBroadcast) {
                final ChunkPos pos = chunkHolder.getPos();

                // Possible for get() to return null, as the chunk holder is not removed during unload
                final WorldTickData into = regionToData.get(CoordinateUtils.getChunkKey(pos.x >> chunkToRegionShift, pos.z >> chunkToRegionShift));
                if (into != null) {
                    into.chunkHoldersToBroadcast.add(chunkHolder);
                }
            }
            // region scheduler
            CanvasRegionScheduler.Scheduler.split(from.regionScheduler, chunkToRegionShift, regionToData, dataSet);
            // event
            for (final WorldTickData data : dataSet) {
                new RegionSplitEvent(from.region.getData(), data.region.getData());
            }
        }

        @Override
        public void mergeInto(final ThreadedRegionizer.@NotNull ThreadedRegion<TickRegionData, TickRegionSectionData> intoRegion) {
            final WorldTickData from = this.tickData;
            final WorldTickData into = intoRegion.getData().tickData;
            final long currentTickTo = into.peekTick();
            final long currentTickFrom = from.peekTick();
            final long fromTickOffset = currentTickTo - currentTickFrom;
            // time
            final long fromRedstoneTimeOffset = into.redstoneTime - from.redstoneTime;
            // entities
            for (final ServerPlayer player : from.localPlayers) {
                into.localPlayers.add(player);
                if (!into.nearbyPlayers.players.containsKey(player)) {
                    into.nearbyPlayers.addPlayer(player);
                }
            }
            for (final Entity entity : from.allEntities) {
                into.allEntities.add(entity);
                entity.updateTicks(fromTickOffset, fromRedstoneTimeOffset);
            }
            for (final Entity entity : from.loadedEntities) {
                into.loadedEntities.add(entity);
            }
            into.entityTickList.addAll(from.entityTickList);
            into.navigatingMobs.addAll(from.navigatingMobs);
            for (final Entity entity : from.trackerEntities) {
                into.trackerEntities.add(entity);
            }
            for (final Entity entity : from.trackerUnloadedEntities) {
                into.trackerUnloadedEntities.add(entity);
            }
            // block ticking
            into.blockEvents.addAll(from.blockEvents);
            // ticklists use game time
            from.blockLevelTicks.merge(into.blockLevelTicks, fromRedstoneTimeOffset);
            from.fluidLevelTicks.merge(into.fluidLevelTicks, fromRedstoneTimeOffset);
            // tile entity ticking
            for (final TickingBlockEntity tileEntityWrapped : from.pendingBlockEntityTickers) {
                into.pendingBlockEntityTickers.add(tileEntityWrapped);
                final BlockEntity tileEntity = tileEntityWrapped.getTileEntity();
                if (tileEntity != null) {
                    tileEntity.updateTicks(fromTickOffset, fromRedstoneTimeOffset);
                }
            }
            for (final TickingBlockEntity tileEntityWrapped : from.blockEntityTickers) {
                into.blockEntityTickers.add(tileEntityWrapped);
                final BlockEntity tileEntity = tileEntityWrapped.getTileEntity();
                if (tileEntity != null) {
                    tileEntity.updateTicks(fromTickOffset, fromRedstoneTimeOffset);
                }
            }
            // ticking chunks
            for (final Iterator<ServerChunkCache.ChunkAndHolder> iterator = from.entityTickingChunks.iterator(); iterator.hasNext();) {
                into.entityTickingChunks.add(iterator.next());
            }
            for (final Iterator<ServerChunkCache.ChunkAndHolder> iterator = from.tickingChunks.iterator(); iterator.hasNext();) {
                into.tickingChunks.add(iterator.next());
            }
            for (final Iterator<ServerChunkCache.ChunkAndHolder> iterator = from.chunks.iterator(); iterator.hasNext();) {
                into.chunks.add(iterator.next());
            }
            // redstone torches
            if (from.redstoneUpdateInfos != null && !from.redstoneUpdateInfos.isEmpty()) {
                if (into.redstoneUpdateInfos == null) {
                    into.redstoneUpdateInfos = new ArrayDeque<>();
                }
                for (final net.minecraft.world.level.block.RedstoneTorchBlock.Toggle info : from.redstoneUpdateInfos) {
                    info.offsetTime(fromRedstoneTimeOffset);
                    into.redstoneUpdateInfos.add(info);
                }
            }
            // mob spawning
            into.catSpawnerNextTick = Math.max(from.catSpawnerNextTick, into.catSpawnerNextTick);
            into.patrolSpawnerNextTick = Math.max(from.patrolSpawnerNextTick, into.patrolSpawnerNextTick);
            into.phantomSpawnerNextTick = Math.max(from.phantomSpawnerNextTick, into.phantomSpawnerNextTick);
            if (from.wanderingTraderTickDelay != Integer.MIN_VALUE && into.wanderingTraderTickDelay != Integer.MIN_VALUE) {
                into.wanderingTraderTickDelay = Math.max(from.wanderingTraderTickDelay, into.wanderingTraderTickDelay);
                into.wanderingTraderSpawnDelay = Math.max(from.wanderingTraderSpawnDelay, into.wanderingTraderSpawnDelay);
                into.wanderingTraderSpawnChance = Math.max(from.wanderingTraderSpawnChance, into.wanderingTraderSpawnChance);
            }
            // chunkHoldersToBroadcast
            into.chunkHoldersToBroadcast.addAll(from.chunkHoldersToBroadcast);
            this.tickData.holderManagerRegionData.merge(into.holderManagerRegionData, fromTickOffset);
            this.tickData.taskQueueData.mergeInto(into.taskQueueData);
            // region scheduler
            CanvasRegionScheduler.Scheduler.merge(from.regionScheduler, into.regionScheduler, fromTickOffset);
            // event
            new RegionMergeEvent(from.region.getData(), into.region.getData()).callEvent();
        }

        @Override
        public @NotNull LongArrayList getOwnedChunkPositions() {
            return this.region.getOwnedChunks();
        }

        @Override
        public @Nullable Long getCenterChunk() {
            ChunkPos center = this.region.getCenterChunk();
            return center == null ? null : center.longKey;
        }

        @Override
        public @NotNull World getWorld() {
            return this.world.getWorld();
        }

        @Override
        public WrappedTickLoop getTickHandle() {
            return this.tickHandle;
        }
    }

    // with how canvas works, we need an isolated class
    // for this, since TECHNICALLY we could have both a
    // region-sharded world, or a full region-world
    public static class WorldTickData {
        @Nullable
        public final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region;
        public final ServerLevel world;
        public final RegionizedTaskQueue.RegionTaskQueueData taskQueueData;
        public RegionizedTaskQueue.RegionTaskQueueData getTaskQueueData() {
            return this.taskQueueData;
        }
        private final ChunkHolderManager.HolderManagerRegionData holderManagerRegionData = new ChunkHolderManager.HolderManagerRegionData();

        public ChunkHolderManager.HolderManagerRegionData getHolderManagerRegionData() {
            return holderManagerRegionData;
        }

        private long currentTick = 0;

        public void popTick() {
            currentTick++;
        }

        public long peekTick() {
            return currentTick;
        }

        // ticks
        private long lagCompensationTick;

        public long getLagCompensationTick() {
            return this.lagCompensationTick;
        }

        public void updateLagCompensationTick() {
            this.lagCompensationTick = (System.nanoTime() - MinecraftServer.SERVER_INIT) / TickScheduler.getScheduler().getTimeBetweenTicks();
        }

        private boolean isHandlingTick;

        public void setHandlingTick(final boolean to) {
            this.isHandlingTick = to;
        }

        public boolean isHandlingTick() {
            return this.isHandlingTick;
        }

        // entities
        private static final Entity[] EMPTY_ENTITY_ARRAY = new Entity[0];
        private final List<ServerPlayer> localPlayers = new CopyOnWriteArrayList<>(); // concurrent
        private final NearbyPlayers nearbyPlayers;
        public final ReferenceList<Entity> allEntities = new ReferenceList<>(EMPTY_ENTITY_ARRAY);
        public final ReferenceList<Entity> loadedEntities = new ReferenceList<>(EMPTY_ENTITY_ARRAY);
        public final Set<Entity> entityTickList = Sets.newConcurrentHashSet();
        public final Set<Mob> navigatingMobs = Sets.newConcurrentHashSet(); // must be concurrent
        public final ReferenceList<Entity> trackerEntities = new ReferenceList<>(EMPTY_ENTITY_ARRAY) {
            @Override
            public boolean add(final Entity entity) {
                if (WorldTickData.this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("adding entity to tracker on non-region");
                return super.add(entity);
            }
        };

        public ReferenceList<Entity> getTrackerEntities(ChunkPos chunkPos) {
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing && chunkPos != null) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtSynchronised(chunkPos.x, chunkPos.z);
                if (theRegion == null) return trackerEntities;
                if (theRegion.getData().tickData != this) {
                    return theRegion.getData().tickData.trackerEntities;
                }
            }
            return trackerEntities;
        }

        private final ReferenceList<Entity> trackerUnloadedEntities = new ReferenceList<>(EMPTY_ENTITY_ARRAY);

        // block ticking
        private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
        private final LevelTicks<Block> blockLevelTicks;
        private final LevelTicks<Fluid> fluidLevelTicks;

        // tile entity ticking
        private final List<TickingBlockEntity> pendingBlockEntityTickers = new ArrayList<>();
        private final List<TickingBlockEntity> blockEntityTickers = new ArrayList<>();
        private boolean tickingBlockEntities;

        // time
        private long redstoneTime = 1L;

        public long getRedstoneGameTime() {
            return this.redstoneTime;
        }

        public void setRedstoneGameTime(final long to) {
            this.redstoneTime = to;
        }

        public void incrementRedstoneTime() {
            this.redstoneTime++;
        }

        // ticking chunks
        private static final ServerChunkCache.ChunkAndHolder[] EMPTY_CHUNK_AND_HOLDER_ARRAY = new ServerChunkCache.ChunkAndHolder[0];
        private final ReferenceList<ServerChunkCache.ChunkAndHolder> entityTickingChunks = new ReferenceList<>(EMPTY_CHUNK_AND_HOLDER_ARRAY);
        private final ReferenceList<ServerChunkCache.ChunkAndHolder> tickingChunks = new ReferenceList<>(EMPTY_CHUNK_AND_HOLDER_ARRAY);
        private final ReferenceList<ServerChunkCache.ChunkAndHolder> chunks = new ReferenceList<>(EMPTY_CHUNK_AND_HOLDER_ARRAY);

        // neighbor updater is threadlocal, don't need to isolate
        // captureDrops is threadlocal, don't need to isolate
        // capturedBlockStates is threadlocal, don't need to isolate
        // capturedTileEntities is threadlocal, don't need to isolate
        // preventPoiUpdated is threadlocal, don't need to isolate
        // captureBlockStates is threadlocal, don't need to isolate
        // captureTreeGeneration is threadlocal, don't need to isolate
        // isBlockPlaceCancelled is threadlocal, don't need to isolate
        // populating is threadlocal, don't need to isolate
        // has___Event does not need to be isolated, as this pulls from the entire server registered listeners regardless, so no point in sharding.
        // lastMidTickExecute is threadlocal, don't need to isolate
        // lastMidTickExecuteFailure is threadlocal, don't need to isolate
        public int wakeupInactiveRemainingAnimals;
        public int wakeupInactiveRemainingFlying;
        public int wakeupInactiveRemainingMonsters;
        public int wakeupInactiveRemainingVillagers;
        public int currentPrimedTnt = 0;
        @Nullable
        @VisibleForDebug
        private NaturalSpawner.SpawnState lastSpawnState;
        public @Nullable NaturalSpawner.SpawnState getLastSpawnState() {
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("attempting to access spawn state from level when regionized");
            return this.lastSpawnState;
        }
        public void setLastSpawnState(NaturalSpawner.SpawnState spawnState) {
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("attempting to access spawn state from level when regionized");
            this.lastSpawnState = spawnState;
        }
        // shouldSignal is threadlocal, don't need to isolate
        public final Map<ServerExplosion.CacheKey, Float> explosionDensityCache = new HashMap<>(64, 0.25f);
        public final PathTypeCache pathTypesByPosCache = new PathTypeCache();
        public final List<LevelChunk> temporaryChunkTickList = new ObjectArrayList<>();
        private final Set<ChunkHolder> chunkHoldersToBroadcast = new ConcurrentSet<>();
        public Set<ChunkHolder> getChunkHoldersToBroadcast() {
            // this is processed on the async loader, which can only pull the level, so we must have it on the level
            if (this.world.levelTickData == null) {
                this.world.levelTickData = new WorldTickData(this.world, null);
            }
            return this.world.levelTickData.chunkHoldersToBroadcast;
        }
        // not transient
        public ArrayDeque<RedstoneTorchBlock.Toggle> redstoneUpdateInfos;
        // mob spawning
        public final PositionCountingAreaMap<ServerPlayer> spawnChunkTracker = new PositionCountingAreaMap<>();
        public int catSpawnerNextTick = 0;
        public int patrolSpawnerNextTick = 0;
        public int phantomSpawnerNextTick = 0;
        public int wanderingTraderTickDelay = Integer.MIN_VALUE;
        public int wanderingTraderSpawnDelay;
        public int wanderingTraderSpawnChance;
        public VillageSiegeState villageSiegeState = new VillageSiegeState();

        public static final class VillageSiegeState {
            public boolean hasSetupSiege;
            public VillageSiege.State siegeState = VillageSiege.State.SIEGE_DONE;
            public int zombiesToSpawn;
            public int nextSpawnTime;
            public int spawnX;
            public int spawnY;
            public int spawnZ;
        }
        // redstone
        public final WireHandler wireHandler;
        public final RedstoneWireTurbo turbo;
        // canvas
        public TPSCalculator tpsCalculator = new TPSCalculator();
        // scheduler
        public final CanvasRegionScheduler.Scheduler regionScheduler = new CanvasRegionScheduler.Scheduler();

        public WorldTickData(ServerLevel world, final @Nullable ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            this.world = world;
            this.blockLevelTicks = new LevelTicks<>(world::isPositionTickingWithEntitiesLoaded, world, true);
            this.fluidLevelTicks = new LevelTicks<>(world::isPositionTickingWithEntitiesLoaded, world, false);
            this.nearbyPlayers = new NearbyPlayers(world);
            this.wireHandler = new WireHandler(world);
            this.region = region;
            this.turbo = new RedstoneWireTurbo((RedStoneWireBlock) Blocks.REDSTONE_WIRE);
            this.taskQueueData = new RegionizedTaskQueue.RegionTaskQueueData(this.world.taskQueueRegionData);
        }

        public ServerLevel getWorld() {
            return world;
        }

        // entities hooks
        public NearbyPlayers getNearbyPlayers() {
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("accessing nearby players from global");
            return this.nearbyPlayers;
        }

        public NearbyPlayers getNearbyPlayers(ChunkPos position) {
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(position.x, position.z);
                if (theRegion == null) {
                    return this.getNearbyPlayers();
                }
                if (theRegion.getData().tickData != this) {
                    return theRegion.getData().tickData.getNearbyPlayers();
                }
            }
            return this.getNearbyPlayers();
        }

        public Collection<Entity> getLocalEntities(ChunkPos pos) {
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(pos.x, pos.z);
                if (theRegion.getData().tickData != this) {
                    return theRegion.getData().tickData.allEntities;
                }
            }
            return this.allEntities;
        }

        public Entity[] getLocalEntitiesCopy() {
            return Arrays.copyOf(this.allEntities.getRawDataUnchecked(), this.allEntities.size(), Entity[].class);
        }

        public List<ServerPlayer> getLocalPlayers() {
            return this.localPlayers;
        }

        public List<ServerPlayer> getLocalPlayers(ChunkPos pos) {
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(pos.x, pos.z);
                if (theRegion.getData().tickData != this) {
                    return theRegion.getData().tickData.localPlayers;
                }
            }
            return this.localPlayers;
        }

        public void addLoadedEntity(final Entity entity) {
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(entity.chunkPosition().x, entity.chunkPosition().z);
                // the chunk has to exist for the entity to be added, so we are ok to assume non-null
                if (theRegion.getData().tickData != this) {
                    theRegion.getData().tickData.addLoadedEntity(entity);
                    return;
                }
            }
            this.loadedEntities.add(entity);
        }

        public void removeLoadedEntity(final Entity entity) {
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(entity.chunkPosition().x, entity.chunkPosition().z);
                // the chunk has to exist for the entity to be added, so we are ok to assume non-null
                if (theRegion.getData().tickData != this) {
                    theRegion.getData().tickData.removeLoadedEntity(entity);
                    return;
                }
            }
            this.loadedEntities.remove(entity);
        }

        public Iterable<Entity> getLoadedEntities() {
            return this.loadedEntities;
        }

        public void addEntityTickingEntity(final Entity entity) {
            if (!TickThread.isTickThreadFor(entity)) {
                throw new IllegalArgumentException("Entity " + entity + " is not under this region's control");
            }
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(entity.chunkPosition().x, entity.chunkPosition().z);
                // the chunk has to exist for the entity to be added, so we are ok to assume non-null
                if (theRegion.getData().tickData != this) {
                    theRegion.getData().tickData.addEntityTickingEntity(entity);
                    return;
                }
            }
            this.entityTickList.add(entity);
        }

        public boolean hasEntityTickingEntity(final Entity entity) {
            return this.entityTickList.contains(entity);
        }

        public void removeEntityTickingEntity(final Entity entity) {
            if (!TickThread.isTickThreadFor(entity)) {
                throw new IllegalArgumentException("Entity " + entity + " is not under this region's control");
            }
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(entity.chunkPosition().x, entity.chunkPosition().z);
                // the chunk has to exist for the entity to be added, so we are ok to assume non-null
                if (theRegion == null) {
                    // syncload it... this really only happens inter-dimensionally
                    this.world.getChunkSource().getChunk(entity.chunkPosition().x, entity.chunkPosition().z, ChunkStatus.FULL, true);
                    theRegion = this.world.regioniser.getRegionAtSynchronised(entity.chunkPosition().x, entity.chunkPosition().z); // its loaded now.
                }
                if (theRegion.getData().tickData != this) {
                    theRegion.getData().tickData.removeEntityTickingEntity(entity);
                    return;
                }
            }
            this.entityTickList.remove(entity);
        }

        public void forEachTickingEntity(final Consumer<Entity> action) {
            for (final Entity entity : this.entityTickList) {
                action.accept(entity);
            }
        }

        public void addEntity(final @NotNull Entity entity) {
            addEntity(entity, true);
        }

        public void addEntity(final @NotNull Entity entity, boolean check) {
            if (!TickThread.isTickThreadFor(this.world, entity.chunkPosition())) {
                throw new IllegalArgumentException("Entity " + entity + " is not under this region's control");
            }
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing && check) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(entity.chunkPosition().x, entity.chunkPosition().z);
                // the chunk has to exist for the entity to be added, so we are ok to assume non-null
                if (theRegion == null) {
                    // syncload it... this really only happens inter-dimensionally
                    this.world.getChunkSource().getChunk(entity.chunkPosition().x, entity.chunkPosition().z, ChunkStatus.FULL, true);
                    theRegion = this.world.regioniser.getRegionAtSynchronised(entity.chunkPosition().x, entity.chunkPosition().z); // its loaded now.
                }
                if (theRegion.getData().tickData != this) {
                    theRegion.getData().tickData.addEntity(entity, false);
                    return;
                }
            }
            this.allEntities.add(entity);
            if (entity instanceof ServerPlayer player) {
                this.localPlayers.add(player);
                if (!this.getNearbyPlayers(player.chunkPosition()).players.containsKey(player)) {
                    this.getNearbyPlayers(player.chunkPosition()).addPlayer(player); // moved from entity callback, required or else we might add to the world by mistake
                }
            }
        }

        public void removeEntity(final Entity entity) {
            removeEntity(entity, true);
        }

        public void removeEntity(final Entity entity, boolean check) {
            if (!TickThread.isTickThreadFor(entity)) {
                throw new IllegalArgumentException("Entity " + entity + " is not under this region's control");
            }
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing && check) {
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(entity.chunkPosition().x, entity.chunkPosition().z);
                // the chunk has to exist for the entity to be added, so we are ok to assume non-null
                if (theRegion.getData().tickData != this) {
                    theRegion.getData().tickData.removeEntity(entity, false);
                    return;
                }
            }
            this.allEntities.remove(entity);
            if (entity instanceof ServerPlayer player) {
                this.localPlayers.remove(player);
            }
        }

        // block ticking hooks
        // Since block event data does not require chunk holders to be created for the chunk they reside in,
        // it's not actually guaranteed that when merging / splitting data that we actually own the data...
        // Note that we can only ever not own the event data when the chunk unloads, and so I've decided to
        // make the code easier by simply discarding it in such an event
        public void pushBlockEvent(final @NotNull BlockEventData blockEventData) {
            TickThread.ensureTickThread(this.world, blockEventData.pos(), "Cannot queue block even data async");
            // let's ensure we actually run this on the appropriate region
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                ChunkPos pos = new ChunkPos(blockEventData.pos());
                this.world.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> theRegion = this.world.regioniser.getRegionAtUnsynchronised(pos.x, pos.z);
                // the chunk has to exist for the block event to be added, so we are ok to assume non-null
                if (theRegion.getData().tickData != this) {
                    this.blockEvents.add(blockEventData);
                    return;
                }
            }
            this.blockEvents.add(blockEventData);
        }

        public void pushBlockEvents(final @NotNull Collection<? extends BlockEventData> blockEvents) {
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("cannot push block event to non-region");
            for (final BlockEventData blockEventData : blockEvents) {
                this.pushBlockEvent(blockEventData);
            }
        }

        public void removeIfBlockEvents(final Predicate<? super BlockEventData> predicate) {
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("cannot remove block event from non-region");
            for (final Iterator<BlockEventData> iterator = this.blockEvents.iterator(); iterator.hasNext();) {
                final BlockEventData blockEventData = iterator.next();
                if (predicate.test(blockEventData)) {
                    iterator.remove();
                }
            }
        }

        public BlockEventData removeFirstBlockEvent() {
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("cannot remove first block event from non-region");
            BlockEventData ret;
            while (!this.blockEvents.isEmpty()) {
                ret = this.blockEvents.removeFirst();
                if (TickThread.isTickThreadFor(this.world, ret.pos())) {
                    return ret;
                } // else: chunk must have been unloaded
            }

            return null;
        }

        public LevelTicks<Block> getBlockLevelTicks() {
            return this.blockLevelTicks;
        }

        public LevelTicks<Fluid> getFluidLevelTicks() {
            return this.fluidLevelTicks;
        }

        // tile entity ticking
        public void addBlockEntityTicker(final @NotNull TickingBlockEntity ticker) {
            TickThread.ensureTickThread(this.world, ticker.getPos(), "Tile entity must be owned by current region");

            // ensure we are on correct region data
            BlockPos position = ticker.getPos();
            if (position == null && ticker.getTileEntity() != null) {
                position = ticker.getTileEntity().worldPosition;
            }
            if (position == null) throw new RuntimeException("Unable to add block entity ticker, cannot pull position");
            if (this.region == null && Config.INSTANCE.ticking.enableThreadedRegionizing) {
                // we are on level... translate to region
                // to add tickers, the chunk cannot be null.
                int x = SectionPos.blockToSectionCoord(position.getX());
                int z = SectionPos.blockToSectionCoord(position.getZ());
                ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> threadedRegion = this.world.regioniser.getRegionAtUnsynchronised(x, z);
                if (threadedRegion == null) {
                    throw new RuntimeException("Attempted to add block entity in a chunk not owned by region");
                } else {
                    threadedRegion.getData().tickData.addBlockEntityTicker(ticker);
                }
                return;
            }
            (this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(ticker);
        }

        public void setTickingBlockEntities(final boolean to) {
            this.tickingBlockEntities = true;
        }

        public List<TickingBlockEntity> getBlockEntityTickers() {
            return this.blockEntityTickers;
        }

        public void pushPendingTickingBlockEntities() {
            if (!this.pendingBlockEntityTickers.isEmpty()) {
                this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
                this.pendingBlockEntityTickers.clear();
            }
        }

        // ticking chunks
        public void addEntityTickingChunk(final ServerChunkCache.ChunkAndHolder holder) {
            this.entityTickingChunks.add(holder);
        }

        public void removeEntityTickingChunk(final ServerChunkCache.ChunkAndHolder holder) {
            this.entityTickingChunks.remove(holder);
        }

        public ReferenceList<ServerChunkCache.ChunkAndHolder> getEntityTickingChunks() {
            return this.entityTickingChunks;
        }

        public void addTickingChunk(final ServerChunkCache.ChunkAndHolder holder) {
            this.tickingChunks.add(holder);
        }

        public void removeTickingChunk(final ServerChunkCache.ChunkAndHolder holder) {
            this.tickingChunks.remove(holder);
        }

        public ReferenceList<ServerChunkCache.ChunkAndHolder> getTickingChunks() {
            return this.tickingChunks;
        }

        public void addChunk(final ServerChunkCache.ChunkAndHolder holder) {
            this.chunks.add(holder);
        }

        public void removeChunk(final ServerChunkCache.ChunkAndHolder holder) {
            this.chunks.remove(holder);
        }

        public ReferenceList<ServerChunkCache.ChunkAndHolder> getChunks() {
            return this.chunks;
        }
    }

    public static @NotNull WorldTickData getTickData(@NotNull ServerLevel level) {
        if (level.levelTickData == null) {
            level.levelTickData = new WorldTickData(level, null);
        }
        if (!Config.INSTANCE.ticking.enableThreadedRegionizing) {
            return level.levelTickData;
        }
        WorldTickData possible = pullRegionData();
        if (possible != null && possible.world == level && possible.region != null) return possible;
        return level.levelTickData;
    }

    private static @Nullable WorldTickData pullRegionData() {
        Thread current = Thread.currentThread();
        if (current instanceof TickScheduler.TickRunner runner) {
            // the runners CAN have a region attached to it.
            // if this is null, then there isn't a region actively ticking this, so we should pull the level.
            return runner.threadLocalTickData;
        }
        return null;
    }

    public static long getCurrentTick(ServerLevel level) throws IllegalStateException {
        return getTickData(level).currentTick;
    }

    public static final class TickRegions implements ThreadedRegionizer.RegionCallbacks<TickRegionData, TickRegionSectionData> {
        @Contract(pure = true)
        @Override
        public @Nullable TickRegionSectionData createNewSectionData(final int sectionX, final int sectionZ, final int sectionShift) {
            return null;
        }

        @Contract("_ -> new")
        @Override
        public @NotNull TickRegionData createNewData(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            return new TickRegionData(region);
        }

        @Override
        public void onRegionCreate(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            new RegionCreateEvent(region.getData()).callEvent();
        }

        @Override
        public void onRegionDestroy(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            new RegionDestroyEvent(region.getData()).callEvent();
        }

        @Override
        public void onRegionActive(final ThreadedRegionizer.@NotNull ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            region.getData().tickHandle.scheduleTo(TickScheduler.getScheduler().scheduler);
        }

        @Override
        public void onRegionInactive(final ThreadedRegionizer.@NotNull ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            TickRegionData data = region.getData();
            data.tickHandle.tick.markNonSchedulable();
        }

        @Override
        public void preMerge(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> from, final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> into) {
        }

        @Override
        public void preSplit(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> from, final List<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> into) {
        }
    }
}
