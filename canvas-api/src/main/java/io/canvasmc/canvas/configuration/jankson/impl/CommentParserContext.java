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

package io.canvasmc.canvas.configuration.jankson.impl;

import io.canvasmc.canvas.configuration.jankson.Jankson;
import io.canvasmc.canvas.configuration.jankson.api.SyntaxError;

public class CommentParserContext implements ParserContext<String> {
    int firstChar = -1;
    int secondChar = -1;

    StringBuilder result = new StringBuilder();

    int prevChar = -1;

    boolean multiLine = false;
    boolean done = false;

    public CommentParserContext(int codePoint) {
        firstChar = codePoint;
    }

    @Override
    public boolean consume(int codePoint, Jankson loader) throws SyntaxError {
        if (done) return false;

        if (firstChar == -1) {
            if (codePoint != '/' && codePoint != '#') {
                throw new SyntaxError("Was expecting the start of a comment, but found '" + (char) codePoint + "' instead.");
            }
            firstChar = codePoint;
            if (firstChar == '#') multiLine = false;
            return true;
        }

        if (secondChar == -1 && firstChar != '#') {
            secondChar = codePoint;
            if (codePoint == '*') {
                multiLine = true;
                return true;
            } else if (codePoint == '/') {
                multiLine = false;
                return true;
            } else {
                if (Character.isWhitespace(codePoint)) {
                    throw new SyntaxError("Was expecting the start of a comment, but found whitespace instead.");
                } else {
                    throw new SyntaxError("Was expecting the start of a comment, but found '" + (char) codePoint + "' instead.");
                }
            }
        }

        //We're past the initiating character(s)
        if (multiLine) {
            if (codePoint == '/' && prevChar == '*') {
                result.deleteCharAt(result.length() - 1); //Get rid of the *
                done = true;
                return true;
            } else {
                prevChar = codePoint;
                result.append((char) codePoint);
                return true;
            }
        } else {
            if (codePoint == '\n') {
                done = true;
                return true;
            } else {
                prevChar = codePoint; //Not really necessary but whatever. For consistency! :)
                result.append((char) codePoint);
                return true;
            }
        }
    }

    @Override
    public void eof() throws SyntaxError {
        if (multiLine) throw new SyntaxError("Unexpected end-of-file while reading a multiline comment.");
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String getResult() throws SyntaxError {
        return result.toString().trim();
    }

}
