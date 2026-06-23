package me.lucko.spark.paper.common.platform.world;

import me.lucko.spark.paper.proto.SparkProtos.WorldStatistics;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Source: <a href="https://github.com/lucko/spark/blob/6c7e414530425da7e167f0060cc0a67d4771f3f9/spark-common/src/main/java/me/lucko/spark/common/platform/world/WorldStatisticsProvider.java">WorldStatisticsProvider.java</a>
 */
public class WorldStatisticsProvider {
    private final AsyncWorldInfoProvider provider;

    public WorldStatisticsProvider(AsyncWorldInfoProvider provider) {
        this.provider = provider;
    }

    public WorldStatistics getWorldStatistics() {
        WorldInfoProvider.ChunksResult<? extends ChunkInfo<?>> result = this.provider.getChunks();
        if (result == null) {
            return null;
        }

        WorldStatistics.Builder stats = WorldStatistics.newBuilder();

        AtomicInteger combinedTotal = new AtomicInteger();
        CountMap<String> combined = new CountMap.Simple<>(new HashMap<>());

        result.getWorlds().forEach((worldName, chunks) -> {
            WorldStatistics.World.Builder builder = WorldStatistics.World.newBuilder();
            builder.setName(worldName);

            List<Region> regions = groupIntoRegions(worldName, chunks); // Canvas - pass worldName to groupIntoRegions

            int total = 0;

            for (Region region : regions) {
                builder.addRegions(regionToProto(region, combined));
                total += region.getTotalEntities().get();
            }

            builder.setTotalEntities(total);
            combinedTotal.addAndGet(total);

            stats.addWorlds(builder.build());
        });

        stats.setTotalEntities(combinedTotal.get());
        combined.asMap().forEach((key, value) -> stats.putEntityCounts(key, value.get()));

        WorldInfoProvider.GameRulesResult gameRules = this.provider.getGameRules();
        if (gameRules != null) {
            gameRules.getRules().forEach((ruleName, rule) -> stats.addGameRules(WorldStatistics.GameRule.newBuilder()
                .setName(ruleName)
                .setDefaultValue(rule.getDefaultValue())
                .putAllWorldValues(rule.getWorldValues())
                .build()
            ));
        }

        Collection<WorldInfoProvider.DataPackInfo> dataPacks = this.provider.getDataPacks();
        if (dataPacks != null) {
            dataPacks.forEach(dataPack -> stats.addDataPacks(WorldStatistics.DataPack.newBuilder()
                .setName(dataPack.name())
                .setDescription(dataPack.description())
                .setSource(dataPack.source())
                .build()
            ));
        }

        return stats.build();
    }

    private static WorldStatistics.Region regionToProto(Region region, CountMap<String> combined) {
        WorldStatistics.Region.Builder builder = WorldStatistics.Region.newBuilder();
        builder.setTotalEntities(region.getTotalEntities().get());
        for (ChunkInfo<?> chunk : region.getChunks()) {
            builder.addChunks(chunkToProto(chunk, combined));
        }
        return builder.build();
    }

    private static <E> WorldStatistics.Chunk chunkToProto(ChunkInfo<E> chunk, CountMap<String> combined) {
        WorldStatistics.Chunk.Builder builder = WorldStatistics.Chunk.newBuilder();
        builder.setX(chunk.getX());
        builder.setZ(chunk.getZ());
        builder.setTotalEntities(chunk.getEntityCounts().total().get());
        chunk.getEntityCounts().asMap().forEach((key, value) -> {
            String name = chunk.entityTypeName(key);
            int count = value.get();

            if (name == null) {
                name = "unknown[" + key.toString() + "]";
            }

            builder.putEntityCounts(name, count);
            combined.add(name, count);
        });
        return builder.build();
    }

    @VisibleForTesting
    // Canvas start - pass worldName and group chunks by Folia region ID instead of Euclidean distance
    static List<Region> groupIntoRegions(String worldName, List<? extends ChunkInfo<?>> chunks) {
        Map<Long, Region> regions = new LinkedHashMap<>();
        for (ChunkInfo<?> chunk : chunks) {
            CountMap<?> counts = chunk.getEntityCounts();
            if (counts.total().get() == 0) {
                continue;
            }

            long id;
            if (chunk instanceof io.canvasmc.canvas.spark.plugin.AbstractFoliaChunkInfo<?> folia) {
                id = folia.getFoliaRegionId();
            } else {
                id = Long.MIN_VALUE;
            }

            Region region = regions.get(id);
            if (region == null) {
                region = new Region(worldName, id == Long.MIN_VALUE ? 0L : id, chunk);
                regions.put(id, region);
            } else {
                region.add(chunk);
            }
        }

        return new ArrayList<>(regions.values());
        // Canvas end -  pass worldName and group chunks by Folia region ID instead of Euclidean distance
    }

    /**
     * A map of nearby chunks grouped together by Euclidean distance.
     */
    static final class Region {
        private final Set<ChunkInfo<?>> chunks;
        private final AtomicInteger totalEntities;
        // Canvas start - track world name and Folia region ID
        final String worldName;
        final long foliaRegionId;
        // Canvas end - track world name and Folia region ID

        // Canvas start - accept worldName and foliaRegionId
        private Region(String worldName, long foliaRegionId, ChunkInfo<?> initial) {
            this.worldName = worldName;
            this.foliaRegionId = foliaRegionId;
            this.chunks = new HashSet<>();
            this.chunks.add(initial);
            this.totalEntities = new AtomicInteger(initial.getEntityCounts().total().get());
        }
        // Canvas end - accept worldName and foliaRegionId

        public Set<ChunkInfo<?>> getChunks() {
            return this.chunks;
        }

        public AtomicInteger getTotalEntities() {
            return this.totalEntities;
        }

        public void add(ChunkInfo<?> chunk) {
            this.chunks.add(chunk);
            this.totalEntities.addAndGet(chunk.getEntityCounts().total().get());
        }

        // Canvas start - expose Folia region ID
        public long getFoliaRegionId() {
            return foliaRegionId;
        }
        // Canvas end - expose Folia region ID
    }

    static final class ChunkCoordinate implements Comparable<ChunkCoordinate> {
        long key;

        ChunkCoordinate() {}

        ChunkCoordinate(int chunkX, int chunkZ) {
            this.setCoordinate(chunkX, chunkZ);
        }
        ChunkCoordinate(long key) {
            this.setKey(key);
        }

        public void setCoordinate(int chunkX, int chunkZ) {
            this.setKey(((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL));
        }

        public void setKey(long key) {
            this.key = key;
        }

        @Override
        public int hashCode() {
            // fastutil hash without the last step, as it is done by HashMap
            // doing the last step twice (h ^= (h >>> 16)) is both more expensive and destroys the hash
            long h = this.key * 0x9E3779B97F4A7C15L;
            h ^= h >>> 32;
            return (int) h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ChunkCoordinate)) {
                return false;
            }
            return this.key == ((ChunkCoordinate) obj).key;
        }

        @Override
        public int compareTo(ChunkCoordinate other) {
            return Long.compare(this.key, other.key);
        }
    }
}
