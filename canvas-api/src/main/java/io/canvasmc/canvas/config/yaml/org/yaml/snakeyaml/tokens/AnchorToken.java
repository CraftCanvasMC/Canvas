//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public final class AnchorToken extends io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token {
    private final String value;

    public AnchorToken(String value, Mark startMark, Mark endMark) {
        super(startMark, endMark);
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public ID getTokenId() {
        return ID.Anchor;
    }
}
