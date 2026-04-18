package io.canvasmc.canvas.simd;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.jspecify.annotations.NullMarked;

/**
 * Hardware-accelerated physics calculations using SIMD (Single Instruction, Multiple Data).
 */
@NullMarked
public final class SIMDPhysics {

    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;

    /**
     * Calculates distances between a source point and multiple target points using SIMD.
     *
     * @param sourceX source X coordinate
     * @param sourceZ source Z coordinate
     * @param targetXs array of target X coordinates
     * @param targetZs array of target Z coordinates
     * @param results output array for squared distances
     */
    public static void calculateSquaredDistancesXZ(double sourceX, double sourceZ, double[] targetXs, double[] targetZs, double[] results) {
        int length = targetXs.length;
        int bound = SPECIES.loopBound(length);

        DoubleVector vSourceX = DoubleVector.broadcast(SPECIES, sourceX);
        DoubleVector vSourceZ = DoubleVector.broadcast(SPECIES, sourceZ);

        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            DoubleVector vTargetX = DoubleVector.fromArray(SPECIES, targetXs, i);
            DoubleVector vTargetZ = DoubleVector.fromArray(SPECIES, targetZs, i);

            DoubleVector dx = vTargetX.sub(vSourceX);
            DoubleVector dz = vTargetZ.sub(vSourceZ);

            DoubleVector distSq = dx.mul(dx).add(dz.mul(dz));
            distSq.intoArray(results, i);
        }

        // Clean up remaining elements
        for (; i < length; i++) {
            double dx = targetXs[i] - sourceX;
            double dz = targetZs[i] - sourceZ;
            results[i] = dx * dx + dz * dz;
        }
    }

    private SIMDPhysics() {}
}
