//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error;

public class MarkedYAMLException extends YAMLException {
    private static final long serialVersionUID = -9119388488683035101L;
    private final String context;
    private final io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark contextMark;
    private final String problem;
    private final io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark problemMark;
    private final String note;

    protected MarkedYAMLException(String context, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark contextMark, String problem, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark problemMark, String note) {
        this(context, contextMark, problem, problemMark, note, null);
    }

    protected MarkedYAMLException(String context, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark contextMark, String problem, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark problemMark, String note, Throwable cause) {
        super(context + "; " + problem + "; " + problemMark, cause);
        this.context = context;
        this.contextMark = contextMark;
        this.problem = problem;
        this.problemMark = problemMark;
        this.note = note;
    }

    protected MarkedYAMLException(String context, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark contextMark, String problem, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark problemMark) {
        this(context, contextMark, problem, problemMark, null, null);
    }

    protected MarkedYAMLException(String context, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark contextMark, String problem, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark problemMark, Throwable cause) {
        this(context, contextMark, problem, problemMark, null, cause);
    }

    public String getMessage() {
        return this.toString();
    }

    public String toString() {
        StringBuilder lines = new StringBuilder();
        if (this.context != null) {
            lines.append(this.context);
            lines.append("\n");
        }

        if (this.contextMark != null && (this.problem == null || this.problemMark == null || this.contextMark.getName()
                                                                                                             .equals(this.problemMark.getName()) || this.contextMark.getLine() != this.problemMark.getLine() || this.contextMark.getColumn() != this.problemMark.getColumn())) {
            lines.append(this.contextMark);
            lines.append("\n");
        }

        if (this.problem != null) {
            lines.append(this.problem);
            lines.append("\n");
        }

        if (this.problemMark != null) {
            lines.append(this.problemMark);
            lines.append("\n");
        }

        if (this.note != null) {
            lines.append(this.note);
            lines.append("\n");
        }

        return lines.toString();
    }

    public String getContext() {
        return this.context;
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark getContextMark() {
        return this.contextMark;
    }

    public String getProblem() {
        return this.problem;
    }

    public Mark getProblemMark() {
        return this.problemMark;
    }
}
