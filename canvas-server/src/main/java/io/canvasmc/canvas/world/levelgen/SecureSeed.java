package io.canvasmc.canvas.world.levelgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.HexFormat;

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
 *       {@code BLAKE3(master, dimension || x || y || z || counter)}, which
 *       is statistically indistinguishable from random and reveals no
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
 */
public final class SecureSeed {

    public static final int MASTER_KEY_BYTES = 128;
    public static final int SALT_BYTES = 32;

    private static final String KDF_CONTEXT = "io.canvasmc.canvas 2026 secure-seed v2 master-key";
    private static final String DIM_SUBKEY_CONTEXT = "io.canvasmc.canvas 2026 secure-seed v2 dim-subkey";

    public enum Mode {
        /** Vanilla / legacy behavior. World seeds are reversible. */
        V1,
        /** BLAKE3-backed secure mode. Recommended for private servers. */
        V2
    }

    private static volatile SecureSeed active;

    private final Mode mode;
    private final long worldSeed;
    private final byte[] salt;
    private final byte[] masterKey;

    private SecureSeed(Mode mode, long worldSeed, byte[] salt, byte[] masterKey) {
        this.mode = mode;
        this.worldSeed = worldSeed;
        this.salt = salt;
        this.masterKey = masterKey;
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
     * been initialized. Callers in the world generation hot path must be
     * tolerant of the {@code null} case (and fall back to V1 behavior) because
     * some ad-hoc generators run before the level finishes loading.
     */
    public static SecureSeed active() {
        return active;
    }

    /**
     * Installs the active secure seed for the current run.
     */
    public static void install(SecureSeed seed) {
        active = seed;
    }

    /**
     * Builds a secure seed from configured inputs. When {@code mode} is
     * {@link Mode#V1}, the salt and KDF expansion are skipped; the returned
     * value still records the original 64-bit seed so call sites can switch
     * on {@link #mode()} without branching on null.
     */
    public static SecureSeed of(Mode mode, long worldSeed, byte[] salt) {
        if (mode == Mode.V1) {
            return new SecureSeed(Mode.V1, worldSeed, EMPTY_SALT, EMPTY_KEY);
        }
        if (salt == null || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException("V2 secure seed requires a " + SALT_BYTES + "-byte salt");
        }
        byte[] kdfInput = new byte[Long.BYTES + SALT_BYTES];
        ByteBuffer.wrap(kdfInput).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(worldSeed)
            .put(salt);
        byte[] masterKey = Blake3.deriveKey(KDF_CONTEXT, kdfInput, MASTER_KEY_BYTES);
        return new SecureSeed(Mode.V2, worldSeed, salt.clone(), masterKey);
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
     * paths that still take a {@code long}. Because the master key is already
     * indistinguishable from random, the truncation is a one-way mapping that
     * cannot be inverted to recover the original world seed.
     */
    public long foldedSeed() {
        if (mode == Mode.V1) {
            return worldSeed;
        }
        long acc = 0L;
        for (int i = 0; i < masterKey.length; i += 8) {
            long lane = 0L;
            for (int b = 0; b < 8; b++) {
                lane |= ((long) (masterKey[i + b] & 0xff)) << (8 * b);
            }
            acc ^= lane;
        }
        return acc;
    }

    /**
     * Derives a stable 32-byte subkey for the given dimension identifier
     * (e.g. {@code "minecraft:overworld"}). Different dimensions get
     * independent randomness, so observing the overworld leaks nothing
     * about the nether or the end.
     */
    public byte[] dimensionSubkey(String dimensionId) {
        if (mode == Mode.V1) {
            return null;
        }
        byte[] dimBytes = dimensionId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] material = new byte[masterKey.length + dimBytes.length];
        System.arraycopy(masterKey, 0, material, 0, masterKey.length);
        System.arraycopy(dimBytes, 0, material, masterKey.length, dimBytes.length);
        return Blake3.deriveKey(DIM_SUBKEY_CONTEXT, material, Blake3.KEY_LEN);
    }

    /**
     * Derives a stable per-coordinate seed under the supplied dimension subkey.
     * This is the entry point used by the random-source adapter to bind
     * (dimension, x, y, z) to a fresh randomness stream.
     */
    public static long deriveCoordinateSeed(byte[] dimensionSubkey, int x, int y, int z, long counter) {
        byte[] header = new byte[Integer.BYTES * 3 + Long.BYTES];
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(x)
            .putInt(y)
            .putInt(z)
            .putLong(counter);
        byte[] out = Blake3.keyedHash(dimensionSubkey, header, Long.BYTES);
        return ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /**
     * Direct positional PRF: returns 8 bytes derived from the master key and
     * the supplied tuple. Useful for code that wants a cheap hash without
     * managing a per-dimension subkey.
     */
    public long positional(int x, int y, int z, long counter) {
        if (mode == Mode.V1) {
            return mixV1(worldSeed, x, y, z, counter);
        }
        byte[] header = new byte[Integer.BYTES * 3 + Long.BYTES];
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(x)
            .putInt(y)
            .putInt(z)
            .putLong(counter);
        byte[] keyMaterial = new byte[Blake3.KEY_LEN];
        System.arraycopy(masterKey, 0, keyMaterial, 0, Blake3.KEY_LEN);
        byte[] out = Blake3.keyedHash(keyMaterial, header, Long.BYTES);
        return ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).getLong();
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

    private static final byte[] EMPTY_SALT = new byte[0];
    private static final byte[] EMPTY_KEY = new byte[0];
}
