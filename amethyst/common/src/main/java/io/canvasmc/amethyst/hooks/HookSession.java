package io.canvasmc.amethyst.hooks;

import io.canvasmc.amethyst.attribution.ThreadIntervals;
import io.canvasmc.amethyst.proto.SampleSet;
import io.canvasmc.amethyst.sampler.Sampler;
import io.canvasmc.amethyst.timeline.TimelineRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

public final class HookSession {
    private final TimelineRecorder timeline;

    @Nullable
    private final Sampler sampler;

    private final List<ThreadIntervals> threads = new CopyOnWriteArrayList<>();
    private final ThreadLocal<ThreadIntervals> intervals = ThreadLocal.withInitial(this::register);

    public HookSession(
        final TimelineRecorder timeline,
        @Nullable
        final Sampler sampler
    ) {
        this.timeline = timeline;
        this.sampler = sampler;
    }

    public TimelineRecorder timeline() {
        return timeline;
    }

    public List<ThreadIntervals> threads() {
        return threads;
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
