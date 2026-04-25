package io.canvasmc.canvas.world.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.util.Mth;
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
 */
@NullMarked
public final class SecureRandomSource implements BitRandomSource {

    private final XoroshiroRandomSource backing;

    public SecureRandomSource(long seedLo, long seedHi) {
        this.backing = new XoroshiroRandomSource(seedLo, seedHi);
    }

    public static SecureRandomSource fromKey(byte[] key32) {
        ByteBuffer buf = ByteBuffer.wrap(key32).order(ByteOrder.LITTLE_ENDIAN);
        return new SecureRandomSource(buf.getLong(), buf.getLong());
    }

    @Override
    public RandomSource fork() {
        return new SecureRandomSource(this.nextLong(), this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        SecureSeed seed = SecureSeed.active();
        if (seed == null || seed.mode() == SecureSeed.Mode.V1) {
            return new LegacyDelegate(this.nextLong());
        }
        byte[] subkey = Blake3.keyedHash(deriveSubkeyMaterial(this.nextLong(), this.nextLong()),
            "secure-positional-fork".getBytes(java.nio.charset.StandardCharsets.UTF_8), Blake3.KEY_LEN);
        return new SecurePositionalFactory(subkey);
    }

    private static byte[] deriveSubkeyMaterial(long lo, long hi) {
        byte[] material = new byte[Long.BYTES * 2];
        ByteBuffer.wrap(material).order(ByteOrder.LITTLE_ENDIAN).putLong(lo).putLong(hi);
        return material;
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
     * Positional factory backed by a per-dimension secure subkey.
     * Each {@code at(x, y, z)} call hashes the coordinate tuple under the
     * subkey to derive 16 fresh bytes of state, eliminating the linear
     * relationship between (x, y, z) and the produced seed that vanilla
     * preserves.
     */
    public static final class SecurePositionalFactory implements PositionalRandomFactory {
        private final byte[] subkey;

        public SecurePositionalFactory(byte[] subkey) {
            this.subkey = subkey.clone();
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            byte[] header = new byte[Integer.BYTES * 3];
            ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(x).putInt(y).putInt(z);
            byte[] out = Blake3.keyedHash(subkey, header, 16);
            ByteBuffer buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
            return new SecureRandomSource(buf.getLong(), buf.getLong());
        }

        @Override
        public RandomSource fromHashOf(String seed) {
            byte[] out = Blake3.keyedHash(subkey, seed.getBytes(java.nio.charset.StandardCharsets.UTF_8), 16);
            ByteBuffer buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
            return new SecureRandomSource(buf.getLong(), buf.getLong());
        }

        @Override
        public RandomSource fromSeed(long seed) {
            byte[] header = new byte[Long.BYTES];
            ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putLong(seed);
            byte[] out = Blake3.keyedHash(subkey, header, 16);
            ByteBuffer buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
            return new SecureRandomSource(buf.getLong(), buf.getLong());
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder info) {
            info.append("SecurePositionalFactory{secure-v2}");
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
            return new SecureRandomSource(Mth.getSeed(x, y, z) ^ seed, seed);
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
}
