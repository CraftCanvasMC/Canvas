package io.canvasmc.canvas.world.levelgen;

import java.util.Arrays;

/**
 * Pure-Java implementation of the BLAKE3 cryptographic hash function.
 *
 * <p>Used by Canvas' secure seed protection (Foldenor patch port) as a PRF and KDF
 * to derive unbiased randomness from world coordinates, dimension, salt and counter
 * such that the original 64-bit world seed cannot be recovered from generated chunks.
 *
 * <p>This implementation follows the BLAKE3 reference specification
 * (<a href="https://github.com/BLAKE3-team/BLAKE3-specs">BLAKE3-specs</a>).
 * It is not optimized for raw throughput on huge inputs, but it is correct
 * and constant-time in branching for fixed-size inputs, which is what the
 * world generator uses (small headers per coordinate / counter request).
 */
public final class Blake3 {

    public static final int KEY_LEN = 32;
    public static final int OUT_LEN = 32;
    public static final int BLOCK_LEN = 64;
    public static final int CHUNK_LEN = 1024;

    private static final int CHUNK_START = 1 << 0;
    private static final int CHUNK_END = 1 << 1;
    private static final int PARENT = 1 << 2;
    private static final int ROOT = 1 << 3;
    private static final int KEYED_HASH = 1 << 4;
    private static final int DERIVE_KEY_CONTEXT = 1 << 5;
    private static final int DERIVE_KEY_MATERIAL = 1 << 6;

    private static final int[] IV = {
        0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
        0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
    };

    private static final int[] MSG_PERMUTATION = {
        2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8
    };

    private Blake3() {
    }

    private static void g(int[] state, int a, int b, int c, int d, int mx, int my) {
        state[a] = state[a] + state[b] + mx;
        state[d] = Integer.rotateRight(state[d] ^ state[a], 16);
        state[c] = state[c] + state[d];
        state[b] = Integer.rotateRight(state[b] ^ state[c], 12);
        state[a] = state[a] + state[b] + my;
        state[d] = Integer.rotateRight(state[d] ^ state[a], 8);
        state[c] = state[c] + state[d];
        state[b] = Integer.rotateRight(state[b] ^ state[c], 7);
    }

    private static void round(int[] state, int[] m) {
        g(state, 0, 4, 8, 12, m[0], m[1]);
        g(state, 1, 5, 9, 13, m[2], m[3]);
        g(state, 2, 6, 10, 14, m[4], m[5]);
        g(state, 3, 7, 11, 15, m[6], m[7]);
        g(state, 0, 5, 10, 15, m[8], m[9]);
        g(state, 1, 6, 11, 12, m[10], m[11]);
        g(state, 2, 7, 8, 13, m[12], m[13]);
        g(state, 3, 4, 9, 14, m[14], m[15]);
    }

    private static void permuteInto(int[] dst, int[] src) {
        for (int i = 0; i < 16; i++) {
            dst[i] = src[MSG_PERMUTATION[i]];
        }
    }

    /**
     * Compresses {@code blockWords} into the supplied chaining value and writes
     * the 16-word output into {@code stateOut}. Caller-owned scratch buffers
     * ({@code stateOut}, {@code blockA}, {@code blockB}) are reused across
     * calls so the hot path is allocation-free. {@code blockA} on entry must
     * already contain the message words.
     */
    private static void compressInto(int[] chainingValue, int[] blockA, int[] blockB,
                                     long counter, int blockLen, int flags, int[] stateOut) {
        stateOut[0] = chainingValue[0];
        stateOut[1] = chainingValue[1];
        stateOut[2] = chainingValue[2];
        stateOut[3] = chainingValue[3];
        stateOut[4] = chainingValue[4];
        stateOut[5] = chainingValue[5];
        stateOut[6] = chainingValue[6];
        stateOut[7] = chainingValue[7];
        stateOut[8] = IV[0];
        stateOut[9] = IV[1];
        stateOut[10] = IV[2];
        stateOut[11] = IV[3];
        stateOut[12] = (int) counter;
        stateOut[13] = (int) (counter >> 32);
        stateOut[14] = blockLen;
        stateOut[15] = flags;

        round(stateOut, blockA);
        permuteInto(blockB, blockA);
        round(stateOut, blockB);
        permuteInto(blockA, blockB);
        round(stateOut, blockA);
        permuteInto(blockB, blockA);
        round(stateOut, blockB);
        permuteInto(blockA, blockB);
        round(stateOut, blockA);
        permuteInto(blockB, blockA);
        round(stateOut, blockB);
        permuteInto(blockA, blockB);
        round(stateOut, blockA);

        for (int i = 0; i < 8; i++) {
            stateOut[i] ^= stateOut[i + 8];
            stateOut[i + 8] ^= chainingValue[i];
        }
    }

    private static int[] compress(int[] chainingValue, int[] blockWords, long counter, int blockLen, int flags) {
        int[] state = new int[16];
        int[] blockA = blockWords.clone();
        int[] blockB = new int[16];
        compressInto(chainingValue, blockA, blockB, counter, blockLen, flags, state);
        return state;
    }

    private static int[] wordsFromBlock(byte[] block, int offset) {
        return wordsFromBytes(block, offset, 16);
    }

    private static int[] wordsFromKey(byte[] key, int offset) {
        return wordsFromBytes(key, offset, 8);
    }

    private static int[] wordsFromBytes(byte[] bytes, int offset, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            int j = offset + i * 4;
            words[i] = (bytes[j] & 0xff)
                | ((bytes[j + 1] & 0xff) << 8)
                | ((bytes[j + 2] & 0xff) << 16)
                | ((bytes[j + 3] & 0xff) << 24);
        }
        return words;
    }

    private static int[] firstEight(int[] state) {
        return Arrays.copyOf(state, 8);
    }

    private static final class ChunkState {
        int[] chainingValue;
        long chunkCounter;
        byte[] block = new byte[BLOCK_LEN];
        int blockLen;
        int blocksCompressed;
        int flags;

        ChunkState(int[] key, long counter, int flags) {
            this.chainingValue = key.clone();
            this.chunkCounter = counter;
            this.flags = flags;
        }

        int len() {
            return BLOCK_LEN * blocksCompressed + blockLen;
        }

        int startFlag() {
            return blocksCompressed == 0 ? CHUNK_START : 0;
        }

        void update(byte[] input, int offset, int length) {
            while (length > 0) {
                if (blockLen == BLOCK_LEN) {
                    int[] words = wordsFromBlock(block, 0);
                    int[] out = compress(chainingValue, words, chunkCounter, BLOCK_LEN, flags | startFlag());
                    chainingValue = firstEight(out);
                    blocksCompressed++;
                    Arrays.fill(block, (byte) 0);
                    blockLen = 0;
                }
                int want = BLOCK_LEN - blockLen;
                int take = Math.min(want, length);
                System.arraycopy(input, offset, block, blockLen, take);
                blockLen += take;
                offset += take;
                length -= take;
            }
        }

        Output output() {
            int[] words = wordsFromBlock(block, 0);
            return new Output(chainingValue, words, chunkCounter, blockLen, flags | startFlag() | CHUNK_END);
        }
    }

    private static int[] parentOutputWords(int[] leftCv, int[] rightCv, int[] key, int flags) {
        int[] block = new int[16];
        System.arraycopy(leftCv, 0, block, 0, 8);
        System.arraycopy(rightCv, 0, block, 8, 8);
        return compress(key, block, 0L, BLOCK_LEN, PARENT | flags);
    }

    private static Output parentOutput(int[] leftCv, int[] rightCv, int[] key, int flags) {
        int[] block = new int[16];
        System.arraycopy(leftCv, 0, block, 0, 8);
        System.arraycopy(rightCv, 0, block, 8, 8);
        return new Output(key, block, 0L, BLOCK_LEN, PARENT | flags);
    }

    private static final class Output {
        final int[] inputCv;
        final int[] blockWords;
        final long counter;
        final int blockLen;
        final int flags;

        Output(int[] inputCv, int[] blockWords, long counter, int blockLen, int flags) {
            this.inputCv = inputCv;
            this.blockWords = blockWords;
            this.counter = counter;
            this.blockLen = blockLen;
            this.flags = flags;
        }

        int[] chainingValue() {
            return firstEight(compress(inputCv, blockWords, counter, blockLen, flags));
        }

        void rootBytes(byte[] out, int offset, int length) {
            long blockCounter = 0;
            while (length > 0) {
                int[] words = compress(inputCv, blockWords, blockCounter, blockLen, flags | ROOT);
                int take = Math.min(length, 64);
                for (int i = 0; i < take; i++) {
                    out[offset + i] = (byte) (words[i >> 2] >>> (8 * (i & 3)));
                }
                offset += take;
                length -= take;
                blockCounter++;
            }
        }
    }

    /**
     * Streaming hasher. Reusable via {@link #reset(byte[])} for a hot path that
     * derives many random words from the same secret key.
     */
    public static final class Hasher {
        private static final int MAX_DEPTH = 54;
        private final int[] key;
        private final int flags;
        private ChunkState chunkState;
        private final int[][] cvStack = new int[MAX_DEPTH][];
        private int cvStackLen;

        private Hasher(int[] key, int flags) {
            this.key = key.clone();
            this.flags = flags;
            this.chunkState = new ChunkState(key, 0L, flags);
        }

        /**
         * Resets this hasher so the same instance can be reused with the supplied
         * 32-byte key and the original flags. Saves allocation on hot paths.
         */
        public void reset(byte[] key32) {
            if (key32.length != KEY_LEN) {
                throw new IllegalArgumentException("Key must be " + KEY_LEN + " bytes");
            }
            int[] keyWords = wordsFromKey(key32, 0);
            System.arraycopy(keyWords, 0, this.key, 0, 8);
            this.chunkState = new ChunkState(this.key, 0L, this.flags);
            this.cvStackLen = 0;
        }

        public Hasher update(byte[] input) {
            return update(input, 0, input.length);
        }

        public Hasher update(byte[] input, int offset, int length) {
            while (length > 0) {
                if (chunkState.len() == CHUNK_LEN) {
                    int[] cv = firstEight(chunkState.output().chainingValue());
                    long totalChunks = chunkState.chunkCounter + 1L;
                    addChunkChainingValue(cv, totalChunks);
                    chunkState = new ChunkState(key, totalChunks, flags);
                }
                int want = CHUNK_LEN - chunkState.len();
                int take = Math.min(want, length);
                chunkState.update(input, offset, take);
                offset += take;
                length -= take;
            }
            return this;
        }

        private void addChunkChainingValue(int[] cv, long totalChunks) {
            while ((totalChunks & 1L) == 0L) {
                cv = firstEight(parentOutputWords(cvStack[--cvStackLen], cv, key, flags));
                totalChunks >>= 1;
            }
            cvStack[cvStackLen++] = cv;
        }

        public byte[] finish(int outputLen) {
            byte[] out = new byte[outputLen];
            finish(out, 0, outputLen);
            return out;
        }

        public void finish(byte[] out, int offset, int outputLen) {
            Output output = chunkState.output();
            int parentNodesRemaining = cvStackLen;
            while (parentNodesRemaining > 0) {
                parentNodesRemaining--;
                output = parentOutput(cvStack[parentNodesRemaining], firstEight(output.chainingValue()), key, flags);
            }
            output.rootBytes(out, offset, outputLen);
        }
    }

    public static Hasher newHasher() {
        return new Hasher(IV, 0);
    }

    public static Hasher newKeyedHasher(byte[] key) {
        if (key.length != KEY_LEN) {
            throw new IllegalArgumentException("Key must be " + KEY_LEN + " bytes");
        }
        return new Hasher(wordsFromKey(key, 0), KEYED_HASH);
    }

    /**
     * Derives a fresh 32-byte subkey from a context string and key material.
     * Two callers using the same context but different material get
     * independent subkeys, which is the canonical way to fan a single
     * master secret out across distinct purposes.
     */
    public static byte[] deriveKey(String context, byte[] keyMaterial, int outputLen) {
        Hasher contextHasher = new Hasher(IV, DERIVE_KEY_CONTEXT);
        contextHasher.update(context.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] contextKey = new byte[KEY_LEN];
        contextHasher.finish(contextKey, 0, KEY_LEN);

        Hasher materialHasher = new Hasher(wordsFromKey(contextKey, 0), DERIVE_KEY_MATERIAL);
        materialHasher.update(keyMaterial);
        byte[] out = new byte[outputLen];
        materialHasher.finish(out, 0, outputLen);
        return out;
    }

    public static byte[] hash(byte[] input, int outputLen) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (outputLen < 0) {
            throw new IllegalArgumentException("outputLen must be non-negative");
        }
        return newHasher().update(input).finish(outputLen);
    }

    public static byte[] keyedHash(byte[] key, byte[] input, int outputLen) {
        if (key == null || key.length != KEY_LEN) {
            throw new IllegalArgumentException("key must be " + KEY_LEN + " bytes");
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (outputLen < 0) {
            throw new IllegalArgumentException("outputLen must be non-negative");
        }
        return newKeyedHasher(key).update(input).finish(outputLen);
    }

    /**
     * Allocation-free PRF API for the world-generation hot path.
     *
     * <p>All entry points read a pre-extracted 8-word key, pack the supplied
     * coordinate/counter bytes into thread-local scratch space, and run a
     * single BLAKE3 compression with {@code KEYED_HASH | CHUNK_START |
     * CHUNK_END | ROOT}. This is bit-for-bit identical to
     * {@link #keyedHash(byte[], byte[], int)} for inputs that fit in a single
     * block (up to {@value #BLOCK_LEN} bytes), but skips the {@code Hasher}
     * and {@code ChunkState} bookkeeping plus all heap allocations along the
     * way.
     */
    public static final class Prf {

        private static final int FAST_FLAGS = KEYED_HASH | CHUNK_START | CHUNK_END | ROOT;

        private static final ThreadLocal<int[]> STATE = ThreadLocal.withInitial(() -> new int[16]);
        private static final ThreadLocal<int[]> BLOCK_A = ThreadLocal.withInitial(() -> new int[16]);
        private static final ThreadLocal<int[]> BLOCK_B = ThreadLocal.withInitial(() -> new int[16]);

        private Prf() {
        }

        /**
         * Extracts the 8 little-endian key words from a 32-byte secret key.
         * The returned array is owned by the caller and can be cached
         * indefinitely; do not pass it across boundaries that mutate it.
         */
        public static int[] keyWords(byte[] key32) {
            if (key32 == null || key32.length != KEY_LEN) {
                throw new IllegalArgumentException("key must be " + KEY_LEN + " bytes");
            }
            return wordsFromKey(key32, 0);
        }

        /**
         * PRF over (x, y, z) — 12 bytes input, returns the first 8 bytes of
         * the BLAKE3 keyed hash as a {@code long}.
         */
        public static long positional(int[] keyWords, int x, int y, int z) {
            int[] state = STATE.get();
            int[] blockA = BLOCK_A.get();
            int[] blockB = BLOCK_B.get();
            zero(blockA);
            blockA[0] = x;
            blockA[1] = y;
            blockA[2] = z;
            compressInto(keyWords, blockA, blockB, 0L, 12, FAST_FLAGS, state);
            return ((long) state[0] & 0xffffffffL) | (((long) state[1] & 0xffffffffL) << 32);
        }

        /**
         * PRF over (x, y, z, counter) — 20 bytes input, returns 8 bytes.
         */
        public static long positional(int[] keyWords, int x, int y, int z, long counter) {
            int[] state = STATE.get();
            int[] blockA = BLOCK_A.get();
            int[] blockB = BLOCK_B.get();
            zero(blockA);
            blockA[0] = x;
            blockA[1] = y;
            blockA[2] = z;
            blockA[3] = (int) counter;
            blockA[4] = (int) (counter >>> 32);
            compressInto(keyWords, blockA, blockB, 0L, 20, FAST_FLAGS, state);
            return ((long) state[0] & 0xffffffffL) | (((long) state[1] & 0xffffffffL) << 32);
        }

        /**
         * PRF over (x, y, z) returning 16 bytes: low 64 bits in {@code outLoHi[0]},
         * high 64 bits in {@code outLoHi[1]}.
         */
        public static void positional2L(int[] keyWords, int x, int y, int z, long[] outLoHi) {
            if (outLoHi == null || outLoHi.length < 2) {
                throw new IllegalArgumentException("outLoHi must have length >= 2");
            }
            int[] state = STATE.get();
            int[] blockA = BLOCK_A.get();
            int[] blockB = BLOCK_B.get();
            zero(blockA);
            blockA[0] = x;
            blockA[1] = y;
            blockA[2] = z;
            compressInto(keyWords, blockA, blockB, 0L, 12, FAST_FLAGS, state);
            outLoHi[0] = ((long) state[0] & 0xffffffffL) | (((long) state[1] & 0xffffffffL) << 32);
            outLoHi[1] = ((long) state[2] & 0xffffffffL) | (((long) state[3] & 0xffffffffL) << 32);
        }

        /**
         * PRF over a single 64-bit input. 8 bytes in, 8 bytes out.
         */
        public static long mixLong(int[] keyWords, long input) {
            int[] state = STATE.get();
            int[] blockA = BLOCK_A.get();
            int[] blockB = BLOCK_B.get();
            zero(blockA);
            blockA[0] = (int) input;
            blockA[1] = (int) (input >>> 32);
            compressInto(keyWords, blockA, blockB, 0L, 8, FAST_FLAGS, state);
            return ((long) state[0] & 0xffffffffL) | (((long) state[1] & 0xffffffffL) << 32);
        }

        /**
         * PRF over (lo, hi). 16 bytes in, 16 bytes out written to outLoHi.
         */
        public static void mix2L(int[] keyWords, long lo, long hi, long[] outLoHi) {
            if (outLoHi == null || outLoHi.length < 2) {
                throw new IllegalArgumentException("outLoHi must have length >= 2");
            }
            int[] state = STATE.get();
            int[] blockA = BLOCK_A.get();
            int[] blockB = BLOCK_B.get();
            zero(blockA);
            blockA[0] = (int) lo;
            blockA[1] = (int) (lo >>> 32);
            blockA[2] = (int) hi;
            blockA[3] = (int) (hi >>> 32);
            compressInto(keyWords, blockA, blockB, 0L, 16, FAST_FLAGS, state);
            outLoHi[0] = ((long) state[0] & 0xffffffffL) | (((long) state[1] & 0xffffffffL) << 32);
            outLoHi[1] = ((long) state[2] & 0xffffffffL) | (((long) state[3] & 0xffffffffL) << 32);
        }

        /**
         * PRF over arbitrary bytes that fit in a single block. For longer
         * inputs, use the streaming {@link Hasher} API instead.
         *
         * @return first 8 bytes of the keyed hash as a {@code long}
         */
        public static long bytes(int[] keyWords, byte[] input, int offset, int length) {
            if (input == null) {
                throw new IllegalArgumentException("input must not be null");
            }
            if (offset < 0 || length < 0 || offset + length > input.length) {
                throw new IllegalArgumentException("offset/length out of bounds");
            }
            if (length > BLOCK_LEN) {
                throw new IllegalArgumentException("Prf.bytes only supports inputs <= " + BLOCK_LEN
                    + " bytes; use Blake3.keyedHash for longer inputs");
            }
            int[] state = STATE.get();
            int[] blockA = BLOCK_A.get();
            int[] blockB = BLOCK_B.get();
            zero(blockA);
            // Pack bytes into the 16-word block, little-endian.
            for (int i = 0; i < length; i++) {
                int wordIdx = i >>> 2;
                int byteShift = (i & 3) << 3;
                blockA[wordIdx] |= (input[offset + i] & 0xff) << byteShift;
            }
            compressInto(keyWords, blockA, blockB, 0L, length, FAST_FLAGS, state);
            return ((long) state[0] & 0xffffffffL) | (((long) state[1] & 0xffffffffL) << 32);
        }

        private static void zero(int[] block) {
            for (int i = 0; i < 16; i++) {
                block[i] = 0;
            }
        }
    }
}
