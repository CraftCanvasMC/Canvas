package io.canvasmc.amethyst.hooks;

import io.canvasmc.amethyst.attribution.ThreadIntervals;
import io.canvasmc.amethyst.proto.SampleSet;
import io.canvasmc.amethyst.sampler.Sampler;
import io.canvasmc.amethyst.timeline.TimelineRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

public final class HookSession {
    public static final long DEFAULT_METRIC_INTERVAL_NS = TimeUnit.SECONDS.toNanos(1L);

    // no snapshot taken yet, so the first check is always due
    private static final long NO_SNAPSHOT = Long.MIN_VALUE;

    private final TimelineRecorder timeline;
    private final AtomicLong lastSnapshot = new AtomicLong(NO_SNAPSHOT);
    private final long metricIntervalNs;

    @Nullable
    private final Sampler sampler;

    private final List<ThreadIntervals> threads = new CopyOnWriteArrayList<>();
    private final ThreadLocal<ThreadIntervals> intervals = ThreadLocal.withInitial(this::register);

    public HookSession(
        final TimelineRecorder timeline,
        @Nullable
        final Sampler sampler
    ) {
        this(timeline, sampler, DEFAULT_METRIC_INTERVAL_NS);
    }

    public HookSession(
        final TimelineRecorder timeline,
        @Nullable
        final Sampler sampler,
        final long metricIntervalNs
    ) {
        if (metricIntervalNs <= 0L) {
            throw new IllegalArgumentException("The metric interval must be positive, got " + metricIntervalNs);
        }

        this.timeline = timeline;
        this.sampler = sampler;
        this.metricIntervalNs = metricIntervalNs;
    }

    public TimelineRecorder timeline() {
        return timeline;
    }

    public List<ThreadIntervals> threads() {
        return threads;
    }

    public long metricIntervalNs() {
        return metricIntervalNs;
    }

    public boolean snapshotDue(final long nanoTime) {
        final long last = lastSnapshot.get();
        if (last != NO_SNAPSHOT && nanoTime - last < metricIntervalNs) {
            return false;
        }

        return lastSnapshot.compareAndSet(last, nanoTime);
    }

    public void tickStart(
        final long handle,
        @Nullable
        final String world,
        final long nanoTime
    ) {
        intervals.get().begin(nanoTime, handle, SampleSet.Phase.PHASE_TICK);
        timeline.ticks(handle, world).tickStart(nanoTime);
    }

    public void tickEnd(
        final long handle,
        @Nullable
        final String world,
        final long nanoTime
    ) {
        intervals.get().end(nanoTime);
        timeline.ticks(handle, world).tickEnd(nanoTime);
    }

    public void tasksStart(final long handle, final long nanoTime) {
        intervals.get().begin(nanoTime, handle, SampleSet.Phase.PHASE_TASKS);
    }

    public void tasksEnd(
        final long handle,
        @Nullable
        final String world,
        final long startNanoTime,
        final long endNanoTime
    ) {
        intervals.get().end(endNanoTime);
        timeline.ticks(handle, world).tasks(startNanoTime, endNanoTime);
    }

    private ThreadIntervals register() {
        final Thread thread = Thread.currentThread();
        final ThreadIntervals out = new ThreadIntervals(thread.threadId());

        threads.add(out);
        if (sampler != null) {
            sampler.addGameThread(thread);
        }

        return out;
    }
}
