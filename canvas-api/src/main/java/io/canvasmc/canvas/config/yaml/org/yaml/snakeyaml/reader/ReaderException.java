//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.reader;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;

public class ReaderException extends YAMLException {
    private static final long serialVersionUID = 8710781187529689083L;
    private final String name;
    private final int codePoint;
    private final int position;

    public ReaderException(String name, int position, int codePoint, String message) {
        super(message);
        this.name = name;
        this.codePoint = codePoint;
        this.position = position;
    }

    public String getName() {
        return this.name;
    }

    public int getCodePoint() {
        return this.codePoint;
    }

    public int getPosition() {
        return this.position;
    }

    public String toString() {
        String s = new String(Character.toChars(this.codePoint));
        return "unacceptable code point '" + s + "' (0x" + Integer.toHexString(this.codePoint)
            .toUpperCase() + ") " + this.getMessage() + "\nin \"" + this.name + "\", position " + this.position;
    }
}
