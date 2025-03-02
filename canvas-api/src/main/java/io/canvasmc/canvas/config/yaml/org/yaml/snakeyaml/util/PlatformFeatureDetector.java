//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util;

public class PlatformFeatureDetector {
    private Boolean isRunningOnAndroid = null;

    public PlatformFeatureDetector() {
    }

    public boolean isRunningOnAndroid() {
        if (this.isRunningOnAndroid == null) {
            String name = System.getProperty("java.runtime.name");
            this.isRunningOnAndroid = name != null && name.startsWith("Android Runtime");
        }

        return this.isRunningOnAndroid;
    }
}
