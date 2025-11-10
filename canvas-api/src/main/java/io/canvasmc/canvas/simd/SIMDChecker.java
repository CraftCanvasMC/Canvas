package io.canvasmc.canvas.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class SIMDChecker {

    @Deprecated
    public static boolean canEnable(ComponentLogger logger) {
        try {
            if (SIMDDetection.getJavaVersion() < 17) {
                return false;
            } else {
                SIMDDetection.testRun = true;

                VectorSpecies<Integer> ISPEC = IntVector.SPECIES_PREFERRED;
                VectorSpecies<Float> FSPEC = FloatVector.SPECIES_PREFERRED;

                logger.info("Max SIMD vector size on this system is {} bits (int)", ISPEC.vectorBitSize());
                logger.info("Max SIMD vector size on this system is {} bits (float)", FSPEC.vectorBitSize());

                if (ISPEC.elementSize() < 2 || FSPEC.elementSize() < 2) {
                    logger.warn("SIMD is not properly supported on this system!");
                    return false;
                }

                return true;
            }
        } catch (NoClassDefFoundError | Exception ignored) {
        } // Basically, we don't do anything. This lets us detect if it's not functional and disable it.
        return false;
    }

}
