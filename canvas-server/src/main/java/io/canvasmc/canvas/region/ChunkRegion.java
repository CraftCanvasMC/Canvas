package io.canvasmc.canvas.region;

import ca.spottedleaf.concurrentutil.util.Priority;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import io.canvasmc.canvas.server.MultiWatchdogThread;
import io.canvasmc.canvas.server.ThreadedServer;
import io.canvasmc.canvas.server.TickLoopConstantsUtils;
import io.canvasmc.canvas.util.IdGenerator;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ChunkRegion extends TickScheduler.FullTick<ChunkRegion.TickHandle> {
    private static final IdGenerator ID_GENERATOR = new IdGenerator();
    public static final Supplier<ResourceLocation> IDENTIFIER_GENERATOR = () -> {
        int id = ID_GENERATOR.poll();
        return ResourceLocation.fromNamespaceAndPath("canvas", "region_" + id);
    };
    public final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region;
    public final ServerLevel world;

    public ChunkRegion(final ThreadedRegionizer.@NotNull ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region, final DedicatedServer server) {
        super(server, IDENTIFIER_GENERATOR.get(), new TickHandle(region));
        this.region = region;
        this.world = region.regioniser.world;
    }

    @Override
    public String toString() {
        return "Region at " + this.world.getDebugLocation() + " surrounding chunk " + this.region.getCenterChunk() + " " + super.toString();
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
        ID_GENERATOR.pop(Integer.parseInt(this.identifier.toString().split("_")[1])); // we split the identifier because the format is 'canvas:region_#'
        super.retire();
    }

    @Override
    public boolean hasTasks() {
        return super.hasTasks() || this.region.getData().tickData.taskQueueData.hasTasks();
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
        return false; // TODO - implement
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
        public boolean blockTick(final WrappedTickLoop loop, final BooleanSupplier hasTimeLeft, final int tickCount) {
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
            // tick region
            try {
                TickScheduler.setTickingData(this.region.getData().tickData);
                if (!MinecraftServer.getServer().isTicking()) {
                    while (hasTimeLeft.getAsBoolean()) {
                        tickHandle.runTasks(hasTimeLeft);
                    }
                    TickScheduler.setTickingData(null);
                    return this.markNotTicking() && !tickHandle.cancelled.get();
                }
                isActive = true;
                try {
                    ServerRegions.WorldTickData data = this.region.getData().tickData;
                    data.popTick();
                    data.setHandlingTick(true);
                    ProfilerFiller profilerFiller = Profiler.get();
                    TickRateManager tickRateManager = this.world.tickRateManager();
                    // run all BLOCKING tasks if we have any
                    final RegionizedTaskQueue.RegionTaskQueueData queue = data.taskQueueData;

                    boolean processedChunkTask = false;

                    boolean executeChunkTask;
                    boolean executeTickTask;
                    do {
                        executeTickTask = queue.executeTickTask(Priority.BLOCKING);
                        executeChunkTask = queue.executeChunkTask();

                        processedChunkTask |= executeChunkTask;
                    } while ((executeChunkTask | executeTickTask));

                    // if we processed any chunk tasks, try to process ticket level updates for full status changes
                    this.world.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
                    boolean runsNormally = tickRateManager.runsNormally();
                    this.world.tickConnection(data); // tick connection on region
                    data.tpsCalculator.doTick();
                    this.world.updateLagCompensationTick();
                    this.world.regionTick1(data);
                    if (runsNormally) {
                        data.incrementRedstoneTime();
                    }
                    this.world.regionTick2(profilerFiller, runsNormally, data);
                    final ServerChunkCache chunkSource = this.world.getChunkSource();
                    chunkSource.tick(hasTimeLeft, true);
                    if (runsNormally) {
                        if (this.ticksSinceLastBlockEventsTickCall++ > Config.INSTANCE.ticksBetweenBlockEvents) {
                            this.world.runBlockEvents();
                            this.ticksSinceLastBlockEventsTickCall = 0;
                        }
                    }
                    data.setHandlingTick(false);
                    this.world.regiontick3(profilerFiller, true, runsNormally, data);
                    ServerRegions.getTickData(this.world).explosionDensityCache.clear();
                    for (final ServerPlayer localPlayer : data.getLocalPlayers()) {
                        localPlayer.connection.chunkSender.sendNextChunks(localPlayer);
                        localPlayer.connection.resumeFlushing();
                    }
                    if (this.region.getData().tickHandle.cancelled.get()) {
                        this.isActive = false;
                    }
                } catch (Exception exception) {
                    TickLoopConstantsUtils.hardCrashCatch(new RegionCrash(exception, tickHandle));
                }
                TickScheduler.setTickingData(null);
            } catch (Throwable throwable) {
                ThreadedServer.LOGGER.error("Unable to tick region surrounding {}", this.region.getCenterChunk(), throwable);
                throw new RuntimeException(throwable);
            }
            return this.markNotTicking() && !tickHandle.cancelled.get();
        }
    }
}
