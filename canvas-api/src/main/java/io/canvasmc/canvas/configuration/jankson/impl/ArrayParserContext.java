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
import io.canvasmc.canvas.configuration.jankson.JsonArray;
import io.canvasmc.canvas.configuration.jankson.api.SyntaxError;

public class ArrayParserContext implements ParserContext<JsonArray> {
    private final JsonArray result = new JsonArray();
    private final String comment = null;
    private boolean foundClosingBrace = false;

    /**
     * Assumes the opening brace has already been consumed!
     */
    public ArrayParserContext() {

    }

    @Override
    public boolean consume(int codePoint, Jankson loader) throws SyntaxError {
        result.setMarshaller(loader.getMarshaller());
        if (foundClosingBrace) return false;
        if (Character.isWhitespace(codePoint) || codePoint == ',') return true;

        if (codePoint == ']') {
            foundClosingBrace = true;
            return true;
        }

        loader.push(new ElementParserContext(), (it) -> {
            if (it.getElement() != null) {
                result.add(it.getElement(), it.getComment());
            } else {
                String existing = result.getComment(result.size() - 1);
                if (existing == null) existing = "";
                String combined = existing + "\n" + it.getComment();
                result.setComment(result.size() - 1, combined);
            }
        });
        return false;
    }

    @Override
    public void eof() throws SyntaxError {
        if (foundClosingBrace) return;
        throw new SyntaxError("Unexpected end-of-file in the middle of a list! Are you missing a ']'?");
    }

    @Override
    public boolean isComplete() {
        return foundClosingBrace;
    }

    @Override
    public JsonArray getResult() throws SyntaxError {
        return result;
    }

}
