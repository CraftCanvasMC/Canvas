package io.canvasmc.canvas.world.levelgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A {@link WorldgenRandom} whose state is seeded by a BLAKE3 PRF over
 * {@code (Globals.worldSeed, salt, x, z, extraSeed)}.
 *
 * <p>Drop-in replacement for {@code new WorldgenRandom(new
 * LegacyRandomSource(...))} in any worldgen call site that needs
 * cryptographically isolated randomness for a given coordinate. Each
 * {@link Salt} value provides domain separation, so the same coordinate
 * with a different salt yields independent randomness.
 *
 * <p>If {@link Globals#worldSeed} is {@code null} (secure seed protection
 * disabled or not yet bootstrapped) the seed falls back to a deterministic
 * hash of the inputs only — sufficient for parity within a single run but
 * not cryptographically protected. Call sites that care about the security
 * guarantee must check {@link Globals#isActive()} before instantiation.
 *
 * <p>Allocation: only the underlying Xoroshiro state is allocated; the
 * BLAKE3 derivation goes through {@link Blake3.Prf} and reuses thread-local
 * scratch.
 */
public class WorldgenCryptoRandom extends WorldgenRandom {

    private static final ThreadLocal<long[]> MIX_SCRATCH = ThreadLocal.withInitial(() -> new long[2]);

    public WorldgenCryptoRandom(int x, int z, Salt salt, long extraSeed) {
        super(buildBaseSource(x, z, salt, extraSeed));
    }

    private static RandomSource buildBaseSource(int x, int z, Salt salt, long extraSeed) {
        if (salt == null) {
            throw new IllegalArgumentException("salt must not be null");
        }
        int[] key = Globals.worldSeedPrfKey;
        if (key == null) {
            // Secure seed not active. Derive a stable but non-secret seed so
            // worldgen still functions — caller chose this path explicitly.
            long fallback = Mth.getSeed(x, salt.ordinal(), z) ^ salt.magic() ^ extraSeed;
            return new XoroshiroRandomSource(fallback, fallback ^ 0x9E3779B97F4A7C15L);
        }
        long[] outLoHi = MIX_SCRATCH.get();
        Blake3.Prf.worldgenCoord(key, x, z, salt.magic(), extraSeed, outLoHi);
        return new XoroshiroRandomSource(outLoHi[0], outLoHi[1]);
    }

    /**
     * Convenience: builds a slime-chunk PRF random for the given chunk
     * coordinates. The salt is {@link Salt#UNDEFINED} and the extra seed
     * is the slime-chunk magic constant from Bedrock parity.
     */
    public static RandomSource seedSlimeChunk(int chunkX, int chunkZ) {
        return new WorldgenCryptoRandom(chunkX, chunkZ, Salt.UNDEFINED, 0x536C696D65L); // "Slime"
    }

    // Vanilla setSeed-style helpers are no-ops for this implementation: the
    // state is already derived from the secure inputs and overriding it
    // would weaken the security guarantee.

    @Override
    public long setDecorationSeed(long levelSeed, int blockX, int blockZ) {
        // Ignore levelSeed; derive a fresh sub-state from the secure inputs.
        // Returns the same 64-bit value the underlying RNG would produce as
        // its first nextLong() call, mirroring vanilla's contract.
        return this.nextLong();
    }

    @Override
    public void setLargeFeatureSeed(long levelSeed, int chunkX, int chunkZ) {
        // No-op: the secure seed already encodes (chunkX, chunkZ) via the
        // factory constructor.
    }

    @Override
    public void setLargeFeatureWithSalt(long levelSeed, int chunkX, int chunkZ, int salt) {
        // No-op for the same reason.
    }
}
