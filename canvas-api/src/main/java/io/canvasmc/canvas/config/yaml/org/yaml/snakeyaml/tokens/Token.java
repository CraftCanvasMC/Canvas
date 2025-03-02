//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;

public abstract class Token {
    private final Mark startMark;
    private final Mark endMark;

    public Token(Mark startMark, Mark endMark) {
        if (startMark != null && endMark != null) {
            this.startMark = startMark;
            this.endMark = endMark;
        } else {
            throw new YAMLException("Token requires marks.");
        }
    }

    public Mark getStartMark() {
        return this.startMark;
    }

    public Mark getEndMark() {
        return this.endMark;
    }

    public abstract ID getTokenId();

    public enum ID {
        Alias("<alias>"),
        Anchor("<anchor>"),
        BlockEnd("<block end>"),
        BlockEntry("-"),
        BlockMappingStart("<block mapping start>"),
        BlockSequenceStart("<block sequence start>"),
        Directive("<directive>"),
        DocumentEnd("<document end>"),
        DocumentStart("<document start>"),
        FlowEntry(","),
        FlowMappingEnd("}"),
        FlowMappingStart("{"),
        FlowSequenceEnd("]"),
        FlowSequenceStart("["),
        Key("?"),
        Scalar("<scalar>"),
        StreamEnd("<stream end>"),
        StreamStart("<stream start>"),
        Tag("<tag>"),
        Value(":"),
        Whitespace("<whitespace>"),
        Comment("#"),
        Error("<error>");

        private final String description;

        ID(String s) {
            this.description = s;
        }

        public String toString() {
            return this.description;
        }
    }
}
