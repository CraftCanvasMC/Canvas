//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import java.util.List;

public final class DirectiveToken<T> extends io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token {
    private final String name;
    private final List<T> value;

    public DirectiveToken(String name, List<T> value, Mark startMark, Mark endMark) {
        super(startMark, endMark);
        this.name = name;
        if (value != null && value.size() != 2) {
            throw new YAMLException("Two strings must be provided instead of " + value.size());
        } else {
            this.value = value;
        }
    }

    public String getName() {
        return this.name;
    }

    public List<T> getValue() {
        return this.value;
    }

    public ID getTokenId() {
        return ID.Directive;
    }
}
