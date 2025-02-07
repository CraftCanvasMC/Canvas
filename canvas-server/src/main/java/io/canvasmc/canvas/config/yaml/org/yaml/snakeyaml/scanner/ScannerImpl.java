//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.reader.StreamReader;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.AliasToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.AnchorToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.BlockEndToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.BlockEntryToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.BlockMappingStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.BlockSequenceStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.DirectiveToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.DocumentEndToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.DocumentStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.FlowEntryToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.FlowMappingEndToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.FlowMappingStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.FlowSequenceEndToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.FlowSequenceStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.KeyToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.ScalarToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.StreamEndToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.StreamStartToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.TagToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.TagTuple;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.ValueToken;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util.ArrayStack;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util.UriEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ScannerImpl implements Scanner {
    public static final Map<Character, String> ESCAPE_REPLACEMENTS = new HashMap();
    public static final Map<Character, Integer> ESCAPE_CODES = new HashMap();
    private static final Pattern NOT_HEXA = Pattern.compile("[^0-9A-Fa-f]");
    static {
        ESCAPE_REPLACEMENTS.put('0', "\u0000");
        ESCAPE_REPLACEMENTS.put('a', "\u0007");
        ESCAPE_REPLACEMENTS.put('b', "\b");
        ESCAPE_REPLACEMENTS.put('t', "\t");
        ESCAPE_REPLACEMENTS.put('n', "\n");
        ESCAPE_REPLACEMENTS.put('v', "\u000b");
        ESCAPE_REPLACEMENTS.put('f', "\f");
        ESCAPE_REPLACEMENTS.put('r', "\r");
        ESCAPE_REPLACEMENTS.put('e', "\u001b");
        ESCAPE_REPLACEMENTS.put(' ', " ");
        ESCAPE_REPLACEMENTS.put('"', "\"");
        ESCAPE_REPLACEMENTS.put('\\', "\\");
        ESCAPE_REPLACEMENTS.put('N', "\u0085");
        ESCAPE_REPLACEMENTS.put('_', " ");
        ESCAPE_REPLACEMENTS.put('L', "\u2028");
        ESCAPE_REPLACEMENTS.put('P', "\u2029");
        ESCAPE_CODES.put('x', 2);
        ESCAPE_CODES.put('u', 4);
        ESCAPE_CODES.put('U', 8);
    }
    private final StreamReader reader;
    private final List<Token> tokens;
    private final ArrayStack<Integer> indents;
    private final Map<Integer, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey> possibleSimpleKeys;
    private boolean done = false;
    private int flowLevel = 0;
    private int tokensTaken = 0;
    private int indent = -1;
    private boolean allowSimpleKey = true;

    public ScannerImpl(StreamReader reader) {
        this.reader = reader;
        this.tokens = new ArrayList(100);
        this.indents = new ArrayStack(10);
        this.possibleSimpleKeys = new LinkedHashMap();
        this.fetchStreamStart();
    }

    public boolean checkToken(Token.ID... choices) {
        while (this.needMoreTokens()) {
            this.fetchMoreTokens();
        }

        if (!this.tokens.isEmpty()) {
            if (choices.length == 0) {
                return true;
            }

            Token.ID first = this.tokens.get(0).getTokenId();

            for (int i = 0; i < choices.length; ++i) {
                if (first == choices[i]) {
                    return true;
                }
            }
        }

        return false;
    }

    public Token peekToken() {
        while (this.needMoreTokens()) {
            this.fetchMoreTokens();
        }

        return this.tokens.get(0);
    }

    public Token getToken() {
        ++this.tokensTaken;
        return this.tokens.remove(0);
    }

    private boolean needMoreTokens() {
        if (this.done) {
            return false;
        } else if (this.tokens.isEmpty()) {
            return true;
        } else {
            this.stalePossibleSimpleKeys();
            return this.nextPossibleSimpleKey() == this.tokensTaken;
        }
    }

    private void fetchMoreTokens() {
        this.scanToNextToken();
        this.stalePossibleSimpleKeys();
        this.unwindIndent(this.reader.getColumn());
        int c = this.reader.peek();
        switch (c) {
            case 0:
                this.fetchStreamEnd();
                return;
            case 33:
                this.fetchTag();
                return;
            case 34:
                this.fetchDouble();
                return;
            case 37:
                if (this.checkDirective()) {
                    this.fetchDirective();
                    return;
                }
                break;
            case 38:
                this.fetchAnchor();
                return;
            case 39:
                this.fetchSingle();
                return;
            case 42:
                this.fetchAlias();
                return;
            case 44:
                this.fetchFlowEntry();
                return;
            case 45:
                if (this.checkDocumentStart()) {
                    this.fetchDocumentStart();
                    return;
                }

                if (this.checkBlockEntry()) {
                    this.fetchBlockEntry();
                    return;
                }
                break;
            case 46:
                if (this.checkDocumentEnd()) {
                    this.fetchDocumentEnd();
                    return;
                }
                break;
            case 58:
                if (this.checkValue()) {
                    this.fetchValue();
                    return;
                }
                break;
            case 62:
                if (this.flowLevel == 0) {
                    this.fetchFolded();
                    return;
                }
                break;
            case 63:
                if (this.checkKey()) {
                    this.fetchKey();
                    return;
                }
                break;
            case 91:
                this.fetchFlowSequenceStart();
                return;
            case 93:
                this.fetchFlowSequenceEnd();
                return;
            case 123:
                this.fetchFlowMappingStart();
                return;
            case 124:
                if (this.flowLevel == 0) {
                    this.fetchLiteral();
                    return;
                }
                break;
            case 125:
                this.fetchFlowMappingEnd();
                return;
        }

        if (this.checkPlain()) {
            this.fetchPlain();
        } else {
            String chRepresentation = String.valueOf(Character.toChars(c));

            for (Character s : ESCAPE_REPLACEMENTS.keySet()) {
                String v = ESCAPE_REPLACEMENTS.get(s);
                if (v.equals(chRepresentation)) {
                    chRepresentation = "\\" + s;
                    break;
                }
            }

            if (c == 9) {
                chRepresentation = chRepresentation + "(TAB)";
            }

            String text = String.format("found character '%s' that cannot start any token. (Do not use %s for indentation)", chRepresentation, chRepresentation);
            throw new ScannerException("while scanning for the next token", null, text, this.reader.getMark());
        }
    }

    private int nextPossibleSimpleKey() {
        return !this.possibleSimpleKeys.isEmpty() ? this.possibleSimpleKeys.values()
                                                                           .iterator()
                                                                           .next().getTokenNumber() : -1;
    }

    private void stalePossibleSimpleKeys() {
        if (!this.possibleSimpleKeys.isEmpty()) {
            Iterator<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey> iterator = this.possibleSimpleKeys.values().iterator();

            while (iterator.hasNext()) {
                io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey key = iterator.next();
                if (key.getLine() != this.reader.getLine() || this.reader.getIndex() - key.getIndex() > 1024) {
                    if (key.isRequired()) {
                        throw new ScannerException("while scanning a simple key", key.getMark(), "could not find expected ':'", this.reader.getMark());
                    }

                    iterator.remove();
                }
            }
        }

    }

    private void savePossibleSimpleKey() {
        boolean required = this.flowLevel == 0 && this.indent == this.reader.getColumn();
        if (!this.allowSimpleKey && required) {
            throw new YAMLException("A simple key is required only if it is the first token in the current line");
        } else {
            if (this.allowSimpleKey) {
                this.removePossibleSimpleKey();
                int tokenNumber = this.tokensTaken + this.tokens.size();
                io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey key = new io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey(tokenNumber, required, this.reader.getIndex(), this.reader.getLine(), this.reader.getColumn(), this.reader.getMark());
                this.possibleSimpleKeys.put(this.flowLevel, key);
            }

        }
    }

    private void removePossibleSimpleKey() {
        io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey key = this.possibleSimpleKeys.remove(this.flowLevel);
        if (key != null && key.isRequired()) {
            throw new ScannerException("while scanning a simple key", key.getMark(), "could not find expected ':'", this.reader.getMark());
        }
    }

    private void unwindIndent(int col) {
        if (this.flowLevel == 0) {
            while (this.indent > col) {
                Mark mark = this.reader.getMark();
                this.indent = this.indents.pop();
                this.tokens.add(new BlockEndToken(mark, mark));
            }

        }
    }

    private boolean addIndent(int column) {
        if (this.indent < column) {
            this.indents.push(this.indent);
            this.indent = column;
            return true;
        } else {
            return false;
        }
    }

    private void fetchStreamStart() {
        Mark mark = this.reader.getMark();
        Token token = new StreamStartToken(mark, mark);
        this.tokens.add(token);
    }

    private void fetchStreamEnd() {
        this.unwindIndent(-1);
        this.removePossibleSimpleKey();
        this.allowSimpleKey = false;
        this.possibleSimpleKeys.clear();
        Mark mark = this.reader.getMark();
        Token token = new StreamEndToken(mark, mark);
        this.tokens.add(token);
        this.done = true;
    }

    private void fetchDirective() {
        this.unwindIndent(-1);
        this.removePossibleSimpleKey();
        this.allowSimpleKey = false;
        Token tok = this.scanDirective();
        this.tokens.add(tok);
    }

    private void fetchDocumentStart() {
        this.fetchDocumentIndicator(true);
    }

    private void fetchDocumentEnd() {
        this.fetchDocumentIndicator(false);
    }

    private void fetchDocumentIndicator(boolean isDocumentStart) {
        this.unwindIndent(-1);
        this.removePossibleSimpleKey();
        this.allowSimpleKey = false;
        Mark startMark = this.reader.getMark();
        this.reader.forward(3);
        Mark endMark = this.reader.getMark();
        Token token;
        if (isDocumentStart) {
            token = new DocumentStartToken(startMark, endMark);
        } else {
            token = new DocumentEndToken(startMark, endMark);
        }

        this.tokens.add(token);
    }

    private void fetchFlowSequenceStart() {
        this.fetchFlowCollectionStart(false);
    }

    private void fetchFlowMappingStart() {
        this.fetchFlowCollectionStart(true);
    }

    private void fetchFlowCollectionStart(boolean isMappingStart) {
        this.savePossibleSimpleKey();
        ++this.flowLevel;
        this.allowSimpleKey = true;
        Mark startMark = this.reader.getMark();
        this.reader.forward(1);
        Mark endMark = this.reader.getMark();
        Token token;
        if (isMappingStart) {
            token = new FlowMappingStartToken(startMark, endMark);
        } else {
            token = new FlowSequenceStartToken(startMark, endMark);
        }

        this.tokens.add(token);
    }

    private void fetchFlowSequenceEnd() {
        this.fetchFlowCollectionEnd(false);
    }

    private void fetchFlowMappingEnd() {
        this.fetchFlowCollectionEnd(true);
    }

    private void fetchFlowCollectionEnd(boolean isMappingEnd) {
        this.removePossibleSimpleKey();
        --this.flowLevel;
        this.allowSimpleKey = false;
        Mark startMark = this.reader.getMark();
        this.reader.forward();
        Mark endMark = this.reader.getMark();
        Token token;
        if (isMappingEnd) {
            token = new FlowMappingEndToken(startMark, endMark);
        } else {
            token = new FlowSequenceEndToken(startMark, endMark);
        }

        this.tokens.add(token);
    }

    private void fetchFlowEntry() {
        this.allowSimpleKey = true;
        this.removePossibleSimpleKey();
        Mark startMark = this.reader.getMark();
        this.reader.forward();
        Mark endMark = this.reader.getMark();
        Token token = new FlowEntryToken(startMark, endMark);
        this.tokens.add(token);
    }

    private void fetchBlockEntry() {
        if (this.flowLevel == 0) {
            if (!this.allowSimpleKey) {
                throw new ScannerException(null, null, "sequence entries are not allowed here", this.reader.getMark());
            }

            if (this.addIndent(this.reader.getColumn())) {
                Mark mark = this.reader.getMark();
                this.tokens.add(new BlockSequenceStartToken(mark, mark));
            }
        }

        this.allowSimpleKey = true;
        this.removePossibleSimpleKey();
        Mark startMark = this.reader.getMark();
        this.reader.forward();
        Mark endMark = this.reader.getMark();
        Token token = new BlockEntryToken(startMark, endMark);
        this.tokens.add(token);
    }

    private void fetchKey() {
        if (this.flowLevel == 0) {
            if (!this.allowSimpleKey) {
                throw new ScannerException(null, null, "mapping keys are not allowed here", this.reader.getMark());
            }

            if (this.addIndent(this.reader.getColumn())) {
                Mark mark = this.reader.getMark();
                this.tokens.add(new BlockMappingStartToken(mark, mark));
            }
        }

        this.allowSimpleKey = this.flowLevel == 0;
        this.removePossibleSimpleKey();
        Mark startMark = this.reader.getMark();
        this.reader.forward();
        Mark endMark = this.reader.getMark();
        Token token = new KeyToken(startMark, endMark);
        this.tokens.add(token);
    }

    private void fetchValue() {
        io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.SimpleKey key = this.possibleSimpleKeys.remove(this.flowLevel);
        if (key != null) {
            this.tokens.add(key.getTokenNumber() - this.tokensTaken, new KeyToken(key.getMark(), key.getMark()));
            if (this.flowLevel == 0 && this.addIndent(key.getColumn())) {
                this.tokens.add(key.getTokenNumber() - this.tokensTaken, new BlockMappingStartToken(key.getMark(), key.getMark()));
            }

            this.allowSimpleKey = false;
        } else {
            if (this.flowLevel == 0 && !this.allowSimpleKey) {
                throw new ScannerException(null, null, "mapping values are not allowed here", this.reader.getMark());
            }

            if (this.flowLevel == 0 && this.addIndent(this.reader.getColumn())) {
                Mark mark = this.reader.getMark();
                this.tokens.add(new BlockMappingStartToken(mark, mark));
            }

            this.allowSimpleKey = this.flowLevel == 0;
            this.removePossibleSimpleKey();
        }

        Mark startMark = this.reader.getMark();
        this.reader.forward();
        Mark endMark = this.reader.getMark();
        Token token = new ValueToken(startMark, endMark);
        this.tokens.add(token);
    }

    private void fetchAlias() {
        this.savePossibleSimpleKey();
        this.allowSimpleKey = false;
        Token tok = this.scanAnchor(false);
        this.tokens.add(tok);
    }

    private void fetchAnchor() {
        this.savePossibleSimpleKey();
        this.allowSimpleKey = false;
        Token tok = this.scanAnchor(true);
        this.tokens.add(tok);
    }

    private void fetchTag() {
        this.savePossibleSimpleKey();
        this.allowSimpleKey = false;
        Token tok = this.scanTag();
        this.tokens.add(tok);
    }

    private void fetchLiteral() {
        this.fetchBlockScalar('|');
    }

    private void fetchFolded() {
        this.fetchBlockScalar('>');
    }

    private void fetchBlockScalar(char style) {
        this.allowSimpleKey = true;
        this.removePossibleSimpleKey();
        Token tok = this.scanBlockScalar(style);
        this.tokens.add(tok);
    }

    private void fetchSingle() {
        this.fetchFlowScalar('\'');
    }

    private void fetchDouble() {
        this.fetchFlowScalar('"');
    }

    private void fetchFlowScalar(char style) {
        this.savePossibleSimpleKey();
        this.allowSimpleKey = false;
        Token tok = this.scanFlowScalar(style);
        this.tokens.add(tok);
    }

    private void fetchPlain() {
        this.savePossibleSimpleKey();
        this.allowSimpleKey = false;
        Token tok = this.scanPlain();
        this.tokens.add(tok);
    }

    private boolean checkDirective() {
        return this.reader.getColumn() == 0;
    }

    private boolean checkDocumentStart() {
        return this.reader.getColumn() == 0 && "---".equals(this.reader.prefix(3)) && io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(3));
    }

    private boolean checkDocumentEnd() {
        return this.reader.getColumn() == 0 && "...".equals(this.reader.prefix(3)) && io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(3));
    }

    private boolean checkBlockEntry() {
        return io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(1));
    }

    private boolean checkKey() {
        return this.flowLevel != 0 || Constant.NULL_BL_T_LINEBR.has(this.reader.peek(1));
    }

    private boolean checkValue() {
        return this.flowLevel != 0 || Constant.NULL_BL_T_LINEBR.has(this.reader.peek(1));
    }

    private boolean checkPlain() {
        int c = this.reader.peek();
        return io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.hasNo(c, "-?:,[]{}#&*!|>'\"%@`") || io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.hasNo(this.reader.peek(1)) && (c == 45 || this.flowLevel == 0 && "?:".indexOf(c) != -1);
    }

    private void scanToNextToken() {
        if (this.reader.getIndex() == 0 && this.reader.peek() == 65279) {
            this.reader.forward();
        }

        boolean found = false;

        while (!found) {
            int ff;
            for (ff = 0; this.reader.peek(ff) == 32; ++ff) {
            }

            if (ff > 0) {
                this.reader.forward(ff);
            }

            if (this.reader.peek() == 35) {
                for (ff = 0; io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_OR_LINEBR.hasNo(this.reader.peek(ff)); ++ff) {
                }

                if (ff > 0) {
                    this.reader.forward(ff);
                }
            }

            if (this.scanLineBreak().length() != 0) {
                if (this.flowLevel == 0) {
                    this.allowSimpleKey = true;
                }
            } else {
                found = true;
            }
        }

    }

    private Token scanDirective() {
        Mark startMark = this.reader.getMark();
        this.reader.forward();
        String name = this.scanDirectiveName(startMark);
        List<?> value = null;
        Mark endMark;
        if ("YAML".equals(name)) {
            value = this.scanYamlDirectiveValue(startMark);
            endMark = this.reader.getMark();
        } else if ("TAG".equals(name)) {
            value = this.scanTagDirectiveValue(startMark);
            endMark = this.reader.getMark();
        } else {
            endMark = this.reader.getMark();

            int ff;
            for (ff = 0; io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_OR_LINEBR.hasNo(this.reader.peek(ff)); ++ff) {
            }

            if (ff > 0) {
                this.reader.forward(ff);
            }
        }

        this.scanDirectiveIgnoredLine(startMark);
        return new DirectiveToken(name, value, startMark, endMark);
    }

    private String scanDirectiveName(Mark startMark) {
        int length = 0;

        int c;
        for (c = this.reader.peek(length); io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.ALPHA.has(c); c = this.reader.peek(length)) {
            ++length;
        }

        if (length == 0) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark, "expected alphabetic or numeric character, but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            String value = this.reader.prefixForward(length);
            c = this.reader.peek();
            if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_LINEBR.hasNo(c)) {
                String s = String.valueOf(Character.toChars(c));
                throw new ScannerException("while scanning a directive", startMark, "expected alphabetic or numeric character, but found " + s + "(" + c + ")", this.reader.getMark());
            } else {
                return value;
            }
        }
    }

    private List<Integer> scanYamlDirectiveValue(Mark startMark) {
        while (this.reader.peek() == 32) {
            this.reader.forward();
        }

        Integer major = this.scanYamlDirectiveNumber(startMark);
        int c = this.reader.peek();
        if (c != 46) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark, "expected a digit or '.', but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            this.reader.forward();
            Integer minor = this.scanYamlDirectiveNumber(startMark);
            c = this.reader.peek();
            if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_LINEBR.hasNo(c)) {
                String s = String.valueOf(Character.toChars(c));
                throw new ScannerException("while scanning a directive", startMark, "expected a digit or ' ', but found " + s + "(" + c + ")", this.reader.getMark());
            } else {
                List<Integer> result = new ArrayList(2);
                result.add(major);
                result.add(minor);
                return result;
            }
        }
    }

    private Integer scanYamlDirectiveNumber(Mark startMark) {
        int c = this.reader.peek();
        if (!Character.isDigit(c)) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark, "expected a digit, but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            int length;
            for (length = 0; Character.isDigit(this.reader.peek(length)); ++length) {
            }

            Integer value = Integer.parseInt(this.reader.prefixForward(length));
            return value;
        }
    }

    private List<String> scanTagDirectiveValue(Mark startMark) {
        while (this.reader.peek() == 32) {
            this.reader.forward();
        }

        String handle = this.scanTagDirectiveHandle(startMark);

        while (this.reader.peek() == 32) {
            this.reader.forward();
        }

        String prefix = this.scanTagDirectivePrefix(startMark);
        List<String> result = new ArrayList(2);
        result.add(handle);
        result.add(prefix);
        return result;
    }

    private String scanTagDirectiveHandle(Mark startMark) {
        String value = this.scanTagHandle("directive", startMark);
        int c = this.reader.peek();
        if (c != 32) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark, "expected ' ', but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            return value;
        }
    }

    private String scanTagDirectivePrefix(Mark startMark) {
        String value = this.scanTagUri("directive", startMark);
        int c = this.reader.peek();
        if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_LINEBR.hasNo(c)) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark, "expected ' ', but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            return value;
        }
    }

    private void scanDirectiveIgnoredLine(Mark startMark) {
        while (this.reader.peek() == 32) {
            this.reader.forward();
        }

        if (this.reader.peek() == 35) {
            while (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_OR_LINEBR.hasNo(this.reader.peek())) {
                this.reader.forward();
            }
        }

        int c = this.reader.peek();
        String lineBreak = this.scanLineBreak();
        if (lineBreak.length() == 0 && c != 0) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a directive", startMark, "expected a comment or a line break, but found " + s + "(" + c + ")", this.reader.getMark());
        }
    }

    private Token scanAnchor(boolean isAnchor) {
        Mark startMark = this.reader.getMark();
        int indicator = this.reader.peek();
        String name = indicator == 42 ? "alias" : "anchor";
        this.reader.forward();
        int length = 0;

        int c;
        for (c = this.reader.peek(length); io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.hasNo(c, ":,[]{}"); c = this.reader.peek(length)) {
            ++length;
        }

        if (length == 0) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning an " + name, startMark, "unexpected character found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            String value = this.reader.prefixForward(length);
            c = this.reader.peek();
            if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.hasNo(c, "?:,]}%@`")) {
                String s = String.valueOf(Character.toChars(c));
                throw new ScannerException("while scanning an " + name, startMark, "unexpected character found " + s + "(" + c + ")", this.reader.getMark());
            } else {
                Mark endMark = this.reader.getMark();
                Token tok;
                if (isAnchor) {
                    tok = new AnchorToken(value, startMark, endMark);
                } else {
                    tok = new AliasToken(value, startMark, endMark);
                }

                return tok;
            }
        }
    }

    private Token scanTag() {
        Mark startMark = this.reader.getMark();
        int c = this.reader.peek(1);
        String handle = null;
        String suffix = null;
        if (c == 60) {
            this.reader.forward(2);
            suffix = this.scanTagUri("tag", startMark);
            c = this.reader.peek();
            if (c != 62) {
                String s = String.valueOf(Character.toChars(c));
                throw new ScannerException("while scanning a tag", startMark, "expected '>', but found '" + s + "' (" + c + ")", this.reader.getMark());
            }

            this.reader.forward();
        } else if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(c)) {
            suffix = "!";
            this.reader.forward();
        } else {
            int length = 1;

            boolean useHandle;
            for (useHandle = false; io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_LINEBR.hasNo(c); c = this.reader.peek(length)) {
                if (c == 33) {
                    useHandle = true;
                    break;
                }

                ++length;
            }

            if (useHandle) {
                handle = this.scanTagHandle("tag", startMark);
            } else {
                handle = "!";
                this.reader.forward();
            }

            suffix = this.scanTagUri("tag", startMark);
        }

        c = this.reader.peek();
        if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_LINEBR.hasNo(c)) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a tag", startMark, "expected ' ', but found '" + s + "' (" + c + ")", this.reader.getMark());
        } else {
            TagTuple value = new TagTuple(handle, suffix);
            Mark endMark = this.reader.getMark();
            return new TagToken(value, startMark, endMark);
        }
    }

    private Token scanBlockScalar(char style) {
        boolean folded;
        folded = style == '>';

        StringBuilder chunks = new StringBuilder();
        Mark startMark = this.reader.getMark();
        this.reader.forward();
        Chomping chompi = this.scanBlockScalarIndicators(startMark);
        int increment = chompi.getIncrement();
        this.scanBlockScalarIgnoredLine(startMark);
        int minIndent = this.indent + 1;
        if (minIndent < 1) {
            minIndent = 1;
        }

        String breaks;
        int indent;
        Mark endMark;
        if (increment == -1) {
            Object[] brme = this.scanBlockScalarIndentation();
            breaks = (String) brme[0];
            int maxIndent = (Integer) brme[1];
            endMark = (Mark) brme[2];
            indent = Math.max(minIndent, maxIndent);
        } else {
            indent = minIndent + increment - 1;
            Object[] brme = this.scanBlockScalarBreaks(indent);
            breaks = (String) brme[0];
            endMark = (Mark) brme[1];
        }

        String lineBreak = "";

        while (this.reader.getColumn() == indent && this.reader.peek() != 0) {
            chunks.append(breaks);
            boolean leadingNonSpace = " \t".indexOf(this.reader.peek()) == -1;

            int length;
            for (length = 0; io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_OR_LINEBR.hasNo(this.reader.peek(length)); ++length) {
            }

            chunks.append(this.reader.prefixForward(length));
            lineBreak = this.scanLineBreak();
            Object[] brme = this.scanBlockScalarBreaks(indent);
            breaks = (String) brme[0];
            endMark = (Mark) brme[1];
            if (this.reader.getColumn() != indent || this.reader.peek() == 0) {
                break;
            }

            if (folded && "\n".equals(lineBreak) && leadingNonSpace && " \t".indexOf(this.reader.peek()) == -1) {
                if (breaks.length() == 0) {
                    chunks.append(" ");
                }
            } else {
                chunks.append(lineBreak);
            }
        }

        if (chompi.chompTailIsNotFalse()) {
            chunks.append(lineBreak);
        }

        if (chompi.chompTailIsTrue()) {
            chunks.append(breaks);
        }

        return new ScalarToken(chunks.toString(), false, startMark, endMark, ScalarStyle.createStyle(style));
    }

    private Chomping scanBlockScalarIndicators(Mark startMark) {
        Boolean chomping = null;
        int increment = -1;
        int c = this.reader.peek();
        if (c != 45 && c != 43) {
            if (Character.isDigit(c)) {
                String s = String.valueOf(Character.toChars(c));
                increment = Integer.parseInt(s);
                if (increment == 0) {
                    throw new ScannerException("while scanning a block scalar", startMark, "expected indentation indicator in the range 1-9, but found 0", this.reader.getMark());
                }

                this.reader.forward();
                c = this.reader.peek();
                if (c == 45 || c == 43) {
                    if (c == 43) {
                        chomping = Boolean.TRUE;
                    } else {
                        chomping = Boolean.FALSE;
                    }

                    this.reader.forward();
                }
            }
        } else {
            if (c == 43) {
                chomping = Boolean.TRUE;
            } else {
                chomping = Boolean.FALSE;
            }

            this.reader.forward();
            c = this.reader.peek();
            if (Character.isDigit(c)) {
                String s = String.valueOf(Character.toChars(c));
                increment = Integer.parseInt(s);
                if (increment == 0) {
                    throw new ScannerException("while scanning a block scalar", startMark, "expected indentation indicator in the range 1-9, but found 0", this.reader.getMark());
                }

                this.reader.forward();
            }
        }

        c = this.reader.peek();
        if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_LINEBR.hasNo(c)) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a block scalar", startMark, "expected chomping or indentation indicators, but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            return new Chomping(chomping, increment);
        }
    }

    private String scanBlockScalarIgnoredLine(Mark startMark) {
        while (this.reader.peek() == 32) {
            this.reader.forward();
        }

        if (this.reader.peek() == 35) {
            while (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_OR_LINEBR.hasNo(this.reader.peek())) {
                this.reader.forward();
            }
        }

        int c = this.reader.peek();
        String lineBreak = this.scanLineBreak();
        if (lineBreak.length() == 0 && c != 0) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a block scalar", startMark, "expected a comment or a line break, but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            return lineBreak;
        }
    }

    private Object[] scanBlockScalarIndentation() {
        StringBuilder chunks = new StringBuilder();
        int maxIndent = 0;
        Mark endMark = this.reader.getMark();

        while (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.LINEBR.has(this.reader.peek(), " \r")) {
            if (this.reader.peek() != 32) {
                chunks.append(this.scanLineBreak());
                endMark = this.reader.getMark();
            } else {
                this.reader.forward();
                if (this.reader.getColumn() > maxIndent) {
                    maxIndent = this.reader.getColumn();
                }
            }
        }

        return new Object[]{chunks.toString(), maxIndent, endMark};
    }

    private Object[] scanBlockScalarBreaks(int indent) {
        StringBuilder chunks = new StringBuilder();
        Mark endMark = this.reader.getMark();

        for (int col = this.reader.getColumn(); col < indent && this.reader.peek() == 32; ++col) {
            this.reader.forward();
        }

        String lineBreak = null;

        while ((lineBreak = this.scanLineBreak()).length() != 0) {
            chunks.append(lineBreak);
            endMark = this.reader.getMark();

            for (int var6 = this.reader.getColumn(); var6 < indent && this.reader.peek() == 32; ++var6) {
                this.reader.forward();
            }
        }

        return new Object[]{chunks.toString(), endMark};
    }

    private Token scanFlowScalar(char style) {
        boolean _double;
        _double = style == '"';

        StringBuilder chunks = new StringBuilder();
        Mark startMark = this.reader.getMark();
        int quote = this.reader.peek();
        this.reader.forward();
        chunks.append(this.scanFlowScalarNonSpaces(_double, startMark));

        while (this.reader.peek() != quote) {
            chunks.append(this.scanFlowScalarSpaces(startMark));
            chunks.append(this.scanFlowScalarNonSpaces(_double, startMark));
        }

        this.reader.forward();
        Mark endMark = this.reader.getMark();
        return new ScalarToken(chunks.toString(), false, startMark, endMark, ScalarStyle.createStyle(style));
    }

    private String scanFlowScalarNonSpaces(boolean doubleQuoted, Mark startMark) {
        StringBuilder chunks = new StringBuilder();

        while (true) {
            int length;
            for (length = 0; io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.hasNo(this.reader.peek(length), "'\"\\"); ++length) {
            }

            if (length != 0) {
                chunks.append(this.reader.prefixForward(length));
            }

            int c = this.reader.peek();
            if (doubleQuoted || c != 39 || this.reader.peek(1) != 39) {
                if ((!doubleQuoted || c != 39) && (doubleQuoted || "\"\\".indexOf(c) == -1)) {
                    if (!doubleQuoted || c != 92) {
                        return chunks.toString();
                    }

                    this.reader.forward();
                    c = this.reader.peek();
                    if (!Character.isSupplementaryCodePoint(c) && ESCAPE_REPLACEMENTS.containsKey((char) c)) {
                        chunks.append(ESCAPE_REPLACEMENTS.get((char) c));
                        this.reader.forward();
                    } else if (!Character.isSupplementaryCodePoint(c) && ESCAPE_CODES.containsKey((char) c)) {
                        length = ESCAPE_CODES.get((char) c);
                        this.reader.forward();
                        String hex = this.reader.prefix(length);
                        if (NOT_HEXA.matcher(hex).find()) {
                            throw new ScannerException("while scanning a double-quoted scalar", startMark, "expected escape sequence of " + length + " hexadecimal numbers, but found: " + hex, this.reader.getMark());
                        }

                        int decimal = Integer.parseInt(hex, 16);
                        String unicode = new String(Character.toChars(decimal));
                        chunks.append(unicode);
                        this.reader.forward(length);
                    } else {
                        if (this.scanLineBreak().length() == 0) {
                            String s = String.valueOf(Character.toChars(c));
                            throw new ScannerException("while scanning a double-quoted scalar", startMark, "found unknown escape character " + s + "(" + c + ")", this.reader.getMark());
                        }

                        chunks.append(this.scanFlowScalarBreaks(startMark));
                    }
                } else {
                    chunks.appendCodePoint(c);
                    this.reader.forward();
                }
            } else {
                chunks.append("'");
                this.reader.forward(2);
            }
        }
    }

    private String scanFlowScalarSpaces(Mark startMark) {
        StringBuilder chunks = new StringBuilder();

        int length;
        for (length = 0; " \t".indexOf(this.reader.peek(length)) != -1; ++length) {
        }

        String whitespaces = this.reader.prefixForward(length);
        int c = this.reader.peek();
        if (c == 0) {
            throw new ScannerException("while scanning a quoted scalar", startMark, "found unexpected end of stream", this.reader.getMark());
        } else {
            String lineBreak = this.scanLineBreak();
            if (lineBreak.length() != 0) {
                String breaks = this.scanFlowScalarBreaks(startMark);
                if (!"\n".equals(lineBreak)) {
                    chunks.append(lineBreak);
                } else if (breaks.length() == 0) {
                    chunks.append(" ");
                }

                chunks.append(breaks);
            } else {
                chunks.append(whitespaces);
            }

            return chunks.toString();
        }
    }

    private String scanFlowScalarBreaks(Mark startMark) {
        StringBuilder chunks = new StringBuilder();

        while (true) {
            String prefix = this.reader.prefix(3);
            if (("---".equals(prefix) || "...".equals(prefix)) && io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(3))) {
                throw new ScannerException("while scanning a quoted scalar", startMark, "found unexpected document separator", this.reader.getMark());
            }

            while (" \t".indexOf(this.reader.peek()) != -1) {
                this.reader.forward();
            }

            String lineBreak = this.scanLineBreak();
            if (lineBreak.length() == 0) {
                return chunks.toString();
            }

            chunks.append(lineBreak);
        }
    }

    private Token scanPlain() {
        StringBuilder chunks = new StringBuilder();
        Mark startMark = this.reader.getMark();
        Mark endMark = startMark;
        int indent = this.indent + 1;
        String spaces = "";

        while (true) {
            int length = 0;
            if (this.reader.peek() == 35) {
                return new ScalarToken(chunks.toString(), startMark, endMark, true);
            }

            while (true) {
                int c = this.reader.peek(length);
                if (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(c) || c == 58 && io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(length + 1), this.flowLevel != 0 ? ",[]{}" : "") || this.flowLevel != 0 && ",?[]{}".indexOf(c) != -1) {
                    if (length == 0) {
                        return new ScalarToken(chunks.toString(), startMark, endMark, true);
                    }

                    this.allowSimpleKey = false;
                    chunks.append(spaces);
                    chunks.append(this.reader.prefixForward(length));
                    endMark = this.reader.getMark();
                    spaces = this.scanPlainSpaces();
                    if (spaces.length() == 0 || this.reader.peek() == 35 || this.flowLevel == 0 && this.reader.getColumn() < indent) {
                        return new ScalarToken(chunks.toString(), startMark, endMark, true);
                    }
                    break;
                }

                ++length;
            }
        }
    }

    private String scanPlainSpaces() {
        int length;
        for (length = 0; this.reader.peek(length) == 32 || this.reader.peek(length) == 9; ++length) {
        }

        String whitespaces = this.reader.prefixForward(length);
        String lineBreak = this.scanLineBreak();
        if (lineBreak.length() == 0) {
            return whitespaces;
        } else {
            this.allowSimpleKey = true;
            String prefix = this.reader.prefix(3);
            if (!"---".equals(prefix) && (!"...".equals(prefix) || !io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(3)))) {
                StringBuilder breaks = new StringBuilder();

                do {
                    while (this.reader.peek() == 32) {
                        this.reader.forward();
                    }

                    String lb = this.scanLineBreak();
                    if (lb.length() == 0) {
                        if (!"\n".equals(lineBreak)) {
                            return lineBreak + breaks;
                        }

                        if (breaks.length() == 0) {
                            return " ";
                        }

                        return breaks.toString();
                    }

                    breaks.append(lb);
                    prefix = this.reader.prefix(3);
                } while (!"---".equals(prefix) && (!"...".equals(prefix) || !io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.NULL_BL_T_LINEBR.has(this.reader.peek(3))));

                return "";
            } else {
                return "";
            }
        }
    }

    private String scanTagHandle(String name, Mark startMark) {
        int c = this.reader.peek();
        if (c != 33) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a " + name, startMark, "expected '!', but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            int length = 1;
            c = this.reader.peek(length);
            if (c != 32) {
                while (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant.ALPHA.has(c)) {
                    ++length;
                    c = this.reader.peek(length);
                }

                if (c != 33) {
                    this.reader.forward(length);
                    String s = String.valueOf(Character.toChars(c));
                    throw new ScannerException("while scanning a " + name, startMark, "expected '!', but found " + s + "(" + c + ")", this.reader.getMark());
                }

                ++length;
            }

            String value = this.reader.prefixForward(length);
            return value;
        }
    }

    private String scanTagUri(String name, Mark startMark) {
        StringBuilder chunks = new StringBuilder();
        int length = 0;

        int c;
        for (c = this.reader.peek(length); Constant.URI_CHARS.has(c); c = this.reader.peek(length)) {
            if (c == 37) {
                chunks.append(this.reader.prefixForward(length));
                length = 0;
                chunks.append(this.scanUriEscapes(name, startMark));
            } else {
                ++length;
            }
        }

        if (length != 0) {
            chunks.append(this.reader.prefixForward(length));
        }

        if (chunks.length() == 0) {
            String s = String.valueOf(Character.toChars(c));
            throw new ScannerException("while scanning a " + name, startMark, "expected URI, but found " + s + "(" + c + ")", this.reader.getMark());
        } else {
            return chunks.toString();
        }
    }

    private String scanUriEscapes(String name, Mark startMark) {
        int length;
        for (length = 1; this.reader.peek(length * 3) == 37; ++length) {
        }

        Mark beginningMark = this.reader.getMark();

        ByteBuffer buff;
        for (buff = ByteBuffer.allocate(length); this.reader.peek() == 37; this.reader.forward(2)) {
            this.reader.forward();

            try {
                byte code = (byte) Integer.parseInt(this.reader.prefix(2), 16);
                buff.put(code);
            } catch (NumberFormatException var12) {
                int c1 = this.reader.peek();
                String s1 = String.valueOf(Character.toChars(c1));
                int c2 = this.reader.peek(1);
                String s2 = String.valueOf(Character.toChars(c2));
                throw new ScannerException("while scanning a " + name, startMark, "expected URI escape sequence of 2 hexadecimal numbers, but found " + s1 + "(" + c1 + ") and " + s2 + "(" + c2 + ")", this.reader.getMark());
            }
        }

        buff.flip();

        try {
            return UriEncoder.decode(buff);
        } catch (CharacterCodingException e) {
            throw new ScannerException("while scanning a " + name, startMark, "expected URI in UTF-8: " + e.getMessage(), beginningMark);
        }
    }

    private String scanLineBreak() {
        int c = this.reader.peek();
        if (c != 13 && c != 10 && c != 133) {
            if (c != 8232 && c != 8233) {
                return "";
            } else {
                this.reader.forward();
                return String.valueOf(Character.toChars(c));
            }
        } else {
            if (c == 13 && 10 == this.reader.peek(1)) {
                this.reader.forward(2);
            } else {
                this.reader.forward();
            }

            return "\n";
        }
    }

    private static class Chomping {
        private final Boolean value;
        private final int increment;

        public Chomping(Boolean value, int increment) {
            this.value = value;
            this.increment = increment;
        }

        public boolean chompTailIsNotFalse() {
            return this.value == null || this.value;
        }

        public boolean chompTailIsTrue() {
            return this.value != null && this.value;
        }

        public int getIncrement() {
            return this.increment;
        }
    }
}
