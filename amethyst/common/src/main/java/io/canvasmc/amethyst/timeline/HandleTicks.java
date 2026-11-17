package io.canvasmc.amethyst.timeline;

import io.canvasmc.amethyst.ProfileClock;
import io.canvasmc.amethyst.proto.Handle;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

public final class HandleTicks {
    private final ProfileClock clock;
    private final long id;
    private final int world;
    private final long firstSeenNanos;

    private final LongArrayList starts = new LongArrayList();
    private final LongArrayList durations = new LongArrayList();
    private final LongArrayList tasksNanos = new LongArrayList();
    private final IntArrayList tasksRuns = new IntArrayList();

    private long lastSeenNanos;
    private boolean ticked;
    private boolean finished;
    private int rowsWritten;

    private long openStart;
    private long openDuration;

    private long pendingNanos;
    private int pendingRuns;
    private long leadingNanos;
    private int leadingRuns;

    HandleTicks(final ProfileClock clock, final long id, final int world, final long nanoTime) {
        this.clock = clock;
        this.id = id;
        this.world = world;
        this.firstSeenNanos = nanoTime;
        this.lastSeenNanos = nanoTime;
    }

    public long id() {
        return id;
    }

    // TODO - call on runTick
    public synchronized void tickStart(final long nanoTime) {
        if (ticked) {
            close();
        }
        else {
            ticked = true;
            leadingNanos = pendingNanos;
            leadingRuns = pendingRuns;
        }

        pendingNanos = 0L;
        pendingRuns = 0;
        openStart = nanoTime;
        openDuration = 0L;
    }

    // TODO - call on runTick
    public synchronized void tickEnd(final long nanoTime) {
        openDuration = nanoTime - openStart;
        lastSeenNanos = nanoTime;
    }

    // TODO - call on runTasks
    public synchronized void tasks(final long startNanoTime, final long endNanoTime) {
        pendingNanos += endNanoTime - startNanoTime;
        pendingRuns++;
        lastSeenNanos = endNanoTime;
    }

    // note: only on handle death
    synchronized void finish(final long nanoTime) {
        finished = true;
        lastSeenNanos = Math.max(lastSeenNanos, nanoTime);
    }

    synchronized boolean finished() {
        return finished;
    }

    // the ticks closed since the last call, plus the open tick once finished
    synchronized Handle write() {
        if (finished && ticked) {
            close();
            ticked = false;
        }

        final Handle.Builder out = Handle.newBuilder()
            .setId(id)
            .setKind(id == 0L ? Handle.Kind.KIND_GLOBAL : Handle.Kind.KIND_REGION)
            .setWorld(world)
            .setFirstSeenUs(clock.sinceStartUs(firstSeenNanos))
            .setLastSeenUs(clock.sinceStartUs(lastSeenNanos));

        long previous = 0L;
        for (int i = 0; i < starts.size(); i++) {
            final long start = clock.sinceStartUs(starts.getLong(i));
            out.addTickStartUsDelta(start - previous)
                .addTickDurationUs(durations.getLong(i) / 1000L)
                .addTasksNs(tasksNanos.getLong(i))
                .addTasksRuns(tasksRuns.getInt(i));
            previous = start;
        }

        if (rowsWritten == 0 && !starts.isEmpty()) {
            out.setLeadingTasksNs(leadingNanos).setLeadingTasksRuns(leadingRuns);
        }

        rowsWritten += starts.size();
        starts.clear();
        durations.clear();
        tasksNanos.clear();
        tasksRuns.clear();

        return out.build();
    }

    private void close() {
        starts.add(openStart);
        durations.add(openDuration);
        tasksNanos.add(pendingNanos);
        tasksRuns.add(pendingRuns);
        pendingNanos = 0L;
        pendingRuns = 0;
    }
}
