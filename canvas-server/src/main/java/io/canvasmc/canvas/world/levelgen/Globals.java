package io.canvasmc.canvas.world.levelgen;

/**
 * Per-server static handle to the active 1024-bit feature seed.
 *
 * <p>Mirrors {@code su.plo.matter.Globals} from the Luminol/Matter
 * reference: worldgen call sites read {@link #worldSeed} directly without
 * threading the level into every method signature. The handle is set once
 * at world bootstrap via {@link #setupGlobals} and is read on the hot path
 * by {@link WorldgenCryptoRandom}.
 *
 * <h2>Thread-safety</h2>
 * The reference is {@code volatile}: writes happen-before all subsequent
 * reads. Reading a {@code long[]} reference is atomic, and the array
 * contents are populated before publication via {@link #setupGlobals},
 * so worldgen threads always observe a fully-constructed seed.
 */
public final class Globals {

    /**
     * Active 1024-bit feature seed. {@code null} when secure seed protection
     * is disabled. Hot-path callers should hoist this read into a local.
     */
    public static volatile long[] worldSeed;

    /**
     * Cached {@link Blake3.Prf} key (8 little-endian words) for
     * {@link #worldSeed}. Recomputed whenever the seed changes.
     */
    public static volatile int[] worldSeedPrfKey;

    private Globals() {
    }

    /**
     * Reads the feature seed from the supplied level (a Mojang
     * {@code ServerLevel} or anything that can produce one) and publishes
     * it to the static fields. Idempotent for identical seeds.
     */
    public static void setupGlobals(long[] featureSeed) {
        if (featureSeed == null) {
            worldSeed = null;
            worldSeedPrfKey = null;
            return;
        }
        if (!HashingV2.isValid(featureSeed)) {
            throw new IllegalArgumentException("featureSeed must be " + HashingV2.FEATURE_SEED_LONGS + " longs");
        }
        long[] previous = worldSeed;
        if (previous != null && previous.length == featureSeed.length) {
            boolean same = true;
            for (int i = 0; i < previous.length; i++) {
                if (previous[i] != featureSeed[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }
        worldSeedPrfKey = HashingV2.prfKeyWords(featureSeed);
        worldSeed = featureSeed.clone();
    }

    /**
     * Convenience: parses {@code featureSeedString} (comma-separated 16
     * decimals) and installs it. Returns the parsed seed, or generates a
     * fresh one if parsing fails. The caller is expected to persist the
     * returned value to disk so the world stays stable.
     */
    public static long[] parseAndInstall(String featureSeedString) {
        long[] parsed = HashingV2.parseFeatureSeed(featureSeedString);
        if (parsed == null) {
            parsed = HashingV2.createRandomFeatureSeed();
        }
        setupGlobals(parsed);
        return parsed;
    }

    /**
     * Returns true when secure seed protection is active for the current
     * world (i.e. {@link #worldSeed} has been installed).
     */
    public static boolean isActive() {
        return worldSeed != null;
    }
}
