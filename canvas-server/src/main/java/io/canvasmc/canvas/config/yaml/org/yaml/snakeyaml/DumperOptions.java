//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.serializer.AnchorGenerator;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.serializer.NumberAnchorGenerator;
import java.util.Map;
import java.util.TimeZone;

public class DumperOptions {
    private ScalarStyle defaultStyle;
    private FlowStyle defaultFlowStyle;
    private boolean canonical;
    private boolean allowUnicode;
    private boolean allowReadOnlyProperties;
    private int indent;
    private int indicatorIndent;
    private boolean indentWithIndicator;
    private int bestWidth;
    private boolean splitLines;
    private LineBreak lineBreak;
    private boolean explicitStart;
    private boolean explicitEnd;
    private TimeZone timeZone;
    private int maxSimpleKeyLength;
    private NonPrintableStyle nonPrintableStyle;
    private Version version;
    private Map<String, String> tags;
    private Boolean prettyFlow;
    private AnchorGenerator anchorGenerator;

    public DumperOptions() {
        this.defaultStyle = ScalarStyle.PLAIN;
        this.defaultFlowStyle = FlowStyle.AUTO;
        this.canonical = false;
        this.allowUnicode = true;
        this.allowReadOnlyProperties = false;
        this.indent = 2;
        this.indicatorIndent = 0;
        this.indentWithIndicator = false;
        this.bestWidth = 80;
        this.splitLines = true;
        this.lineBreak = LineBreak.UNIX;
        this.explicitStart = false;
        this.explicitEnd = false;
        this.timeZone = null;
        this.maxSimpleKeyLength = 128;
        this.nonPrintableStyle = NonPrintableStyle.BINARY;
        this.version = null;
        this.tags = null;
        this.prettyFlow = false;
        this.anchorGenerator = new NumberAnchorGenerator(0);
    }

    public boolean isAllowUnicode() {
        return this.allowUnicode;
    }

    public void setAllowUnicode(boolean allowUnicode) {
        this.allowUnicode = allowUnicode;
    }

    public ScalarStyle getDefaultScalarStyle() {
        return this.defaultStyle;
    }

    public void setDefaultScalarStyle(ScalarStyle defaultStyle) {
        if (defaultStyle == null) {
            throw new NullPointerException("Use ScalarStyle enum.");
        } else {
            this.defaultStyle = defaultStyle;
        }
    }

    public int getIndent() {
        return this.indent;
    }

    public void setIndent(int indent) {
        if (indent < 1) {
            throw new YAMLException("Indent must be at least 1");
        } else if (indent > 10) {
            throw new YAMLException("Indent must be at most 10");
        } else {
            this.indent = indent;
        }
    }

    public int getIndicatorIndent() {
        return this.indicatorIndent;
    }

    public void setIndicatorIndent(int indicatorIndent) {
        if (indicatorIndent < 0) {
            throw new YAMLException("Indicator indent must be non-negative.");
        } else if (indicatorIndent > 9) {
            throw new YAMLException("Indicator indent must be at most Emitter.MAX_INDENT-1: 9");
        } else {
            this.indicatorIndent = indicatorIndent;
        }
    }

    public boolean getIndentWithIndicator() {
        return this.indentWithIndicator;
    }

    public void setIndentWithIndicator(boolean indentWithIndicator) {
        this.indentWithIndicator = indentWithIndicator;
    }

    public Version getVersion() {
        return this.version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public boolean isCanonical() {
        return this.canonical;
    }

    public void setCanonical(boolean canonical) {
        this.canonical = canonical;
    }

    public boolean isPrettyFlow() {
        return this.prettyFlow;
    }

    public void setPrettyFlow(boolean prettyFlow) {
        this.prettyFlow = prettyFlow;
    }

    public int getWidth() {
        return this.bestWidth;
    }

    public void setWidth(int bestWidth) {
        this.bestWidth = bestWidth;
    }

    public boolean getSplitLines() {
        return this.splitLines;
    }

    public void setSplitLines(boolean splitLines) {
        this.splitLines = splitLines;
    }

    public LineBreak getLineBreak() {
        return this.lineBreak;
    }

    public void setLineBreak(LineBreak lineBreak) {
        if (lineBreak == null) {
            throw new NullPointerException("Specify line break.");
        } else {
            this.lineBreak = lineBreak;
        }
    }

    public FlowStyle getDefaultFlowStyle() {
        return this.defaultFlowStyle;
    }

    public void setDefaultFlowStyle(FlowStyle defaultFlowStyle) {
        if (defaultFlowStyle == null) {
            throw new NullPointerException("Use FlowStyle enum.");
        } else {
            this.defaultFlowStyle = defaultFlowStyle;
        }
    }

    public boolean isExplicitStart() {
        return this.explicitStart;
    }

    public void setExplicitStart(boolean explicitStart) {
        this.explicitStart = explicitStart;
    }

    public boolean isExplicitEnd() {
        return this.explicitEnd;
    }

    public void setExplicitEnd(boolean explicitEnd) {
        this.explicitEnd = explicitEnd;
    }

    public Map<String, String> getTags() {
        return this.tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public boolean isAllowReadOnlyProperties() {
        return this.allowReadOnlyProperties;
    }

    public void setAllowReadOnlyProperties(boolean allowReadOnlyProperties) {
        this.allowReadOnlyProperties = allowReadOnlyProperties;
    }

    public TimeZone getTimeZone() {
        return this.timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public AnchorGenerator getAnchorGenerator() {
        return this.anchorGenerator;
    }

    public void setAnchorGenerator(AnchorGenerator anchorGenerator) {
        this.anchorGenerator = anchorGenerator;
    }

    public int getMaxSimpleKeyLength() {
        return this.maxSimpleKeyLength;
    }

    public void setMaxSimpleKeyLength(int maxSimpleKeyLength) {
        if (maxSimpleKeyLength > 1024) {
            throw new YAMLException("The simple key must not span more than 1024 stream characters. See https://yaml.org/spec/1.1/#id934537");
        } else {
            this.maxSimpleKeyLength = maxSimpleKeyLength;
        }
    }

    public NonPrintableStyle getNonPrintableStyle() {
        return this.nonPrintableStyle;
    }

    public void setNonPrintableStyle(NonPrintableStyle style) {
        this.nonPrintableStyle = style;
    }

    public enum ScalarStyle {
        DOUBLE_QUOTED('"'),
        SINGLE_QUOTED('\''),
        LITERAL('|'),
        FOLDED('>'),
        PLAIN(null);

        private final Character styleChar;

        ScalarStyle(Character style) {
            this.styleChar = style;
        }

        public static ScalarStyle createStyle(Character style) {
            if (style == null) {
                return PLAIN;
            } else {
                switch (style) {
                    case '"':
                        return DOUBLE_QUOTED;
                    case '\'':
                        return SINGLE_QUOTED;
                    case '>':
                        return FOLDED;
                    case '|':
                        return LITERAL;
                    default:
                        throw new YAMLException("Unknown scalar style character: " + style);
                }
            }
        }

        public Character getChar() {
            return this.styleChar;
        }

        public String toString() {
            return "Scalar style: '" + this.styleChar + "'";
        }
    }

    public enum FlowStyle {
        FLOW(Boolean.TRUE),
        BLOCK(Boolean.FALSE),
        AUTO(null);

        private final Boolean styleBoolean;

        FlowStyle(Boolean flowStyle) {
            this.styleBoolean = flowStyle;
        }

        /**
         * @deprecated
         */
        @Deprecated
        public static FlowStyle fromBoolean(Boolean flowStyle) {
            return flowStyle == null ? AUTO : (flowStyle ? FLOW : BLOCK);
        }

        public Boolean getStyleBoolean() {
            return this.styleBoolean;
        }

        public String toString() {
            return "Flow style: '" + this.styleBoolean + "'";
        }
    }

    public enum LineBreak {
        WIN("\r\n"),
        MAC("\r"),
        UNIX("\n");

        private final String lineBreak;

        LineBreak(String lineBreak) {
            this.lineBreak = lineBreak;
        }

        public static LineBreak getPlatformLineBreak() {
            String platformLineBreak = System.getProperty("line.separator");

            for (LineBreak lb : values()) {
                if (lb.lineBreak.equals(platformLineBreak)) {
                    return lb;
                }
            }

            return UNIX;
        }

        public String getString() {
            return this.lineBreak;
        }

        public String toString() {
            return "Line break: " + this.name();
        }
    }

    public enum Version {
        V1_0(new Integer[]{1, 0}),
        V1_1(new Integer[]{1, 1});

        private final Integer[] version;

        Version(Integer[] version) {
            this.version = version;
        }

        public int major() {
            return this.version[0];
        }

        public int minor() {
            return this.version[1];
        }

        public String getRepresentation() {
            return this.version[0] + "." + this.version[1];
        }

        public String toString() {
            return "Version: " + this.getRepresentation();
        }
    }

    public enum NonPrintableStyle {
        BINARY,
        ESCAPE;

        NonPrintableStyle() {
        }
    }
}
