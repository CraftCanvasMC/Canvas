package io.canvasmc.canvas.region;

import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.RollingAverage;
import io.canvasmc.canvas.TickTimes;
import io.canvasmc.canvas.scheduler.TickLoopScheduler;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.ThreadedServer;
import io.canvasmc.canvas.server.TickLoopConstantsUtils;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.chunk.LevelChunk;
import org.agrona.collections.ObjectHashSet;
import org.jetbrains.annotations.NotNull;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static io.canvasmc.canvas.scheduler.MultithreadedTickScheduler.TIME_BETWEEN_TICKS;

public class ChunkRegion extends TickLoopScheduler.AbstractTick {
    public final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public final ServerLevel world;
    private int ticksSinceLastBlockEventsTickCall = 0;
    public boolean isActive = false;
    public final RollingAverage tps5s = new RollingAverage(5);
    public final RollingAverage tps10s = new RollingAverage(10);
    public final RollingAverage tps15s = new RollingAverage(15);
    public final RollingAverage tps1m = new RollingAverage(60);
    public final TickTimes tickTimes5s = new TickTimes(100);
    public final TickTimes tickTimes10s = new TickTimes(200);
    public final TickTimes tickTimes15s = new TickTimes(300);
    public final TickTimes tickTimes60s = new TickTimes(1200);
    public boolean lagging = false;
    public int currentTick;
    protected long tickSection = 0;
    public long fullTaskStart = 0L;

    public ChunkRegion(final ThreadedRegionizer.@NotNull ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region) {
        this.region = region;
        this.world = region.regioniser.world;
    }

    private void tickMspt(long start) {
        long totalProcessNanos = Util.getNanos() - start;
        this.tickTimes5s.add(this.currentTick, totalProcessNanos);
        this.tickTimes10s.add(this.currentTick, totalProcessNanos);
        this.tickTimes15s.add(this.currentTick, totalProcessNanos);
        this.tickTimes60s.add(this.currentTick, totalProcessNanos);
    }

    private void tickTps(long start) {
        if (++currentTick % MinecraftServer.SAMPLE_INTERVAL == 0) {
            final long diff = start - tickSection;
            final java.math.BigDecimal currentTps = MinecraftServer.TPS_BASE.divide(new java.math.BigDecimal(diff), 30, java.math.RoundingMode.HALF_UP);
            tps5s.add(currentTps, diff);
            tps1m.add(currentTps, diff);
            tps15s.add(currentTps, diff);
            tps10s.add(currentTps, diff);

            lagging = tps5s.getAverage() < org.purpurmc.purpur.PurpurConfig.laggingThreshold;
            tickSection = start;
        }
    }

    public boolean blockRegionTick() {
        if (cancelled.get()) {
            return false;
        }
        if (!this.tryMarkTicking()) {
            if (!this.cancelled.get()) {
                throw new IllegalStateException("Scheduled region should be acquirable");
            }
            // region was killed
            return false;
        }
        // tick region
        try {
            TickLoopScheduler.setTickingData(this.region.getData().tickData);
            isActive = true;
            long nanosecondsOverload = this.world.tickRateManager().nanosecondsPerTick();
            boolean doesntHaveTime = nanosecondsOverload == 0L;
            try {
                final BooleanSupplier hasTimeLeft = doesntHaveTime ? () -> false : this::haveTime;
                try {
                    this.tick(hasTimeLeft);
                } finally {
                    this.tickMspt(this.fullTaskStart);
                    this.tickTps(this.fullTaskStart);
                }
                // run region tasks if we still have time, not part of the tick so don't include in mspt/tps calculations
                this.runRegionTasks(hasTimeLeft);
            } catch (Exception e) {
                TickLoopConstantsUtils.hardCrashCatch(new RegionCrash(e, this));
            }
            TickLoopScheduler.setTickingData(null);
        } catch (Throwable throwable) {
            ThreadedServer.LOGGER.error("Unable to tick region surrounding {}", this.region.getCenterChunk(), throwable);
            throw new RuntimeException(throwable);
        }
        return this.markNotTicking() && !this.cancelled.get();
    }

    // copied and modified from io.canvasmc.canvas.server.AbstractTickLoop
    protected boolean haveTime() {
        return world.server.forceTicks || Util.getNanos() < (this.tickStart + TIME_BETWEEN_TICKS/*equivalent to nextTickTimeNanos*/);
    }

    private void tick(BooleanSupplier hasTimeLeft) {
        ServerRegions.WorldTickData data = ServerRegions.getTickData(this.world);
        data.popTick();
        data.setHandlingTick(true);
        this.fullTaskStart = Util.getNanos();
        ProfilerFiller profilerFiller = Profiler.get();
        TickRateManager tickRateManager = this.world.tickRateManager();
        boolean runsNormally = tickRateManager.runsNormally();
        this.world.tickConnection(data); // tick connection on region
        data.tpsCalculator.doTick();
        this.world.updateLagCompensationTick();
        this.world.regionTick1(data);
        if (runsNormally) {
            data.incrementRedstoneTime();
        }
        this.world.regionTick2(profilerFiller, runsNormally, data);
        this.world.getChunkSource().tick(hasTimeLeft, true);
        if (runsNormally) {
            if (this.ticksSinceLastBlockEventsTickCall++ > Config.INSTANCE.ticksBetweenBlockEvents) {
                if (!Config.INSTANCE.ticking.enableThreadedRegionizing) this.world.runBlockEvents();
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
        if (this.cancelled.get()) {
            this.isActive = false;
        }
    }

    protected boolean runRegionTasks(final BooleanSupplier canContinue) {
        final RegionizedTaskQueue.RegionTaskQueueData queue = this.region.getData().tickData.taskQueueData;

        boolean processedChunkTask = false;

        boolean executeChunkTask = true;
        boolean executeTickTask = true;
        do {
            if (executeTickTask) {
                executeTickTask = queue.executeTickTask();
            }
            if (executeChunkTask) {
                processedChunkTask |= (executeChunkTask = queue.executeChunkTask());
            }
        } while ((executeChunkTask | executeTickTask) && canContinue.getAsBoolean());

        if (processedChunkTask) {
            // if we processed any chunk tasks, try to process ticket level updates for full status changes
            this.world.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
        }
        return true;
    }

    public final boolean isMarkedAsNonSchedulable() {
        return this.cancelled.get();
    }

    protected boolean tryMarkTicking() {
        return this.region.tryMarkTicking(this::isMarkedAsNonSchedulable);
    }

    protected boolean markNotTicking() {
        return this.region.markNotTicking();
    }

    public void prepareSchedule() {
        setScheduledStart(System.nanoTime() + TIME_BETWEEN_TICKS);
    }

    public final void markNonSchedulable() {
        this.cancelled.set(true);
        isActive = false;
    }

    @Override
    public boolean blockTick() {
        return blockRegionTick();
    }

    @Override
    public String toString() {
        return "Region at " + this.world.location() + " surrounding chunk " + this.region.getCenterChunk() + " " + super.toString();
    }
}
