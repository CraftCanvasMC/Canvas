//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public class DuplicateKeyException extends ConstructorException {
    protected DuplicateKeyException(Mark contextMark, Object key, Mark problemMark) {
        super("while constructing a mapping", contextMark, "found duplicate key " + key, problemMark);
    }
}
