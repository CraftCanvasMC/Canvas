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
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import io.canvasmc.canvas.configuration.jankson.api.SyntaxError;

public class TokenParserContext implements ParserContext<JsonPrimitive> {
    private String token = "";
    private boolean complete = false;

    public TokenParserContext(int firstCodePoint) {
        token += (char) firstCodePoint;
    }

    @Override
    public boolean consume(int codePoint, Jankson loader) throws SyntaxError {
        if (complete) return false;

        if (codePoint == '~' || Character.isUnicodeIdentifierPart(codePoint)) {

            if (codePoint < 0xFFFF) {
                token += ((char) codePoint);
                return true;
            } else {
                //Construct a high and low surrogate pair for this code point
                //TODO: Finish implementing
                int temp = codePoint - 0x10000;
                int highSurrogate = (temp >>> 10) + 0xD800;
                int lowSurrogate = (temp & 0b11_1111_1111) + 0xDC00;

                token += (char) highSurrogate;
                token += (char) lowSurrogate;

                return true;
            }

        } else {
            complete = true;
            return false;
        }
    }

    @Override
    public void eof() throws SyntaxError {
        complete = true;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public JsonPrimitive getResult() throws SyntaxError {
        return new JsonPrimitive(token);
    }
}
