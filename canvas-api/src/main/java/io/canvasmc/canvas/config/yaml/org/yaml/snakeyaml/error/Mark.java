//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner.Constant;
import java.io.Serializable;

public final class Mark implements Serializable {
    private final String name;
    private final int index;
    private final int line;
    private final int column;
    private final int[] buffer;
    private final int pointer;

    public Mark(String name, int index, int line, int column, char[] str, int pointer) {
        this(name, index, line, column, toCodePoints(str), pointer);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Mark(String name, int index, int line, int column, String buffer, int pointer) {
        this(name, index, line, column, buffer.toCharArray(), pointer);
    }

    public Mark(String name, int index, int line, int column, int[] buffer, int pointer) {
        this.name = name;
        this.index = index;
        this.line = line;
        this.column = column;
        this.buffer = buffer;
        this.pointer = pointer;
    }

    private static int[] toCodePoints(char[] str) {
        int[] codePoints = new int[Character.codePointCount(str, 0, str.length)];
        int i = 0;

        for (int c = 0; i < str.length; ++c) {
            int cp = Character.codePointAt(str, i);
            codePoints[c] = cp;
            i += Character.charCount(cp);
        }

        return codePoints;
    }

    private boolean isLineBreak(int c) {
        return Constant.NULL_OR_LINEBR.has(c);
    }

    public String get_snippet(int indent, int max_length) {
        float half = (float) max_length / 2.0F - 1.0F;
        int start = this.pointer;
        String head = "";

        while (start > 0 && !this.isLineBreak(this.buffer[start - 1])) {
            --start;
            if ((float) (this.pointer - start) > half) {
                head = " ... ";
                start += 5;
                break;
            }
        }

        String tail = "";
        int end = this.pointer;

        while (end < this.buffer.length && !this.isLineBreak(this.buffer[end])) {
            ++end;
            if ((float) (end - this.pointer) > half) {
                tail = " ... ";
                end -= 5;
                break;
            }
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < indent; ++i) {
            result.append(" ");
        }

        result.append(head);

        for (int i = start; i < end; ++i) {
            result.appendCodePoint(this.buffer[i]);
        }

        result.append(tail);
        result.append("\n");

        for (int i = 0; i < indent + this.pointer - start + head.length(); ++i) {
            result.append(" ");
        }

        result.append("^");
        return result.toString();
    }

    public String get_snippet() {
        return this.get_snippet(4, 75);
    }

    public String toString() {
        String snippet = this.get_snippet();
        final String builder = " in " + this.name +
            ", line " +
            (this.line + 1) +
            ", column " +
            (this.column + 1) +
            ":\n" +
            snippet;
        return builder;
    }

    public String getName() {
        return this.name;
    }

    public int getLine() {
        return this.line;
    }

    public int getColumn() {
        return this.column;
    }

    public int getIndex() {
        return this.index;
    }

    public int[] getBuffer() {
        return this.buffer;
    }

    public int getPointer() {
        return this.pointer;
    }
}
