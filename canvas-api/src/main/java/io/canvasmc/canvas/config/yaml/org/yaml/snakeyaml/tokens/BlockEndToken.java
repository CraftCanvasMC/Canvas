//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public final class BlockEndToken extends io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token {
    public BlockEndToken(Mark startMark, Mark endMark) {
        super(startMark, endMark);
    }

    public ID getTokenId() {
        return ID.BlockEnd;
    }
}
