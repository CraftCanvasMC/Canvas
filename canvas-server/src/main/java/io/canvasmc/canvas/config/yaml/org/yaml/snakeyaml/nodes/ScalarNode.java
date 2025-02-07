//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public class ScalarNode extends Node {
    private final ScalarStyle style;
    private final String value;

    public ScalarNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, String value, Mark startMark, Mark endMark, ScalarStyle style) {
        this(tag, true, value, startMark, endMark, style);
    }

    public ScalarNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, boolean resolved, String value, Mark startMark, Mark endMark, ScalarStyle style) {
        super(tag, startMark, endMark);
        if (value == null) {
            throw new NullPointerException("value in a Node is required.");
        } else {
            this.value = value;
            if (style == null) {
                throw new NullPointerException("Scalar style must be provided.");
            } else {
                this.style = style;
                this.resolved = resolved;
            }
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ScalarNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, String value, Mark startMark, Mark endMark, Character style) {
        this(tag, value, startMark, endMark, ScalarStyle.createStyle(style));
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ScalarNode(Tag tag, boolean resolved, String value, Mark startMark, Mark endMark, Character style) {
        this(tag, resolved, value, startMark, endMark, ScalarStyle.createStyle(style));
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Character getStyle() {
        return this.style.getChar();
    }

    public ScalarStyle getScalarStyle() {
        return this.style;
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeId getNodeId() {
        return NodeId.scalar;
    }

    public String getValue() {
        return this.value;
    }

    public String toString() {
        return "<" + this.getClass().getName() + " (tag=" + this.getTag() + ", value=" + this.getValue() + ")>";
    }

    public boolean isPlain() {
        return this.style == ScalarStyle.PLAIN;
    }
}
