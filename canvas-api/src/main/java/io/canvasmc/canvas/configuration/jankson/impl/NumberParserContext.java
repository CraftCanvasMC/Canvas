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
import java.util.Locale;

public class NumberParserContext implements ParserContext<JsonPrimitive> {
    private final String acceptedChars = "0123456789.+-eExabcdefInityNn";
    private String numberString = "";
    private boolean complete = false;

    public NumberParserContext(int firstCodePoint) {
        numberString += (char) firstCodePoint;
    }

    @Override
    public boolean consume(int codePoint, Jankson loader) throws SyntaxError {
        if (complete) return false;

        if (acceptedChars.indexOf(codePoint) != -1) {
            numberString += (char) codePoint;
            return true;
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
        //parse special values
        String lc = numberString.toLowerCase(Locale.ROOT);
        if (lc.equals("infinity") || lc.equals("+infinity")) {
            return new JsonPrimitive(Double.POSITIVE_INFINITY);
        } else if (lc.equals("-infinity")) {
            return new JsonPrimitive(Double.NEGATIVE_INFINITY);
        } else if (lc.equals("nan")) {
            return new JsonPrimitive(Double.NaN);
        }

        //Fallback to the number parsers
        if (numberString.startsWith(".")) numberString = '0' + numberString;
        if (numberString.endsWith(".")) numberString = numberString + '0';
        if (numberString.startsWith("0x")) {
            numberString = numberString.substring(2);
            try {
                Long l = Long.parseUnsignedLong(numberString, 16);
                return new JsonPrimitive(l);
            } catch (NumberFormatException nfe) {
                throw new SyntaxError("Tried to parse '" + numberString + "' as a hexadecimal number, but it appears to be invalid.");
            }
        }
        if (numberString.startsWith("-0x")) {
            numberString = numberString.substring(3);
            try {
                Long l = -Long.parseUnsignedLong(numberString, 16);
                return new JsonPrimitive(l);
            } catch (NumberFormatException nfe) {
                throw new SyntaxError("Tried to parse '" + numberString + "' as a hexadecimal number, but it appears to be invalid.");
            }
        }


        if (numberString.indexOf('.') != -1) {
            //Return as a Double
            try {
                Double d = Double.valueOf(numberString);
                return new JsonPrimitive(d);
            } catch (NumberFormatException ex) {
                throw new SyntaxError("Tried to parse '" + numberString + "' as a floating-point number, but it appears to be invalid.");
            }
        } else {
            //Return as a Long
            try {
                Long l = Long.valueOf(numberString);
                return new JsonPrimitive(l);
            } catch (NumberFormatException ex) {
                throw new SyntaxError("Tried to parse '" + numberString + "' as an integer, but it appears to be invalid.");
            }
        }
    }

}
