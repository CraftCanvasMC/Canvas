package io.canvasmc.canvas.world.levelgen;

/**
 * Domain-separation tags for {@link WorldgenCryptoRandom}.
 *
 * <p>Each enum value translates to a unique 64-bit constant that is mixed
 * into the BLAKE3 PRF input. Two callers using the same coordinate but a
 * different salt get statistically independent randomness, which means
 * (for example) the placement RNG for a stronghold cannot be used to
 * predict the placement RNG for a desert temple.
 *
 * <p>The constants are stable: do not change them, because doing so would
 * regenerate every world that depends on a given salt.
 */
public enum Salt {

    UNDEFINED(0x4D6174746572_30L),               // "Matter\0"
    STRONGHOLDS(0x4D617474657253_74L),           // "MatterSt"
    GENERATE_FEATURE(0x4D617474657247_65L),      // "MatterGe"
    POTENTIONAL_FEATURE(0x4D617474657250_6FL),   // "MatterPo"
    JIGSAW_PLACEMENT(0x4D617474657221_4AL),      // "Matter!J"
    GEODE_FEATURE(0x4D617474657247_64L);         // "MatterGd"

    private final long magic;

    Salt(long magic) {
        this.magic = magic;
    }

    public long magic() {
        return this.magic;
    }
}
