/*
 * MIT License
 *
 * Copyright (c) 2018-2019 Falkreon (Isaac Ellingson)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.canvasmc.canvas.configuration.jankson.api;

@SuppressWarnings("serial")
public class SyntaxError extends Exception {
    int startLine = -1;
    int startColumn = -1;

    int line = -1;
    int column = -1;

    public SyntaxError(String message) {
        super(message);
    }

    public String getCompleteMessage() {
        StringBuilder message = new StringBuilder();
        if (startLine != -1 && startColumn != -1) {
            message.append("Started at line ");
            message.append(startLine + 1);
            message.append(", column ");
            message.append(startColumn + 1);
            message.append("; ");
        }

        if (line != -1 && column != -1) {
            message.append("Errored at line ");
            message.append(line + 1);
            message.append(", column ");
            message.append(column + 1);
            message.append("; ");
        }

        message.append(super.getMessage());

        return message.toString();
    }

    public String getLineMessage() {
        StringBuilder message = new StringBuilder();
        boolean hasStart = (startLine != -1 && startColumn != -1);
        boolean hasEnd = (line != -1 && column != -1);

        if (hasStart) {
            message.append("Started at line ");
            message.append(startLine + 1);
            message.append(", column ");
            message.append(startColumn + 1);
        }

        if (hasStart && hasEnd) {
            message.append("; ");
        }

        if (hasEnd) {
            message.append("Errored at line ");
            message.append(line + 1);
            message.append(", column ");
            message.append(column + 1);
        }

        return message.toString();
    }

    public void setStartParsing(int line, int column) {
        this.startLine = line;
        this.startColumn = column;
    }

    public void setEndParsing(int line, int column) {
        this.line = line;
        this.column = column;
    }

}
