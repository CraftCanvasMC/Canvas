//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens;

public final class TagTuple {
    private final String handle;
    private final String suffix;

    public TagTuple(String handle, String suffix) {
        if (suffix == null) {
            throw new NullPointerException("Suffix must be provided.");
        } else {
            this.handle = handle;
            this.suffix = suffix;
        }
    }

    public String getHandle() {
        return this.handle;
    }

    public String getSuffix() {
        return this.suffix;
    }
}
