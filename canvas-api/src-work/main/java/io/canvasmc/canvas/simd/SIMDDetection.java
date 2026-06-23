package io.canvasmc.canvas.simd;

import org.slf4j.Logger;

public class SIMDDetection {

    public static boolean isEnabled = false;
    public static boolean testRun = false;

    public static boolean canEnable(Logger logger) {
        try {
            return SIMDChecker.canEnable(logger);
        } catch (NoClassDefFoundError | Exception ignored) {
            return false;
        }
    }

    public static int getJavaVersion() {
        // https://stackoverflow.com/a/2591122
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        version = version.split("-")[0]; // Azul is stupid
        return Integer.parseInt(version);
    }

}
