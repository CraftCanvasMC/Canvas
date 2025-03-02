//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.MarkedYAMLException;

public class ConstructorException extends MarkedYAMLException {
    private static final long serialVersionUID = -8816339931365239910L;

    protected ConstructorException(String context, Mark contextMark, String problem, Mark problemMark, Throwable cause) {
        super(context, contextMark, problem, problemMark, cause);
    }

    protected ConstructorException(String context, Mark contextMark, String problem, Mark problemMark) {
        this(context, contextMark, problem, problemMark, null);
    }
}
