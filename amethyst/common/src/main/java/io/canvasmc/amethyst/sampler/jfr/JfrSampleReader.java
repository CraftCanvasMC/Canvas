package io.canvasmc.amethyst.sampler.jfr;

import io.canvasmc.amethyst.AmethystUtils;
import io.canvasmc.amethyst.ProfileClock;
import io.canvasmc.amethyst.attribution.ThreadIntervals;
import io.canvasmc.amethyst.proto.Samples;
import io.canvasmc.amethyst.proto.ThreadInfo;
import io.canvasmc.amethyst.source.SourceResolver;
import io.canvasmc.amethyst.source.SourceTable;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import one.jfr.JfrReader;
import one.jfr.event.AllocationSample;
import one.jfr.event.ContendedLock;
import one.jfr.event.Event;
import one.jfr.event.ExecutionSample;

public final class JfrSampleReader {

    private static final long MONOTONIC_TOLERANCE = AmethystUtils.ONE_S_NANOS;
    private static final String THREAD_STATES = "jdk.types.ThreadState";
    private static final String DEFAULT_STATE = "STATE_DEFAULT";

    private final ProfileClock clock;
    private final long cpuIntervalNanos;
    private final long wallIntervalNanos;

    // added to from any thread while running
    private final Set<Long> gameThreads = ConcurrentHashMap.newKeySet();
    private final StackTree stacks;
    private final SourceTable sources;
    private final Int2IntOpenHashMap threadIndex = new Int2IntOpenHashMap();
    private final List<ThreadInfo> threads = new ObjectArrayList<>();
    private final SampleColumns cpu = new SampleColumns();
    private final SampleColumns wall = new SampleColumns();
    private final SampleColumns allocation = new SampleColumns();
    private final SampleColumns lock = new SampleColumns();

    // per read
    private final Long2ObjectOpenHashMap<ThreadIntervals.View> intervals = new Long2ObjectOpenHashMap<>();

    private int writtenThreads;
    private LongPredicate keepHandle = (_) -> true;
    private long chunk = Long.MIN_VALUE;
    private boolean monotonic;
    private int defaultState;

    public JfrSampleReader(
        final ProfileClock clock,
        final long cpuIntervalNanos,
        final long wallIntervalNanos,
        final SourceResolver sources
    ) {
        this.clock = clock;
        this.cpuIntervalNanos = cpuIntervalNanos;
        this.wallIntervalNanos = wallIntervalNanos;
        this.sources = new SourceTable(sources);
        this.stacks = new StackTree(this.sources);
        this.threadIndex.defaultReturnValue(-1);
        this.intervals.defaultReturnValue(ThreadIntervals.View.EMPTY);
    }

    public void addGameThread(final long threadId) {
        gameThreads.add(threadId);
    }

    public void read(
        final Path jfr,
        final Collection<ThreadIntervals.View> intervals,
        final LongPredicate keepHandle
    ) throws IOException {
        this.intervals.clear();

        for (final ThreadIntervals.View thread : intervals) {
            this.intervals.put(thread.threadId(), thread);
            // a thread that ran a handle HAS TO BE a game thread
            this.gameThreads.add(thread.threadId());
        }

        this.keepHandle = keepHandle;
        this.chunk = Long.MIN_VALUE;

        try (final JfrReader reader = new JfrReader(jfr.toString())) {
            Event event;
            while ((event = reader.readEvent()) != null) {
                if (reader.chunkStartNanos != chunk) {
                    newChunk(reader);
                }

                switch (event) {
                    // the reader hides which event a sample came from, but only wall samples carry a thread
                    // state, cpu samples are always the default one
                    case ExecutionSample sample when sample.threadState != defaultState ->
                        record(reader, wall, sample, sample.samples() * wallIntervalNanos, -1);
                    case ExecutionSample sample ->
                        record(reader, cpu, sample, sample.samples() * cpuIntervalNanos, -1);
                    case AllocationSample sample ->
                        record(reader, allocation, sample, sample.value(), stacks.className(reader, sample.classId));
                    case ContendedLock sample -> {
                        final long waited = (long) (sample.duration * reader.nanosPerTick);
                        record(reader, lock, sample, waited, stacks.className(reader, sample.classId));
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private void newChunk(final JfrReader reader) {
        chunk = reader.chunkStartNanos;
        stacks.newChunk();
        defaultState = defaultState(reader);

        // with clock=monotonic the raw ticks are System.nanoTime(), but some platforms' monotonic clock isn't the
        // jvms, so check before trusting it because u can never be too sure ig

        final long expected = clock.startNano() + (reader.chunkStartNanos - clock.startEpochNanos());
        monotonic =
            reader.ticksPerSec == AmethystUtils.ONE_S_NANOS
            && Math.abs(reader.chunkStartTicks - expected) < MONOTONIC_TOLERANCE;
    }

    private void record(
        final JfrReader reader,
        final SampleColumns into,
        final Event event,
        final long value,
        final int className
    ) {
        // wall clock fallback, less precise but works everywhere so whatever
        final long nanoTime = monotonic
            ? event.time
            : clock.startNano() + reader.eventTimeToNanos(event.time) - clock.startEpochNanos();
        final int thread = thread(reader, event.tid);
        final ThreadIntervals.View ran = intervals.get(threads.get(thread).getJavaId());
        final int entry = ran.find(nanoTime);
        final long handle = ran.handle(entry);
        if (!keepHandle.test(handle)) {
            return;
        }

        into.add(
            clock.sinceStartUs(nanoTime),
            stacks.stack(reader, event.stackTraceId),
            thread,
            handle,
            ran.phase(entry),
            value,
            className
        );
    }

    private int thread(final JfrReader reader, final int tid) {
        final int existing = threadIndex.get(tid);
        if (existing != -1) {
            return existing;
        }

        final Long javaId = reader.javaThreads.get(tid);
        final String name = reader.threads.get(tid);
        final long id = javaId == null ? -1L : javaId;

        threads.add(
            ThreadInfo.newBuilder()
                .setJavaId(id)
                .setNativeId(tid)
                .setName(name == null ? "Thread " + tid : name)
                .setGame(gameThreads.contains(id))
                .build()
        );
        threadIndex.put(tid, threads.size() - 1);

        return threads.size() - 1;
    }

    public Samples write(final boolean cpuEnabled, final boolean allocationEnabled, final boolean lockEnabled) {
        final Samples.Builder out = Samples.newBuilder();
        stacks.write(out);
        out.addAllThreads(threads.subList(writtenThreads, threads.size()));
        writtenThreads = threads.size();
        out.setCpu(cpu.write(cpuEnabled));
        out.setWall(wall.write(true));
        out.setAllocation(allocation.write(allocationEnabled));
        out.setLock(lock.write(lockEnabled));
        sources.write(out);
        return out.build();
    }

    private static int defaultState(final JfrReader reader) {
        final Map<Integer, String> states = reader.enums.get(THREAD_STATES);
        if (states != null) {
            for (final Map.Entry<Integer, String> state : states.entrySet()) {
                if (state.getValue().equals(DEFAULT_STATE)) {
                    return state.getKey();
                }
            }
        }
        return 0;
    }
}
