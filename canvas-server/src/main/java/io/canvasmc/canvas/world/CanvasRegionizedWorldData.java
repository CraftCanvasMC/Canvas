package io.canvasmc.canvas.world;

import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.region.RegionTickData;
import io.canvasmc.canvas.world.chunk.NatureSpawnChunkMap;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FlowingFluid;
import org.bukkit.craftbukkit.CraftWorld;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CanvasRegionizedWorldData {
    private static final RegionTickData.IRegionizedData<CanvasRegionizedWorldData> CANVAS_REGION_DATA = TickRegions.TickRegionData.createRegionizedDataApiHandle(
        (regionTickData, world) -> new CanvasRegionizedWorldData((TickRegions.TickRegionData) regionTickData, ((CraftWorld) world).getHandle()),
        new RegionTickData.IRegionizedData.IRegionizedCallback<>() {
            @Override
            public void merge(
                final @NonNull CanvasRegionizedWorldData from,
                final @NonNull CanvasRegionizedWorldData into,
                final long fromTickOffset
            ) {
                // no-op currently
            }

            @Override
            public void split(
                final @NonNull CanvasRegionizedWorldData from,
                final int chunkToRegionShift,
                final @NonNull Long2ReferenceOpenHashMap<CanvasRegionizedWorldData> regionToData,
                final @NonNull ReferenceOpenHashSet<CanvasRegionizedWorldData> dataSet
            ) {
                // no-op currently
            }
        }
    );

    public static @Nullable CanvasRegionizedWorldData getCurrentWorldData() {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>
            region = TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            return null;
        }
        return region.getData().getOrCreateFromIRegionizedData(CANVAS_REGION_DATA);
    }

    private final TickRegions.TickRegionData regionData;
    private final ServerLevel level;
    private final RegionizedWorldData worldData;

    // this is transient, from Leaf optimized flowing fluids
    public LongSet slopeDistanceCacheVisited = new LongOpenHashSet(512);
    public FlowingFluid.SlopeDistanceNodeDeque slopeDistanceCacheQueue = new FlowingFluid.SlopeDistanceNodeDeque();

    // natural spawn chunk optimizations from Leaf
    public final NatureSpawnChunkMap natureSpawnChunkMap = new NatureSpawnChunkMap();

    public final RandomSource simpleUnsafeLocalRandom = GlobalConfiguration.createFastRandom();

    // tpsbar and rambar
    public final RegionResourceBar tpsbar;
    public final RegionResourceBar rambar;

    public long projectilesLoadedTick = 0;
    public long projectilesLoadedThisTick = 0;

    private CanvasRegionizedWorldData(final TickRegions.TickRegionData regionData, final ServerLevel level) {
        this.regionData = regionData;
        this.level = level;

        final RegionizedWorldData worldData = TickRegionScheduler.getCurrentRegionizedWorldData();

        this.tpsbar = new RegionizedTpsBar(worldData);
        this.rambar = new RegionizedRamBar(worldData);

        this.worldData = worldData;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public RegionizedWorldData getFoliaWorldData() {
        return worldData;
    }

    public TickRegions.TickRegionData getRegionData() {
        return regionData;
    }
}
