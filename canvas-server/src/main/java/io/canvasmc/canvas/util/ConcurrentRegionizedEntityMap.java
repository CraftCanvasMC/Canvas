package io.canvasmc.canvas.util;

import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.util.fastutil.Int2ObjectConcurrentHashMap;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectCollections;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.agrona.collections.ObjectHashSet;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentRegionizedEntityMap implements Int2ObjectMap<ChunkMap.TrackedEntity> {
    private final ServerLevel level;

    public Set<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>> getRegions() {
        Set<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>> regions = new ObjectHashSet<>();
        level.regioniser.computeForAllRegions(regions::add);
        return regions;
    }

    public ConcurrentRegionizedEntityMap(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int size() {
        final AtomicInteger total = new AtomicInteger();
        level.regioniser.computeForAllRegions((region -> total.addAndGet(region.getData().tickData.trackerEntities.size())));
        return total.get();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsValue(final Object value) {
        // the value must be a TrackedEntity
        if (!(value instanceof final ChunkMap.TrackedEntity tracked)) return false;
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : getRegions()) {
            if (region.getData().tickData.trackerEntities.contains(tracked.entity)) return true;
        }
        return false;
    }

    @Override
    public void putAll(@NotNull final Map<? extends Integer, ? extends ChunkMap.TrackedEntity> m) {
        m.forEach((_, value) -> {
            // we need to shard this now into each region
            ChunkPos chunk = value.entity.chunkPosition();
            ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region = this.level.regioniser.getRegionAtUnsynchronised(chunk.x, chunk.z);
            if (region == null) throw new RuntimeException("unable to shard to region for putAll");
            region.getData().tickData.trackerEntities.add(value.entity);
        });
    }

    @Override
    public void defaultReturnValue(final ChunkMap.TrackedEntity rv) {
        // no default return value
    }

    @Override
    public ChunkMap.TrackedEntity defaultReturnValue() {
        // no default return value
        return null;
    }

    @Override
    public ObjectSet<Entry<ChunkMap.TrackedEntity>> int2ObjectEntrySet() {
        Int2ObjectMap<ChunkMap.TrackedEntity> entrySetMap = new Int2ObjectConcurrentHashMap<>();
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : this.getRegions()) {
            for (final Entity entity : region.getData().tickData.trackerEntities) {
                if (entity != null) entrySetMap.put(entity.getId(), entity.moonrise$getTrackedEntity());
            }
        }
        return entrySetMap.int2ObjectEntrySet();
    }

    @Override
    public @NotNull IntSet keySet() {
        IntSet intSet = IntSets.synchronize(new IntArraySet());
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : this.getRegions()) {
            for (final Entity entity : region.getData().tickData.trackerEntities) {
                if (entity != null) intSet.add(entity.getId());
            }
        }
        return intSet;
    }

    @Override
    public @NotNull ObjectCollection<ChunkMap.TrackedEntity> values() {
        ObjectCollection<ChunkMap.TrackedEntity> objectCollection = ObjectCollections.synchronize(new ObjectArraySet<>());
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : this.getRegions()) {
            for (final Entity entity : region.getData().tickData.trackerEntities) {
                if (entity != null) objectCollection.add(entity.moonrise$getTrackedEntity());
            }
        }
        return objectCollection;
    }

    @Override
    public ChunkMap.TrackedEntity get(final int key) {
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : getRegions()) {
            for (final Entity trackerEntity : region.getData().tickData.trackerEntities) {
                if (trackerEntity.getId() == key) return trackerEntity.moonrise$getTrackedEntity();
            }
        }
        return null;
    }

    @Override
    public boolean containsKey(final int key) {
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : getRegions()) {
            for (final Entity trackerEntity : region.getData().tickData.trackerEntities) {
                if (trackerEntity.getId() == key) return true;
            }
        }
        return false;
    }

    @Override
    public ChunkMap.TrackedEntity remove(final int key) {
        for (final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region : getRegions()) {
            for (final Entity trackerEntity : region.getData().tickData.trackerEntities) {
                if (trackerEntity.getId() == key) {
                    region.getData().tickData.trackerEntities.remove(trackerEntity);
                    return trackerEntity.moonrise$getTrackedEntity();
                }
            }
        }
        return null;
    }

    @Override
    public ChunkMap.TrackedEntity remove(final Object key) {
        if (!(key instanceof Integer integer)) return null;
        return remove((int) integer);
    }
}
