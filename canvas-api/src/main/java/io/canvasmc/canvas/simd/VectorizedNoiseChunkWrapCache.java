package io.canvasmc.canvas.simd;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

public final class VectorizedNoiseChunkWrapCache {

    private static final VectorSpecies<Integer> PAIR_SPEC = IntVector.SPECIES_64;
    private static final ThreadLocal<CacheState> STATE = ThreadLocal.withInitial(CacheState::new);

    private VectorizedNoiseChunkWrapCache() {
    }

    public static Object getCached(final Object owner, final Object function) {
        final CacheState state = STATE.get();
        return state.get(owner, function);
    }

    public static void cache(final Object owner, final Object function, final Object wrapped) {
        final CacheState state = STATE.get();
        state.put(owner, function, wrapped);
    }

    private static final class CacheState {
        private final int[] ownerHashes = new int[PAIR_SPEC.length()];
        private final int[] functionHashes = new int[PAIR_SPEC.length()];
        private final Object[] owners = new Object[PAIR_SPEC.length()];
        private final Object[] functions = new Object[PAIR_SPEC.length()];
        private final Object[] wrapped = new Object[PAIR_SPEC.length()];

        private int nextSlot;

        private Object get(final Object owner, final Object function) {
            if (owners[0] == null && owners[1] == null) {
                return null;
            }

            final int ownerHash = System.identityHashCode(owner);
            final int functionHash = System.identityHashCode(function);

            final IntVector ownerVec = IntVector.fromArray(PAIR_SPEC, ownerHashes, 0);
            final IntVector functionVec = IntVector.fromArray(PAIR_SPEC, functionHashes, 0);
            final VectorMask<Integer> mask = ownerVec.eq(ownerHash).and(functionVec.eq(functionHash));
            if (!mask.anyTrue()) {
                return null;
            }

            final int first = mask.firstTrue();
            if (owners[first] == owner && functions[first] == function) {
                return wrapped[first];
            }

            final int second = first ^ 1;
            if (mask.laneIsSet(second) && owners[second] == owner && functions[second] == function) {
                return wrapped[second];
            }
            return null;
        }

        private void put(final Object owner, final Object function, final Object wrappedValue) {
            final int slot = nextSlot;
            owners[slot] = owner;
            functions[slot] = function;
            wrapped[slot] = wrappedValue;
            ownerHashes[slot] = System.identityHashCode(owner);
            functionHashes[slot] = System.identityHashCode(function);
            nextSlot = slot ^ 1;
        }
    }
}
