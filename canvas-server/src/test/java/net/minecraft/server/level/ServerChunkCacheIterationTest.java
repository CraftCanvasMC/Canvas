package net.minecraft.server.level;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerChunkCacheIterationTest {

    @Test
    void stableSnapshotTicksEveryEntryAndPollsEveryEightIndices() {
        final ReferenceList<Object> chunks = chunks(17);
        final Object[] raw = chunks.getRawDataUnchecked();
        final int size = chunks.size();
        final List<Object> ticked = new ArrayList<>();
        final List<Integer> polledIndices = new ArrayList<>();

        ServerChunkCache.iterateTickingChunks(raw, size, ticked::add, polledIndices::add);

        assertEquals(Arrays.asList(raw).subList(0, size), ticked);
        assertEquals(List.of(0, 8, 16), polledIndices);
    }

    @Test
    void removalsDuringPollingSkipVacatedTailSlots() {
        final ReferenceList<Object> chunks = chunks(12);
        final Object[] initial = chunks.getRawDataUnchecked().clone();
        final Object[] raw = chunks.getRawDataUnchecked();
        final int size = chunks.size();
        final List<Object> ticked = new ArrayList<>();
        final List<Integer> polledIndices = new ArrayList<>();

        ServerChunkCache.iterateTickingChunks(raw, size, chunk -> {
            assertNotNull(chunk);
            ticked.add(chunk);
        }, index -> {
            polledIndices.add(index);
            if (index == 0) {
                chunks.remove(initial[1]);
                chunks.remove(initial[2]);
            }
        });

        assertEquals(List.of(initial[0], initial[11], initial[10], initial[3], initial[4], initial[5], initial[6], initial[7], initial[8], initial[9]), ticked);
        assertEquals(List.of(0, 8), polledIndices);
    }

    @Test
    void entriesAppendedAfterSnapshotAreDeferred() {
        final ReferenceList<Object> chunks = chunks(10);
        final Object[] raw = chunks.getRawDataUnchecked();
        final int size = chunks.size();
        final List<Object> ticked = new ArrayList<>();
        final Object appended = new Object();

        ServerChunkCache.iterateTickingChunks(raw, size, ticked::add, index -> {
            if (index == 0) {
                chunks.add(appended);
            }
        });

        assertEquals(Arrays.asList(raw).subList(0, size), ticked);
    }

    @Test
    void consecutiveNullTailSlotsStillPollAtOriginalIndices() {
        final ReferenceList<Object> chunks = chunks(17);
        final Object[] initial = chunks.getRawDataUnchecked().clone();
        final Object[] raw = chunks.getRawDataUnchecked();
        final int size = chunks.size();
        final List<Object> ticked = new ArrayList<>();
        final List<Integer> polledIndices = new ArrayList<>();

        ServerChunkCache.iterateTickingChunks(raw, size, chunk -> {
            assertNotNull(chunk);
            ticked.add(chunk);
        }, index -> {
            polledIndices.add(index);
            if (index == 0) {
                for (int i = 1; i < size; ++i) {
                    chunks.remove(initial[i]);
                }
            }
        });

        assertEquals(List.of(initial[0]), ticked);
        assertEquals(List.of(0, 8, 16), polledIndices);
    }

    private static ReferenceList<Object> chunks(final int count) {
        final ReferenceList<Object> chunks = new ReferenceList<>(new Object[0]);
        for (int i = 0; i < count; ++i) {
            chunks.add(new Object());
        }
        return chunks;
    }
}
