package io.canvasmc.amethyst.attribution;

import io.canvasmc.amethyst.proto.SampleSet;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * This is the intervals for what a GAME thread runs over time, so samples can be matched to handles by thread id and
 * timestamps. The game thread writes, and the profiler thread takes a view and prunes
 *
 * @author dueris
 */
public final class ThreadIntervals {
    private final long threadId;

    // from times[i] on, the thread runs handles[i] in phases[i], end() adds an idle entry
    private final LongArrayList times = new LongArrayList();
    private final LongArrayList handles = new LongArrayList();
    private final ByteArrayList phases = new ByteArrayList();

    public ThreadIntervals(final long threadId) {
        this.threadId = threadId;
    }

    public synchronized void begin(final long nanoTime, final long handle, final SampleSet.Phase phase) {
        add(nanoTime, handle, phase.getNumber());
    }

    public synchronized void end(final long nanoTime) {
        add(nanoTime, -1L, SampleSet.Phase.PHASE_NONE_VALUE);
    }

    private void add(final long nanoTime, final long handle, final int phase) {
        times.add(nanoTime);
        handles.add(handle);
        phases.add((byte) phase);
    }

    public synchronized View view() {
        return new View(threadId, times.toLongArray(), handles.toLongArray(), phases.toByteArray());
    }

    // drops what no sample at or after the time needs, the entry in effect at that time stays
    public synchronized void prune(final long nanoTime) {
        int drop = 0;
        while (drop + 1 < times.size() && times.getLong(drop + 1) <= nanoTime) {
            drop++;
        }

        // remove everything from the first idx <-> the drop idx
        times.removeElements(0, drop);
        handles.removeElements(0, drop);
        phases.removeElements(0, drop);
    }

    // representation of a copy of a thread entries sorted by time
    public record View(long threadId, long[] times, long[] handles, byte[] phases) {
        public static final View EMPTY = new View(-1L, new long[0], new long[0], new byte[0]);

        /// @return the handle idx at the time
        public int find(final long nanoTime) {
            int low = 0;
            int high = times.length - 1;
            while (low <= high) {
                final int middle = (low + high) >>> 1;
                if (times[middle] <= nanoTime) {
                    low = middle + 1;
                }
                else {
                    high = middle - 1;
                }
            }
            return high;
        }

        /// @return the handle of an entry from the [#find] index, `-1` for none
        public long handle(final int entry) {
            return entry < 0 ? -1L : handles[entry];
        }

        /// @return the phase of an entry from the [#find] index
        public int phase(final int entry) {
            return entry < 0 ? SampleSet.Phase.PHASE_NONE_VALUE : phases[entry];
        }
    }
}
