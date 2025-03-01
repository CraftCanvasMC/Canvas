//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public final class ScalarToken extends io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token {
    private final String value;
    private final boolean plain;
    private final ScalarStyle style;

    public ScalarToken(String value, Mark startMark, Mark endMark, boolean plain) {
        this(value, plain, startMark, endMark, ScalarStyle.PLAIN);
    }

    public ScalarToken(String value, boolean plain, Mark startMark, Mark endMark, ScalarStyle style) {
        super(startMark, endMark);
        this.value = value;
        this.plain = plain;
        if (style == null) {
            throw new NullPointerException("Style must be provided.");
        } else {
            this.style = style;
        }
    }

    public boolean getPlain() {
        return this.plain;
    }

    public String getValue() {
        return this.value;
    }

    public ScalarStyle getStyle() {
        return this.style;
    }

    public ID getTokenId() {
        return ID.Scalar;
    }
}
