package io.canvasmc.canvas.world.levelgen;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Secure world generation primitives (Foldenor patch port).
 *
 * <p>The vanilla world seed is a 64-bit value that is fed directly into Java's
 * legacy/xoroshiro RNG, which means a sufficiently motivated attacker can
 * statistically reverse-engineer the seed from a handful of generated chunks.
 *
 * <p>This class replaces that pipeline with two layers of cryptographic
 * indirection:
 *
 * <ol>
 *   <li>The 64-bit world seed is expanded into a 1024-bit master key by mixing
 *       it with a per-server SALT through BLAKE3's KDF.</li>
 *   <li>Per-position randomness is derived as
 *       {@code BLAKE3(prfKey, dimension-subkey || x || y || z || counter)},
 *       which is statistically indistinguishable from random and reveals no
 *       information about either the master key or the world seed.</li>
 * </ol>
 *
 * <p>Two modes are supported:
 * <ul>
 *   <li>{@link Mode#V1}: vanilla-compatible, predictable, reversible. Default
 *       for parity.</li>
 *   <li>{@link Mode#V2}: secure mode. Reproducible (same seed + same SALT
 *       yield the same world) but not reversible.</li>
 * </ul>
 *
 * <h2>Threading and lifecycle</h2>
 * The active secure seed is set once at world load via {@link #install} and
 * is read on every world-gen call. Reads are wait-free; the setter is
 * idempotent for identical inputs and warns the operator about live
 * reconfiguration.
 *
 * <h2>Performance</h2>
 * The hot path for positional randomness uses {@link Blake3.Prf}, which
 * packs the input directly into thread-local scratch space and runs a single
 * BLAKE3 compression with no heap allocation.
 */
public final class SecureSeed {

    public static final int MASTER_KEY_BYTES = 128;
    public static final int SALT_BYTES = 32;
    public static final int DIM_SUBKEY_BYTES = 32;

    private static final String KDF_CONTEXT = "io.canvasmc.canvas 2026 secure-seed v2 master-key";

    public enum Mode {
        /** Vanilla / legacy behavior. World seeds are reversible. */
        V1,
        /** BLAKE3-backed secure mode. Recommended for private servers. */
        V2
    }

    private static final AtomicReference<SecureSeed> ACTIVE = new AtomicReference<>();

    private final Mode mode;
    private final long worldSeed;
    private final byte[] salt;
    private final byte[] masterKey;
    /** First 32 bytes of {@link #masterKey} pre-extracted as 8 LE words. */
    private final int[] prfKey;
    private final long foldedSeed;
    /** dimensionId -> 8-word subkey, computed on first use. */
    private final ConcurrentHashMap<String, int[]> subkeyCache;

    private SecureSeed(Mode mode, long worldSeed, byte[] salt, byte[] masterKey,
                       int[] prfKey, long foldedSeed, ConcurrentHashMap<String, int[]> cache) {
        this.mode = mode;
        this.worldSeed = worldSeed;
        this.salt = salt;
        this.masterKey = masterKey;
        this.prfKey = prfKey;
        this.foldedSeed = foldedSeed;
        this.subkeyCache = cache;
    }

    public Mode mode() {
        return mode;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public byte[] saltCopy() {
        return salt.clone();
    }

    /**
     * Returns the active secure seed, or {@code null} if the world has not yet
     * been initialized. Hot-path callers should hoist this read into a local
     * variable rather than calling repeatedly.
     */
    public static SecureSeed active() {
        return ACTIVE.get();
    }

    /**
     * Installs the active secure seed for the current run. Idempotent for
     * identical inputs; logs a warning if called with a different seed after
     * one has already been installed (to surface mid-run reconfiguration
     * bugs in upstream code).
     */
    public static void install(SecureSeed seed) {
        SecureSeed previous = ACTIVE.getAndSet(seed);
        if (previous != null && seed != null && !equivalent(previous, seed)) {
            // Use the same logger plumbing as Config; defer to a logger call
            // routed through Blake3-free utilities so this class has no
            // hard dependency on logging frameworks.
            System.err.println("[Canvas/SecureSeed] active secure seed replaced at runtime; "
                + "this can break reproducibility within a single server lifecycle");
        }
    }

    private static boolean equivalent(SecureSeed a, SecureSeed b) {
        if (a.mode != b.mode || a.worldSeed != b.worldSeed) {
            return false;
        }
        return java.util.Arrays.equals(a.salt, b.salt);
    }

    /**
     * Builds a secure seed from configured inputs. When {@code mode} is
     * {@link Mode#V1}, the salt and KDF expansion are skipped; the returned
     * value still records the original 64-bit seed so call sites can switch
     * on {@link #mode()} without branching on null.
     */
    public static SecureSeed of(Mode mode, long worldSeed, byte[] salt) {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.V1) {
            return new SecureSeed(Mode.V1, worldSeed, EMPTY_BYTES, EMPTY_BYTES,
                EMPTY_KEY_WORDS, worldSeed, new ConcurrentHashMap<>());
        }
        if (salt == null || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException("V2 secure seed requires a " + SALT_BYTES + "-byte salt");
        }
        byte[] kdfInput = packSeedAndSalt(worldSeed, salt);
        byte[] masterKey = Blake3.deriveKey(KDF_CONTEXT, kdfInput, MASTER_KEY_BYTES);
        int[] prfKey = Blake3.Prf.keyWords(java.util.Arrays.copyOf(masterKey, Blake3.KEY_LEN));
        // Strong fold: take the first 8 bytes of an unkeyed BLAKE3 hash of the
        // master key. This is a one-way 64-bit projection with no exploitable
        // cancellation that an XOR fold would expose.
        byte[] foldBytes = Blake3.hash(masterKey, 8);
        long folded = ((long) (foldBytes[0] & 0xff))
            | (((long) (foldBytes[1] & 0xff)) << 8)
            | (((long) (foldBytes[2] & 0xff)) << 16)
            | (((long) (foldBytes[3] & 0xff)) << 24)
            | (((long) (foldBytes[4] & 0xff)) << 32)
            | (((long) (foldBytes[5] & 0xff)) << 40)
            | (((long) (foldBytes[6] & 0xff)) << 48)
            | (((long) (foldBytes[7] & 0xff)) << 56);
        return new SecureSeed(Mode.V2, worldSeed, salt.clone(), masterKey, prfKey, folded,
            new ConcurrentHashMap<>());
    }

    private static byte[] packSeedAndSalt(long seed, byte[] salt) {
        byte[] out = new byte[Long.BYTES + SALT_BYTES];
        out[0] = (byte) seed;
        out[1] = (byte) (seed >>> 8);
        out[2] = (byte) (seed >>> 16);
        out[3] = (byte) (seed >>> 24);
        out[4] = (byte) (seed >>> 32);
        out[5] = (byte) (seed >>> 40);
        out[6] = (byte) (seed >>> 48);
        out[7] = (byte) (seed >>> 56);
        System.arraycopy(salt, 0, out, Long.BYTES, SALT_BYTES);
        return out;
    }

    /**
     * Generates a fresh, cryptographically secure 32-byte salt. Callers should
     * persist the result to config so worlds remain stable across restarts.
     */
    public static byte[] generateSalt() {
        byte[] s = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(s);
        return s;
    }

    public static String saltToHex(byte[] salt) {
        if (salt == null || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException("salt must be " + SALT_BYTES + " bytes");
        }
        return HexFormat.of().formatHex(salt);
    }

    public static byte[] saltFromHex(String hex) {
        if (hex == null) {
            return null;
        }
        String trimmed = hex.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = HexFormat.of().parseHex(trimmed);
            return bytes.length == SALT_BYTES ? bytes : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Folds the 1024-bit master key down to a 64-bit pseudo-seed for code
     * paths that still take a {@code long}. Computed once at construction
     * via {@code BLAKE3(masterKey)[:8]}, which is a strong one-way hash and
     * therefore cannot be inverted to recover the original world seed.
     */
    public long foldedSeed() {
        return foldedSeed;
    }

    /**
     * Returns the cached 8-word PRF key (first 32 bytes of the master key).
     * Hot-path callers can pass this directly into {@link Blake3.Prf} for
     * allocation-free hashing. The returned array is shared; do not mutate.
     */
    public int[] prfKeyWords() {
        return prfKey;
    }

    /**
     * Derives a stable 32-byte subkey for the given dimension identifier
     * (e.g. {@code "minecraft:overworld"}). Different dimensions get
     * independent randomness so observing the overworld leaks nothing
     * about the nether or the end. Subkeys are cached per dimension id.
     */
    public byte[] dimensionSubkey(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (mode == Mode.V1) {
            return null;
        }
        int[] cached = subkeyCache.computeIfAbsent(dimensionId, this::computeDimensionSubkeyWords);
        byte[] out = new byte[DIM_SUBKEY_BYTES];
        wordsToBytes(cached, out);
        return out;
    }

    /**
     * Same as {@link #dimensionSubkey(String)} but returns the cached 8-word
     * representation directly. Hot-path callers that pass the subkey straight
     * to {@link Blake3.Prf} should use this overload to avoid repacking.
     */
    public int[] dimensionSubkeyWords(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (mode == Mode.V1) {
            return null;
        }
        return subkeyCache.computeIfAbsent(dimensionId, this::computeDimensionSubkeyWords);
    }

    private int[] computeDimensionSubkeyWords(String dimensionId) {
        // PRF the dimension id under the master PRF key. This is one
        // BLAKE3 keyedHash call per dimension, performed once and cached;
        // we don't go through the heavier KDF on a concatenated buffer.
        byte[] dimBytes = dimensionId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] prfKeyBytes = java.util.Arrays.copyOf(masterKey, Blake3.KEY_LEN);
        byte[] subkey = Blake3.keyedHash(prfKeyBytes, dimBytes, DIM_SUBKEY_BYTES);
        return Blake3.Prf.keyWords(subkey);
    }

    private static void wordsToBytes(int[] words, byte[] out) {
        for (int i = 0; i < words.length; i++) {
            int w = words[i];
            int j = i * 4;
            out[j] = (byte) w;
            out[j + 1] = (byte) (w >>> 8);
            out[j + 2] = (byte) (w >>> 16);
            out[j + 3] = (byte) (w >>> 24);
        }
    }

    /**
     * Direct positional PRF: returns 8 bytes derived from the master PRF
     * key and the supplied tuple. Allocation-free, matches BLAKE3 keyed
     * hash bit-for-bit. Includes domain separation by counter.
     */
    public long positional(int x, int y, int z, long counter) {
        if (mode == Mode.V1) {
            return mixV1(worldSeed, x, y, z, counter);
        }
        return Blake3.Prf.positional(prfKey, x, y, z, counter);
    }

    /**
     * Dimension-aware positional PRF. Uses the dimension's subkey instead of
     * the master PRF key, eliminating cross-dimension correlation entirely.
     */
    public long positional(String dimensionId, int x, int y, int z, long counter) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (mode == Mode.V1) {
            return mixV1(worldSeed, x, y, z, counter);
        }
        int[] subkey = dimensionSubkeyWords(dimensionId);
        return Blake3.Prf.positional(subkey, x, y, z, counter);
    }

    /**
     * Derives a stable per-coordinate seed under the supplied dimension subkey.
     * This is the entry point used by the random-source adapter to bind
     * (dimension, x, y, z) to a fresh randomness stream.
     */
    public static long deriveCoordinateSeed(int[] dimensionSubkeyWords, int x, int y, int z, long counter) {
        Objects.requireNonNull(dimensionSubkeyWords, "dimensionSubkeyWords");
        if (dimensionSubkeyWords.length != 8) {
            throw new IllegalArgumentException("subkey must be 8 words");
        }
        return Blake3.Prf.positional(dimensionSubkeyWords, x, y, z, counter);
    }

    private static long mixV1(long seed, int x, int y, int z, long counter) {
        long h = seed;
        h ^= ((long) x) * 0x9E3779B97F4A7C15L;
        h ^= ((long) y) * 0xBF58476D1CE4E5B9L;
        h ^= ((long) z) * 0x94D049BB133111EBL;
        h ^= counter;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return h;
    }

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int[] EMPTY_KEY_WORDS = new int[0];
}
