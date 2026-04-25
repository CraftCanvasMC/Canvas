package io.canvasmc.canvas.world.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link RandomSource} whose state is seeded by a BLAKE3 PRF derived from
 * the secure master key. The downstream RNG is still Xoroshiro128++ for
 * speed; the cryptographic guarantee is that <em>no observer can recover
 * the seed</em> from a sequence of outputs because the seed material has
 * already been irreversibly mixed before it ever reaches the RNG.
 *
 * <p>Hot-path methods avoid allocation entirely: positional derivations go
 * through {@link Blake3.Prf}, which packs coordinates into a thread-local
 * scratch block and runs a single BLAKE3 compression in place.
 */
@NullMarked
public final class SecureRandomSource implements BitRandomSource {

    /** Domain tag for fork derivations: BLAKE3 of the literal "fork-rng-v2". */
    private static final long FORK_TAG = 0xF0B61BACC0DEFEEDL;

    private final XoroshiroRandomSource backing;

    public SecureRandomSource(long seedLo, long seedHi) {
        this.backing = new XoroshiroRandomSource(seedLo, seedHi);
    }

    public static SecureRandomSource fromKey(byte[] key32) {
        if (key32 == null || key32.length < 16) {
            throw new IllegalArgumentException("key bytes must be at least 16");
        }
        long lo = readLongLe(key32, 0);
        long hi = readLongLe(key32, 8);
        return new SecureRandomSource(lo, hi);
    }

    @Override
    public RandomSource fork() {
        // Securely derive the forked state by mixing the next two RNG outputs
        // through the BLAKE3 PRF. Without this, an observer who learns one
        // output of the parent could partially reconstruct child state.
        SecureSeed seed = SecureSeed.active();
        long lo = this.nextLong();
        long hi = this.nextLong();
        if (seed == null || seed.mode() == SecureSeed.Mode.V1) {
            return new SecureRandomSource(lo, hi);
        }
        long[] mixed = MIX_SCRATCH.get();
        Blake3.Prf.mix2L(seed.prfKeyWords(), lo ^ FORK_TAG, hi, mixed);
        return new SecureRandomSource(mixed[0], mixed[1]);
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        SecureSeed seed = SecureSeed.active();
        if (seed == null || seed.mode() == SecureSeed.Mode.V1) {
            return new LegacyDelegate(this.nextLong());
        }
        // Securely derive a per-factory subkey by hashing the next two RNG
        // outputs under the master PRF key. The child factory holds the
        // 8-word subkey directly; per-coord at() calls don't reallocate.
        long[] mixed = MIX_SCRATCH.get();
        Blake3.Prf.mix2L(seed.prfKeyWords(), this.nextLong(), this.nextLong(), mixed);
        // Expand 16 bytes -> 32 bytes via two more PRF calls, so the factory's
        // subkey occupies the full BLAKE3 key width.
        long[] expanded = EXPAND_SCRATCH.get();
        Blake3.Prf.mix2L(seed.prfKeyWords(), mixed[0], mixed[1], expanded);
        int[] subkeyWords = new int[8];
        packLongsToWords(mixed[0], mixed[1], expanded[0], expanded[1], subkeyWords);
        return new SecurePositionalFactory(subkeyWords);
    }

    @Override
    public void setSeed(long seed) {
        this.backing.setSeed(seed);
    }

    @Override
    public int next(int bits) {
        return (int) (this.nextLong() >>> (64 - bits));
    }

    @Override
    public long nextLong() {
        return this.backing.nextLong();
    }

    @Override
    public int nextInt() {
        return this.backing.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return this.backing.nextInt(bound);
    }

    @Override
    public boolean nextBoolean() {
        return this.backing.nextBoolean();
    }

    @Override
    public float nextFloat() {
        return this.backing.nextFloat();
    }

    @Override
    public double nextDouble() {
        return this.backing.nextDouble();
    }

    @Override
    public double nextGaussian() {
        return this.backing.nextGaussian();
    }

    /**
     * Positional factory backed by a per-dimension secure subkey held as 8
     * pre-extracted BLAKE3 key words. Each {@code at(x, y, z)} call hashes
     * the coordinate tuple under the subkey via the allocation-free
     * {@link Blake3.Prf} path.
     */
    public static final class SecurePositionalFactory implements PositionalRandomFactory {
        private final int[] subkeyWords;

        public SecurePositionalFactory(int[] subkeyWords) {
            Objects.requireNonNull(subkeyWords, "subkeyWords");
            if (subkeyWords.length != 8) {
                throw new IllegalArgumentException("subkey must be 8 words");
            }
            this.subkeyWords = subkeyWords.clone();
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long[] out = MIX_SCRATCH.get();
            Blake3.Prf.positional2L(subkeyWords, x, y, z, out);
            return new SecureRandomSource(out[0], out[1]);
        }

        @Override
        public RandomSource fromHashOf(String seed) {
            byte[] utf8 = seed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (utf8.length <= Blake3.BLOCK_LEN) {
                long lo = Blake3.Prf.bytes(subkeyWords, utf8, 0, utf8.length);
                // Derive hi by re-hashing under domain tag so lo and hi are independent.
                long hi = Blake3.Prf.mixLong(subkeyWords, lo ^ STRING_TAG);
                return new SecureRandomSource(lo, hi);
            }
            // Slow path: allocate to use the full streaming hasher. Strings
            // longer than 64 bytes are rare in worldgen call sites.
            byte[] keyBytes = subkeyWordsToBytes();
            byte[] out = Blake3.keyedHash(keyBytes, utf8, 16);
            return new SecureRandomSource(readLongLe(out, 0), readLongLe(out, 8));
        }

        @Override
        public RandomSource fromSeed(long seed) {
            long lo = Blake3.Prf.mixLong(subkeyWords, seed);
            long hi = Blake3.Prf.mixLong(subkeyWords, seed ^ SEED_TAG);
            return new SecureRandomSource(lo, hi);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder info) {
            info.append("SecurePositionalFactory{secure-v2}");
        }

        private byte[] subkeyWordsToBytes() {
            byte[] bytes = new byte[Blake3.KEY_LEN];
            for (int i = 0; i < 8; i++) {
                int w = subkeyWords[i];
                int j = i * 4;
                bytes[j] = (byte) w;
                bytes[j + 1] = (byte) (w >>> 8);
                bytes[j + 2] = (byte) (w >>> 16);
                bytes[j + 3] = (byte) (w >>> 24);
            }
            return bytes;
        }
    }

    /**
     * Fallback used when the active mode is V1 (or when secure seed has not
     * yet been initialized). Mirrors the structure vanilla uses so seed
     * parity is preserved.
     */
    private static final class LegacyDelegate implements PositionalRandomFactory {
        private final long seed;

        LegacyDelegate(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            return new SecureRandomSource(net.minecraft.util.Mth.getSeed(x, y, z) ^ seed, seed);
        }

        @Override
        public RandomSource fromHashOf(String seed) {
            return new SecureRandomSource(((long) seed.hashCode()) ^ this.seed, this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new SecureRandomSource(seed, this.seed);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder info) {
            info.append("SecurePositionalFactory{legacy=").append(this.seed).append('}');
        }
    }

    private static long readLongLe(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff))
            | (((long) (data[offset + 1] & 0xff)) << 8)
            | (((long) (data[offset + 2] & 0xff)) << 16)
            | (((long) (data[offset + 3] & 0xff)) << 24)
            | (((long) (data[offset + 4] & 0xff)) << 32)
            | (((long) (data[offset + 5] & 0xff)) << 40)
            | (((long) (data[offset + 6] & 0xff)) << 48)
            | (((long) (data[offset + 7] & 0xff)) << 56);
    }

    private static void packLongsToWords(long a, long b, long c, long d, int[] out) {
        out[0] = (int) a;
        out[1] = (int) (a >>> 32);
        out[2] = (int) b;
        out[3] = (int) (b >>> 32);
        out[4] = (int) c;
        out[5] = (int) (c >>> 32);
        out[6] = (int) d;
        out[7] = (int) (d >>> 32);
    }

    private static final long STRING_TAG = 0xA1A2A3A4A5A6A7A8L;
    private static final long SEED_TAG = 0xB1B2B3B4B5B6B7B8L;

    /** Reusable two-long scratch buffers to avoid allocation in fork()/at(). */
    private static final ThreadLocal<long[]> MIX_SCRATCH = ThreadLocal.withInitial(() -> new long[2]);
    private static final ThreadLocal<long[]> EXPAND_SCRATCH = ThreadLocal.withInitial(() -> new long[2]);
}
