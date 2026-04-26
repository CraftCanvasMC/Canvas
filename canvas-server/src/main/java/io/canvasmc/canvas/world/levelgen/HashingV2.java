package io.canvasmc.canvas.world.levelgen;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Luminol/Matter-compatible BLAKE3 secure-seed primitives.
 *
 * <p>Two static entry points cover the worldgen pipeline:
 *
 * <ul>
 *   <li>{@link #expandLevelSeedTo1024Bits} — KDF: a 64-bit seed becomes a
 *       1024-bit (16-long) master from which all per-component seeds are
 *       derived. Output is statistically indistinguishable from random and
 *       contains no recoverable trace of the input seed.</li>
 *   <li>{@link #getTerrainSeed} — PRF: extracts a 64-bit subkey from the
 *       1024-bit master for a specific terrain component (base terrain,
 *       aquifer, ore, climate, surface). Independent components are
 *       cryptographically isolated from each other.</li>
 * </ul>
 *
 * <p>All output is deterministic in the input: the same {@code (seed, salt,
 * coordinates)} tuple always produces the same bytes, so worlds remain
 * stable across restarts.
 *
 * <p>The 1024-bit feature seed is the actual cryptographic secret; the
 * vanilla 64-bit world seed is left unchanged for parity with display
 * commands and Bukkit APIs.
 */
public final class HashingV2 {

    public static final int FEATURE_SEED_LONGS = 16;
    public static final int FEATURE_SEED_BYTES = FEATURE_SEED_LONGS * Long.BYTES;

    private static final String KDF_CONTEXT = "io.canvasmc.canvas 2026 secure-seed v2 feature-seed";
    private static final String TERRAIN_CONTEXT = "io.canvasmc.canvas 2026 secure-seed v2 terrain";

    /**
     * Distinct components of the worldgen pipeline. Each gets an independent
     * 64-bit subkey extracted from the 1024-bit master, so the surface noise
     * cannot be used to predict ore placement and vice versa.
     */
    public enum TerrainType {
        BASE_TERRAIN(0),
        AQUIFER(1),
        ORE(2),
        CLIMATE(3),
        SURFACE(4),
        END_ISLANDS(5);

        private final int index;

        TerrainType(int index) {
            this.index = index;
        }

        public int index() {
            return this.index;
        }
    }

    private HashingV2() {
    }

    /**
     * Expands a 64-bit world seed into a 1024-bit feature seed.
     * Equivalent to {@code BLAKE3.deriveKey(KDF_CONTEXT, seed_bytes, 128)}.
     */
    public static long[] expandLevelSeedTo1024Bits(long levelSeed) {
        byte[] kdfInput = new byte[Long.BYTES];
        for (int i = 0; i < Long.BYTES; i++) {
            kdfInput[i] = (byte) (levelSeed >>> (i * 8));
        }
        byte[] expanded = Blake3.deriveKey(KDF_CONTEXT, kdfInput, FEATURE_SEED_BYTES);
        return bytesToLongs(expanded);
    }

    /**
     * Generates a fresh, cryptographically random 1024-bit feature seed.
     */
    public static long[] createRandomFeatureSeed() {
        byte[] random = new byte[FEATURE_SEED_BYTES];
        new SecureRandom().nextBytes(random);
        return bytesToLongs(random);
    }

    /**
     * Returns true if the supplied feature seed is a syntactically valid
     * 1024-bit value (non-null, exactly 16 longs).
     */
    public static boolean isValid(long[] featureSeed) {
        return featureSeed != null && featureSeed.length == FEATURE_SEED_LONGS;
    }

    /**
     * Derives a 64-bit subkey for the given terrain component.
     * Stable: the same (featureSeed, type) always yields the same long.
     */
    public static long getTerrainSeed(long[] featureSeed, TerrainType type) {
        if (!isValid(featureSeed)) {
            throw new IllegalArgumentException("featureSeed must be " + FEATURE_SEED_LONGS + " longs");
        }
        // Pack: 128 bytes of featureSeed || 4 bytes terrain index.
        // We hash with Blake3.deriveKey for strong domain separation.
        byte[] material = new byte[FEATURE_SEED_BYTES + Integer.BYTES];
        longsToBytes(featureSeed, material, 0);
        int idx = type.index();
        material[FEATURE_SEED_BYTES] = (byte) idx;
        material[FEATURE_SEED_BYTES + 1] = (byte) (idx >>> 8);
        material[FEATURE_SEED_BYTES + 2] = (byte) (idx >>> 16);
        material[FEATURE_SEED_BYTES + 3] = (byte) (idx >>> 24);
        byte[] hash = Blake3.deriveKey(TERRAIN_CONTEXT, material, Long.BYTES);
        return bytesToLongLE(hash, 0);
    }

    /**
     * Cached PRF key (8 BLAKE3 words) for the supplied feature seed.
     * Hot-path callers use this with {@link Blake3.Prf} for allocation-free
     * coordinate hashing.
     */
    public static int[] prfKeyWords(long[] featureSeed) {
        if (!isValid(featureSeed)) {
            throw new IllegalArgumentException("featureSeed must be " + FEATURE_SEED_LONGS + " longs");
        }
        byte[] keyBytes = new byte[Blake3.KEY_LEN];
        longsToBytes(featureSeed, keyBytes, 0, Blake3.KEY_LEN / Long.BYTES);
        return Blake3.Prf.keyWords(keyBytes);
    }

    /**
     * Serializes a feature seed as a comma-separated decimal string suitable
     * for {@code feature-level-seed=...} in {@code server.properties}.
     */
    public static String featureSeedToString(long[] featureSeed) {
        if (!isValid(featureSeed)) {
            throw new IllegalArgumentException("featureSeed must be " + FEATURE_SEED_LONGS + " longs");
        }
        StringBuilder sb = new StringBuilder(featureSeed.length * 12);
        for (int i = 0; i < featureSeed.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(featureSeed[i]);
        }
        return sb.toString();
    }

    /**
     * Parses a comma-separated decimal feature-seed string. Returns
     * {@code null} for null/empty input or malformed values, so the caller
     * can fall back to {@link #createRandomFeatureSeed} cleanly.
     */
    public static long[] parseFeatureSeed(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split(",");
        if (parts.length != FEATURE_SEED_LONGS) {
            return null;
        }
        long[] out = new long[FEATURE_SEED_LONGS];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Long.parseLong(parts[i].trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return out;
    }

    private static byte[] longsToBytes(long[] longs, byte[] out, int offset, int count) {
        for (int i = 0; i < count; i++) {
            long l = longs[i];
            int j = offset + i * Long.BYTES;
            out[j] = (byte) l;
            out[j + 1] = (byte) (l >>> 8);
            out[j + 2] = (byte) (l >>> 16);
            out[j + 3] = (byte) (l >>> 24);
            out[j + 4] = (byte) (l >>> 32);
            out[j + 5] = (byte) (l >>> 40);
            out[j + 6] = (byte) (l >>> 48);
            out[j + 7] = (byte) (l >>> 56);
        }
        return out;
    }

    private static byte[] longsToBytes(long[] longs, byte[] out, int offset) {
        return longsToBytes(longs, out, offset, longs.length);
    }

    private static long[] bytesToLongs(byte[] bytes) {
        long[] out = new long[bytes.length / Long.BYTES];
        for (int i = 0; i < out.length; i++) {
            out[i] = bytesToLongLE(bytes, i * Long.BYTES);
        }
        return out;
    }

    private static long bytesToLongLE(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff))
            | (((long) (bytes[offset + 1] & 0xff)) << 8)
            | (((long) (bytes[offset + 2] & 0xff)) << 16)
            | (((long) (bytes[offset + 3] & 0xff)) << 24)
            | (((long) (bytes[offset + 4] & 0xff)) << 32)
            | (((long) (bytes[offset + 5] & 0xff)) << 40)
            | (((long) (bytes[offset + 6] & 0xff)) << 48)
            | (((long) (bytes[offset + 7] & 0xff)) << 56);
    }
}
