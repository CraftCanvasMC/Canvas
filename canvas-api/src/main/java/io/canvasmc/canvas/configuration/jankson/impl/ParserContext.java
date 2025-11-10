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

public interface ParserContext<T> {
    /**
     * Consume one codepoint from the stream, and either use it to continue composing the result or to discover that
     * the result is complete and processing should stop. Throws a SyntaxError if unexpected or nonsense characters are
     * encountered.
     */

    boolean consume(int codePoint, Jankson loader) throws SyntaxError;

    /**
     * Notifies this context that the file ended abruptly while in this context and before isComplete returned true. In
     * some contexts, like a single-line comment, this is fine. In most contexts, this should throw a descriptive error.
     */
    void eof() throws SyntaxError;

    /**
     * Returns true if the parser has assembled a complete result. After true is returned, no more code points will be
     * offered to consume, and getResult will soon be called to retrieve the result.
     */
    boolean isComplete();

    /**
     * Gets the result of parsing. Will be called only after isComplete reports true and processing of input has
     * ceased.
     */
    T getResult() throws SyntaxError;
}
