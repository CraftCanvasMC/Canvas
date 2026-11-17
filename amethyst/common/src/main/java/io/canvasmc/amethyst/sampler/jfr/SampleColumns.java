package io.canvasmc.amethyst.sampler.jfr;

import io.canvasmc.amethyst.proto.SampleSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;

// one kind of sample, one column per field, until the part is written
final class SampleColumns {

    private final LongArrayList times = new LongArrayList();
    private final IntArrayList stacks = new IntArrayList();
    private final IntArrayList threads = new IntArrayList();
    private final LongArrayList handles = new LongArrayList();
    private final IntArrayList phases = new IntArrayList();
    private final LongArrayList values = new LongArrayList();
    private final IntArrayList classNames = new IntArrayList();

    private long lastTimeUs;

    void add(
        final long timeUs,
        final int stack,
        final int thread,
        final long handle,
        final int phase,
        final long value,
        final int className
    ) {
        times.add(timeUs);
        stacks.add(stack);
        threads.add(thread);
        handles.add(handle);
        phases.add(phase);
        values.add(value);
        classNames.add(className);
    }

    // JFR isnt sorted by time, so we need to sort by time, then clear
    SampleSet write(final boolean enabled) {
        final SampleSet.Builder out = SampleSet.newBuilder().setEnabled(enabled);
        final int size = times.size();
        final long[] timeArray = times.elements();
        final int[] order = new int[size];

        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        LongArrays.radixSortIndirect(order, timeArray, 0, size, true);

        for (final int i : order) {
            out
                .addTimeUsDelta(timeArray[i] - lastTimeUs)
                .addStack(stacks.getInt(i))
                .addThread(threads.getInt(i))
                .addHandle(handles.getLong(i))
                .addPhaseValue(phases.getInt(i))
                .addValue(values.getLong(i))
                .addClassName(classNames.getInt(i));
            lastTimeUs = timeArray[i];
        }

        times.clear();
        stacks.clear();
        threads.clear();
        handles.clear();
        phases.clear();
        values.clear();
        classNames.clear();

        return out.build();
    }
}
