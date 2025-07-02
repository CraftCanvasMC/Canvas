package io.canvasmc.canvas.region;

import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.ThreadedServerHealthDump;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import io.canvasmc.canvas.server.MultiWatchdogThread;
import io.canvasmc.canvas.server.ThreadedServer;
import io.canvasmc.canvas.util.IdGenerator;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ChunkRegion extends TickScheduler.FullTick<ChunkRegion.TickHandle> {
    private static final Map<ResourceLocation, IdGenerator> LEVEL_TO_ID_GENERATOR = new ConcurrentHashMap<>();
    public static final Function<ServerLevel, ResourceLocation> IDENTIFIER_GENERATOR = (level) -> {
        int id = LEVEL_TO_ID_GENERATOR.computeIfAbsent(level.dimension().location(), (_) -> new IdGenerator()).poll();
        return ResourceLocation.fromNamespaceAndPath("canvas", level.dimension().location().getPath() + "_region_" + id);
    };
    public final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region;
    public final ServerLevel world;

    public ChunkRegion(final ThreadedRegionizer.@NotNull ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region, final DedicatedServer server) {
        super(server, IDENTIFIER_GENERATOR.apply(region.regioniser.world), new TickHandle(region), false);
        this.region = region;
        this.world = region.regioniser.world;
    }

    @Override
    public String toString() {
        ChunkPos pos = this.region.getCenterChunk();
        return "Region [" + this.world.toString() + "," + (pos == null ? "null" : pos.x + "," + pos.z) + "]";
    }

    @Override
    public @NotNull Component debugInfo() {
        int pendingBlock = this.region.getData().tickData.getBlockLevelTicks().count();
        int pendingFluid = this.region.getData().tickData.getFluidLevelTicks().count();
        int localPlayers = this.region.getData().tickData.getLocalPlayers().size();
        int localEntities = this.region.getData().tickData.getLocalEntitiesCopy().length;
        TextComponent.Builder basic = Component.text()
            .append(Component.text("Basic Information", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Pending Block Ticks: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(pendingBlock, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Pending Fluid Ticks: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(pendingFluid, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Local Players: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(localPlayers, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Local Entities: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(localEntities, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text(" - ", ThreadedServerHealthDump.LIST, TextDecoration.BOLD))
            .append(Component.text("Ticking Chunks: ", ThreadedServerHealthDump.PRIMARY))
            .append(Component.text(ServerRegions.getTickData(this.world).lastTickingChunksCount, ThreadedServerHealthDump.INFORMATION))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text("Tile Entities", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(this.world.doTileEntityInfo(this.region))
            .append(ThreadedServerHealthDump.NEW_LINE);
        basic.append(Component.text("Entities", ThreadedServerHealthDump.HEADER, TextDecoration.BOLD))
            .append(ThreadedServerHealthDump.NEW_LINE)
            .append(this.world.doEntityInfo(this.region))
            .append(ThreadedServerHealthDump.NEW_LINE);
        return basic.build();
    }

    @Override
    public boolean runTasks(final BooleanSupplier canContinue) {
        MultiWatchdogThread.RunningTick watchdogEntry = new MultiWatchdogThread.RunningTick(Util.getNanos(), this, Thread.currentThread());
        try {
            MultiWatchdogThread.WATCHDOG.dock(watchdogEntry);
            TickScheduler.setTickingData(this.region.getData().tickData);
            super.runTasks(canContinue);
            return runRegionTasks(canContinue);
        } finally {
            TickScheduler.setTickingData(null);
            MultiWatchdogThread.WATCHDOG.undock(watchdogEntry);
        }
    }

    @Override
    public void retire() {
        LEVEL_TO_ID_GENERATOR.computeIfAbsent(this.world.dimension().location(), (_) -> new IdGenerator()).pop(Integer.parseInt(this.identifier.toString().split("region_")[1])); // we split the identifier because the format is 'canvas:region_#'
        super.retire();
    }

    @Override
    public boolean hasTasks() {
        return (super.hasTasks() || this.region.getData().tickData.taskQueueData.hasTasks()) && !this.world.isSleeping();
    }

    public boolean runRegionTasks(final BooleanSupplier canContinue) {
        final RegionizedTaskQueue.RegionTaskQueueData queue = this.region.getData().tickData.taskQueueData;

        boolean processedChunkTask = false;

        boolean executeChunkTask;
        boolean executeTickTask;
        do {
            executeTickTask = queue.executeTickTask();
            executeChunkTask = queue.executeChunkTask();

            processedChunkTask |= executeChunkTask;
        } while ((executeChunkTask | executeTickTask) && canContinue.getAsBoolean());

        if (processedChunkTask) {
            // if we processed any chunk tasks, try to process ticket level updates for full status changes
            this.world.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
        }
        return true;
    }

    @Override
    public boolean shouldSleep() {
        return false; // this is managed inside the tick handle
    }

    public static class TickHandle implements WrappedTick {
        private final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region;
        private final ServerLevel world;
        public boolean isActive;
        private int ticksSinceLastBlockEventsTickCall;

        @Contract(pure = true)
        public TickHandle(ThreadedRegionizer.@NotNull ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region) {
            this.region = region;
            this.world = region.regioniser.world;
        }

        public final boolean isMarkedAsNonSchedulable() {
            return this.region.getData().tickHandle.cancelled.get();
        }

        protected boolean tryMarkTicking() {
            return this.region.tryMarkTicking(this::isMarkedAsNonSchedulable);
        }

        protected boolean markNotTicking() {
            return this.region.markNotTicking();
        }

        public final void markNonSchedulable() {
            this.region.getData().tickHandle.retire();
            isActive = false;
        }

        @Override
        public boolean blockTick(final @NotNull WrappedTickLoop loop, final @NotNull BooleanSupplier hasTimeLeft, final int tickCount) {
            final ChunkRegion tickHandle = region.getData().tickHandle;
            if (tickHandle.cancelled.get()) {
                return false;
            }
            if (!this.tryMarkTicking()) {
                if (!tickHandle.cancelled.get()) {
                    throw new IllegalStateException("Scheduled region should be acquirable");
                }
                // region was killed
                return false;
            }
            // check world sleep, if the world is sleeping, we must sleep
            if (this.world.isSleeping()) {
                return this.markNotTicking() && !tickHandle.cancelled.get();
            }
            // tick region

            TickScheduler.setTickingData(this.region.getData().tickData);
            if (!MinecraftServer.getServer().isTicking()) {
                while (hasTimeLeft.getAsBoolean()) {
                    tickHandle.runTasks(hasTimeLeft);
                }
                TickScheduler.setTickingData(null);
                return this.markNotTicking() && !tickHandle.cancelled.get();
            }
            isActive = true;
            ServerRegions.WorldTickData data = this.region.getData().tickData;
            data.popTick();
            data.setHandlingTick(true);
            TickRateManager tickRateManager = this.world.tickRateManager();
            tickHandle.runRegionTasks(hasTimeLeft); // run region tasks before ticking just in case
            // bench this block, as technically this is the actual tick
            tickHandle.bench(() -> {
                boolean runsNormally = tickRateManager.runsNormally();
                this.world.tickConnection(data); // tick connection on region
                data.tpsCalculator.doTick();
                this.world.updateLagCompensationTick();
                this.world.regionTick1(data);
                if (runsNormally) {
                    data.incrementRedstoneTime();
                }
                this.world.regionTick2(runsNormally, data);
                final ServerChunkCache chunkSource = this.world.getChunkSource();
                chunkSource.tick(hasTimeLeft, true);
                if (runsNormally) {
                    if (this.ticksSinceLastBlockEventsTickCall++ > Config.INSTANCE.ticksBetweenBlockEvents) {
                        this.world.runBlockEvents();
                        this.ticksSinceLastBlockEventsTickCall = 0;
                    }
                }
                data.setHandlingTick(false);
                this.world.regiontick3(true, runsNormally, data);
                if (this.region.getData().tickHandle.cancelled.get()) {
                    this.isActive = false;
                }
            });
            TickScheduler.setTickingData(null);
            return this.markNotTicking() && !tickHandle.cancelled.get();
        }
    }
}
