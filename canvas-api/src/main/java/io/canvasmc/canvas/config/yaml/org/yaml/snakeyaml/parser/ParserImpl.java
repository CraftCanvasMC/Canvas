//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.parser;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.Version;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.AliasEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.DocumentEndEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.DocumentStartEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.Event;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.ImplicitTuple;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.MappingEndEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.MappingStartEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.ScalarEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.SequenceEndEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.SequenceStartEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.StreamEndEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.StreamStartEvent;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.reader.StreamReader;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Scanner;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.ScannerImpl;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.AliasToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.AnchorToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.BlockEntryToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.DirectiveToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.ScalarToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.StreamEndToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.StreamStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.TagToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.TagTuple;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token.ID;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util.ArrayStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParserImpl implements Parser {
    private static final Map<String, String> DEFAULT_TAGS = new HashMap();

    static {
        DEFAULT_TAGS.put("!", "!");
        DEFAULT_TAGS.put("!!", "tag:yaml.org,2002:");
    }

    protected final Scanner scanner;
    private final ArrayStack<Production> states;
    private final ArrayStack<Mark> marks;
    private Event currentEvent;
    private Production state;
    private VersionTagsTuple directives;

    public ParserImpl(StreamReader reader) {
        this(new ScannerImpl(reader));
    }

    public ParserImpl(Scanner scanner) {
        this.scanner = scanner;
        this.currentEvent = null;
        this.directives = new VersionTagsTuple(null, new HashMap(DEFAULT_TAGS));
        this.states = new ArrayStack(100);
        this.marks = new ArrayStack(10);
        this.state = new ParseStreamStart();
    }

    public boolean checkEvent(Event.ID choice) {
        this.peekEvent();
        return this.currentEvent != null && this.currentEvent.is(choice);
    }

    public Event peekEvent() {
        if (this.currentEvent == null && this.state != null) {
            this.currentEvent = this.state.produce();
        }

        return this.currentEvent;
    }

    public Event getEvent() {
        this.peekEvent();
        Event value = this.currentEvent;
        this.currentEvent = null;
        return value;
    }

    private VersionTagsTuple processDirectives() {
        Version yamlVersion = null;
        HashMap<String, String> tagHandles = new HashMap();

        while (this.scanner.checkToken(ID.Directive)) {
            DirectiveToken token = (DirectiveToken) this.scanner.getToken();
            if (token.getName().equals("YAML")) {
                if (yamlVersion != null) {
                    throw new ParserException(null, null, "found duplicate YAML directive", token.getStartMark());
                }

                List<Integer> value = token.getValue();
                Integer major = value.get(0);
                if (major != 1) {
                    throw new ParserException(null, null, "found incompatible YAML document (version 1.* is required)", token.getStartMark());
                }

                Integer minor = value.get(1);
                switch (minor) {
                    case 0:
                        yamlVersion = Version.V1_0;
                        break;
                    default:
                        yamlVersion = Version.V1_1;
                }
            } else if (token.getName().equals("TAG")) {
                List<String> value = token.getValue();
                String handle = value.get(0);
                String prefix = value.get(1);
                if (tagHandles.containsKey(handle)) {
                    throw new ParserException(null, null, "duplicate tag handle " + handle, token.getStartMark());
                }

                tagHandles.put(handle, prefix);
            }
        }

        if (yamlVersion != null || !tagHandles.isEmpty()) {
            for (String key : DEFAULT_TAGS.keySet()) {
                if (!tagHandles.containsKey(key)) {
                    tagHandles.put(key, DEFAULT_TAGS.get(key));
                }
            }

            this.directives = new VersionTagsTuple(yamlVersion, tagHandles);
        }

        return this.directives;
    }

    private Event parseFlowNode() {
        return this.parseNode(false, false);
    }

    private Event parseBlockNodeOrIndentlessSequence() {
        return this.parseNode(true, true);
    }

    private Event parseNode(boolean block, boolean indentlessSequence) {
        Mark startMark = null;
        Mark endMark = null;
        Mark tagMark = null;
        Event event;
        if (this.scanner.checkToken(ID.Alias)) {
            AliasToken token = (AliasToken) this.scanner.getToken();
            event = new AliasEvent(token.getValue(), token.getStartMark(), token.getEndMark());
            this.state = this.states.pop();
        } else {
            String anchor = null;
            TagTuple tagTokenTag = null;
            if (this.scanner.checkToken(ID.Anchor)) {
                AnchorToken token = (AnchorToken) this.scanner.getToken();
                startMark = token.getStartMark();
                endMark = token.getEndMark();
                anchor = token.getValue();
                if (this.scanner.checkToken(ID.Tag)) {
                    TagToken tagToken = (TagToken) this.scanner.getToken();
                    tagMark = tagToken.getStartMark();
                    endMark = tagToken.getEndMark();
                    tagTokenTag = tagToken.getValue();
                }
            } else if (this.scanner.checkToken(ID.Tag)) {
                TagToken tagToken = (TagToken) this.scanner.getToken();
                startMark = tagToken.getStartMark();
                tagMark = startMark;
                endMark = tagToken.getEndMark();
                tagTokenTag = tagToken.getValue();
                if (this.scanner.checkToken(ID.Anchor)) {
                    AnchorToken token = (AnchorToken) this.scanner.getToken();
                    endMark = token.getEndMark();
                    anchor = token.getValue();
                }
            }

            String tag = null;
            if (tagTokenTag != null) {
                String handle = tagTokenTag.getHandle();
                String suffix = tagTokenTag.getSuffix();
                if (handle != null) {
                    if (!this.directives.getTags().containsKey(handle)) {
                        throw new ParserException("while parsing a node", startMark, "found undefined tag handle " + handle, tagMark);
                    }

                    tag = this.directives.getTags().get(handle) + suffix;
                } else {
                    tag = suffix;
                }
            }

            if (startMark == null) {
                startMark = this.scanner.peekToken().getStartMark();
                endMark = startMark;
            }

            event = null;
            boolean implicit = tag == null || tag.equals("!");
            if (indentlessSequence && this.scanner.checkToken(ID.BlockEntry)) {
                endMark = this.scanner.peekToken().getEndMark();
                event = new SequenceStartEvent(anchor, tag, implicit, startMark, endMark, FlowStyle.BLOCK);
                this.state = new ParseIndentlessSequenceEntry();
            } else if (this.scanner.checkToken(ID.Scalar)) {
                ScalarToken token = (ScalarToken) this.scanner.getToken();
                endMark = token.getEndMark();
                ImplicitTuple implicitValues;
                if ((!token.getPlain() || tag != null) && !"!".equals(tag)) {
                    if (tag == null) {
                        implicitValues = new ImplicitTuple(false, true);
                    } else {
                        implicitValues = new ImplicitTuple(false, false);
                    }
                } else {
                    implicitValues = new ImplicitTuple(true, false);
                }

                event = new ScalarEvent(anchor, tag, implicitValues, token.getValue(), startMark, endMark, token.getStyle());
                this.state = this.states.pop();
            } else if (this.scanner.checkToken(ID.FlowSequenceStart)) {
                endMark = this.scanner.peekToken().getEndMark();
                event = new SequenceStartEvent(anchor, tag, implicit, startMark, endMark, FlowStyle.FLOW);
                this.state = new ParseFlowSequenceFirstEntry();
            } else if (this.scanner.checkToken(ID.FlowMappingStart)) {
                endMark = this.scanner.peekToken().getEndMark();
                event = new MappingStartEvent(anchor, tag, implicit, startMark, endMark, FlowStyle.FLOW);
                this.state = new ParseFlowMappingFirstKey();
            } else if (block && this.scanner.checkToken(ID.BlockSequenceStart)) {
                endMark = this.scanner.peekToken().getStartMark();
                event = new SequenceStartEvent(anchor, tag, implicit, startMark, endMark, FlowStyle.BLOCK);
                this.state = new ParseBlockSequenceFirstEntry();
            } else if (block && this.scanner.checkToken(ID.BlockMappingStart)) {
                endMark = this.scanner.peekToken().getStartMark();
                event = new MappingStartEvent(anchor, tag, implicit, startMark, endMark, FlowStyle.BLOCK);
                this.state = new ParseBlockMappingFirstKey();
            } else {
                if (anchor == null && tag == null) {
                    String node;
                    if (block) {
                        node = "block";
                    } else {
                        node = "flow";
                    }

                    Token token = this.scanner.peekToken();
                    throw new ParserException("while parsing a " + node + " node", startMark, "expected the node content, but found '" + token.getTokenId() + "'", token.getStartMark());
                }

                event = new ScalarEvent(anchor, tag, new ImplicitTuple(implicit, false), "", startMark, endMark, ScalarStyle.PLAIN);
                this.state = this.states.pop();
            }
        }

        return event;
    }

    private Event processEmptyScalar(Mark mark) {
        return new ScalarEvent(null, null, new ImplicitTuple(true, false), "", mark, mark, ScalarStyle.PLAIN);
    }

    private class ParseStreamStart implements Production {
        private ParseStreamStart() {
        }

        public Event produce() {
            StreamStartToken token = (StreamStartToken) ParserImpl.this.scanner.getToken();
            Event event = new StreamStartEvent(token.getStartMark(), token.getEndMark());
            ParserImpl.this.state = ParserImpl.this.new ParseImplicitDocumentStart();
            return event;
        }
    }

    private class ParseImplicitDocumentStart implements Production {
        private ParseImplicitDocumentStart() {
        }

        public Event produce() {
            if (!ParserImpl.this.scanner.checkToken(ID.Directive, ID.DocumentStart, ID.StreamEnd)) {
                ParserImpl.this.directives = new VersionTagsTuple(null, ParserImpl.DEFAULT_TAGS);
                Token token = ParserImpl.this.scanner.peekToken();
                Mark startMark = token.getStartMark();
                Event event = new DocumentStartEvent(startMark, startMark, false, null, null);
                ParserImpl.this.states.push(ParserImpl.this.new ParseDocumentEnd());
                ParserImpl.this.state = ParserImpl.this.new ParseBlockNode();
                return event;
            } else {
                Production p = ParserImpl.this.new ParseDocumentStart();
                return p.produce();
            }
        }
    }

    private class ParseDocumentStart implements Production {
        private ParseDocumentStart() {
        }

        public Event produce() {
            while (ParserImpl.this.scanner.checkToken(ID.DocumentEnd)) {
                ParserImpl.this.scanner.getToken();
            }

            Event event;
            if (!ParserImpl.this.scanner.checkToken(ID.StreamEnd)) {
                Token token = ParserImpl.this.scanner.peekToken();
                Mark startMark = token.getStartMark();
                VersionTagsTuple tuple = ParserImpl.this.processDirectives();
                if (!ParserImpl.this.scanner.checkToken(ID.DocumentStart)) {
                    throw new ParserException(null, null, "expected '<document start>', but found '" + ParserImpl.this.scanner.peekToken()
                        .getTokenId() + "'", ParserImpl.this.scanner.peekToken()
                        .getStartMark());
                }

                token = ParserImpl.this.scanner.getToken();
                Mark endMark = token.getEndMark();
                event = new DocumentStartEvent(startMark, endMark, true, tuple.getVersion(), tuple.getTags());
                ParserImpl.this.states.push(ParserImpl.this.new ParseDocumentEnd());
                ParserImpl.this.state = ParserImpl.this.new ParseDocumentContent();
            } else {
                StreamEndToken token = (StreamEndToken) ParserImpl.this.scanner.getToken();
                event = new StreamEndEvent(token.getStartMark(), token.getEndMark());
                if (!ParserImpl.this.states.isEmpty()) {
                    throw new YAMLException("Unexpected end of stream. States left: " + ParserImpl.this.states);
                }

                if (!ParserImpl.this.marks.isEmpty()) {
                    throw new YAMLException("Unexpected end of stream. Marks left: " + ParserImpl.this.marks);
                }

                ParserImpl.this.state = null;
            }

            return event;
        }
    }

    private class ParseDocumentEnd implements Production {
        private ParseDocumentEnd() {
        }

        public Event produce() {
            Token token = ParserImpl.this.scanner.peekToken();
            Mark startMark = token.getStartMark();
            Mark endMark = startMark;
            boolean explicit = false;
            if (ParserImpl.this.scanner.checkToken(ID.DocumentEnd)) {
                token = ParserImpl.this.scanner.getToken();
                endMark = token.getEndMark();
                explicit = true;
            }

            Event event = new DocumentEndEvent(startMark, endMark, explicit);
            ParserImpl.this.state = ParserImpl.this.new ParseDocumentStart();
            return event;
        }
    }

    private class ParseDocumentContent implements Production {
        private ParseDocumentContent() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.Directive, ID.DocumentStart, ID.DocumentEnd, ID.StreamEnd)) {
                Event event = ParserImpl.this.processEmptyScalar(ParserImpl.this.scanner.peekToken().getStartMark());
                ParserImpl.this.state = ParserImpl.this.states.pop();
                return event;
            } else {
                Production p = ParserImpl.this.new ParseBlockNode();
                return p.produce();
            }
        }
    }

    private class ParseBlockNode implements Production {
        private ParseBlockNode() {
        }

        public Event produce() {
            return ParserImpl.this.parseNode(true, false);
        }
    }

    private class ParseBlockSequenceFirstEntry implements Production {
        private ParseBlockSequenceFirstEntry() {
        }

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();
            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseBlockSequenceEntry()).produce();
        }
    }

    private class ParseBlockSequenceEntry implements Production {
        private ParseBlockSequenceEntry() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.BlockEntry)) {
                BlockEntryToken token = (BlockEntryToken) ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(ID.BlockEntry, ID.BlockEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseBlockSequenceEntry());
                    return (ParserImpl.this.new ParseBlockNode()).produce();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseBlockSequenceEntry();
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else if (!ParserImpl.this.scanner.checkToken(ID.BlockEnd)) {
                Token token = ParserImpl.this.scanner.peekToken();
                throw new ParserException("while parsing a block collection", ParserImpl.this.marks.pop(), "expected <block end>, but found '" + token.getTokenId() + "'", token.getStartMark());
            } else {
                Token token = ParserImpl.this.scanner.getToken();
                Event event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());
                ParserImpl.this.state = ParserImpl.this.states.pop();
                ParserImpl.this.marks.pop();
                return event;
            }
        }
    }

    private class ParseIndentlessSequenceEntry implements Production {
        private ParseIndentlessSequenceEntry() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.BlockEntry)) {
                Token token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(ID.BlockEntry, ID.Key, ID.Value, ID.BlockEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseIndentlessSequenceEntry());
                    return (ParserImpl.this.new ParseBlockNode()).produce();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseIndentlessSequenceEntry();
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else {
                Token token = ParserImpl.this.scanner.peekToken();
                Event event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());
                ParserImpl.this.state = ParserImpl.this.states.pop();
                return event;
            }
        }
    }

    private class ParseBlockMappingFirstKey implements Production {
        private ParseBlockMappingFirstKey() {
        }

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();
            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseBlockMappingKey()).produce();
        }
    }

    private class ParseBlockMappingKey implements Production {
        private ParseBlockMappingKey() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.Key)) {
                Token token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(ID.Key, ID.Value, ID.BlockEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseBlockMappingValue());
                    return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingValue();
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else if (!ParserImpl.this.scanner.checkToken(ID.BlockEnd)) {
                Token token = ParserImpl.this.scanner.peekToken();
                throw new ParserException("while parsing a block mapping", ParserImpl.this.marks.pop(), "expected <block end>, but found '" + token.getTokenId() + "'", token.getStartMark());
            } else {
                Token token = ParserImpl.this.scanner.getToken();
                Event event = new MappingEndEvent(token.getStartMark(), token.getEndMark());
                ParserImpl.this.state = ParserImpl.this.states.pop();
                ParserImpl.this.marks.pop();
                return event;
            }
        }
    }

    private class ParseBlockMappingValue implements Production {
        private ParseBlockMappingValue() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.Value)) {
                Token token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(ID.Key, ID.Value, ID.BlockEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseBlockMappingKey());
                    return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingKey();
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingKey();
                Token token = ParserImpl.this.scanner.peekToken();
                return ParserImpl.this.processEmptyScalar(token.getStartMark());
            }
        }
    }

    private class ParseFlowSequenceFirstEntry implements Production {
        private ParseFlowSequenceFirstEntry() {
        }

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();
            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseFlowSequenceEntry(true)).produce();
        }
    }

    private class ParseFlowSequenceEntry implements Production {
        private boolean first = false;

        public ParseFlowSequenceEntry(boolean first) {
            this.first = first;
        }

        public Event produce() {
            if (!ParserImpl.this.scanner.checkToken(ID.FlowSequenceEnd)) {
                if (!this.first) {
                    if (!ParserImpl.this.scanner.checkToken(ID.FlowEntry)) {
                        Token token = ParserImpl.this.scanner.peekToken();
                        throw new ParserException("while parsing a flow sequence", ParserImpl.this.marks.pop(), "expected ',' or ']', but got " + token.getTokenId(), token.getStartMark());
                    }

                    ParserImpl.this.scanner.getToken();
                }

                if (ParserImpl.this.scanner.checkToken(ID.Key)) {
                    Token token = ParserImpl.this.scanner.peekToken();
                    Event event = new MappingStartEvent(null, null, true, token.getStartMark(), token.getEndMark(), FlowStyle.FLOW);
                    ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingKey();
                    return event;
                }

                if (!ParserImpl.this.scanner.checkToken(ID.FlowSequenceEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowSequenceEntry(false));
                    return ParserImpl.this.parseFlowNode();
                }
            }

            Token token = ParserImpl.this.scanner.getToken();
            Event event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());
            ParserImpl.this.state = ParserImpl.this.states.pop();
            ParserImpl.this.marks.pop();
            return event;
        }
    }

    private class ParseFlowSequenceEntryMappingKey implements Production {
        private ParseFlowSequenceEntryMappingKey() {
        }

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();
            if (!ParserImpl.this.scanner.checkToken(ID.Value, ID.FlowEntry, ID.FlowSequenceEnd)) {
                ParserImpl.this.states.push(ParserImpl.this.new ParseFlowSequenceEntryMappingValue());
                return ParserImpl.this.parseFlowNode();
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingValue();
                return ParserImpl.this.processEmptyScalar(token.getEndMark());
            }
        }
    }

    private class ParseFlowSequenceEntryMappingValue implements Production {
        private ParseFlowSequenceEntryMappingValue() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.Value)) {
                Token token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(ID.FlowEntry, ID.FlowSequenceEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowSequenceEntryMappingEnd());
                    return ParserImpl.this.parseFlowNode();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingEnd();
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingEnd();
                Token token = ParserImpl.this.scanner.peekToken();
                return ParserImpl.this.processEmptyScalar(token.getStartMark());
            }
        }
    }

    private class ParseFlowSequenceEntryMappingEnd implements Production {
        private ParseFlowSequenceEntryMappingEnd() {
        }

        public Event produce() {
            ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntry(false);
            Token token = ParserImpl.this.scanner.peekToken();
            return new MappingEndEvent(token.getStartMark(), token.getEndMark());
        }
    }

    private class ParseFlowMappingFirstKey implements Production {
        private ParseFlowMappingFirstKey() {
        }

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();
            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseFlowMappingKey(true)).produce();
        }
    }

    private class ParseFlowMappingKey implements Production {
        private boolean first = false;

        public ParseFlowMappingKey(boolean first) {
            this.first = first;
        }

        public Event produce() {
            if (!ParserImpl.this.scanner.checkToken(ID.FlowMappingEnd)) {
                if (!this.first) {
                    if (!ParserImpl.this.scanner.checkToken(ID.FlowEntry)) {
                        Token token = ParserImpl.this.scanner.peekToken();
                        throw new ParserException("while parsing a flow mapping", ParserImpl.this.marks.pop(), "expected ',' or '}', but got " + token.getTokenId(), token.getStartMark());
                    }

                    ParserImpl.this.scanner.getToken();
                }

                if (ParserImpl.this.scanner.checkToken(ID.Key)) {
                    Token token = ParserImpl.this.scanner.getToken();
                    if (!ParserImpl.this.scanner.checkToken(ID.Value, ID.FlowEntry, ID.FlowMappingEnd)) {
                        ParserImpl.this.states.push(ParserImpl.this.new ParseFlowMappingValue());
                        return ParserImpl.this.parseFlowNode();
                    }

                    ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingValue();
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }

                if (!ParserImpl.this.scanner.checkToken(ID.FlowMappingEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowMappingEmptyValue());
                    return ParserImpl.this.parseFlowNode();
                }
            }

            Token token = ParserImpl.this.scanner.getToken();
            Event event = new MappingEndEvent(token.getStartMark(), token.getEndMark());
            ParserImpl.this.state = ParserImpl.this.states.pop();
            ParserImpl.this.marks.pop();
            return event;
        }
    }

    private class ParseFlowMappingValue implements Production {
        private ParseFlowMappingValue() {
        }

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(ID.Value)) {
                Token token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(ID.FlowEntry, ID.FlowMappingEnd)) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowMappingKey(false));
                    return ParserImpl.this.parseFlowNode();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingKey(false);
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingKey(false);
                Token token = ParserImpl.this.scanner.peekToken();
                return ParserImpl.this.processEmptyScalar(token.getStartMark());
            }
        }
    }

    private class ParseFlowMappingEmptyValue implements Production {
        private ParseFlowMappingEmptyValue() {
        }

        public Event produce() {
            ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingKey(false);
            return ParserImpl.this.processEmptyScalar(ParserImpl.this.scanner.peekToken().getStartMark());
        }
    }
}
