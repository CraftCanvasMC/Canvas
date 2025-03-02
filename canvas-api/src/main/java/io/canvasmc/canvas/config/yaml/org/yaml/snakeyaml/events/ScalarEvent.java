//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public final class ScalarEvent extends NodeEvent {
    private final String tag;
    private final ScalarStyle style;
    private final String value;
    private final ImplicitTuple implicit;

    public ScalarEvent(String anchor, String tag, ImplicitTuple implicit, String value, Mark startMark, Mark endMark, ScalarStyle style) {
        super(anchor, startMark, endMark);
        this.tag = tag;
        this.implicit = implicit;
        if (value == null) {
            throw new NullPointerException("Value must be provided.");
        } else {
            this.value = value;
            if (style == null) {
                throw new NullPointerException("Style must be provided.");
            } else {
                this.style = style;
            }
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ScalarEvent(String anchor, String tag, ImplicitTuple implicit, String value, Mark startMark, Mark endMark, Character style) {
        this(anchor, tag, implicit, value, startMark, endMark, ScalarStyle.createStyle(style));
    }

    public String getTag() {
        return this.tag;
    }

    public ScalarStyle getScalarStyle() {
        return this.style;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Character getStyle() {
        return this.style.getChar();
    }

    public String getValue() {
        return this.value;
    }

    public ImplicitTuple getImplicit() {
        return this.implicit;
    }

    protected String getArguments() {
        return super.getArguments() + ", tag=" + this.tag + ", " + this.implicit + ", value=" + this.value;
    }

    public ID getEventId() {
        return ID.Scalar;
    }

    public boolean isPlain() {
        return this.style == ScalarStyle.PLAIN;
    }
}
