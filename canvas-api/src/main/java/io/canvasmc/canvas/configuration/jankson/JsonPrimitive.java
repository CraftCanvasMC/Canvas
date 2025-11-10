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

package io.canvasmc.canvas.configuration.jankson;

import java.util.Objects;
import javax.annotation.Nonnull;

public class JsonPrimitive extends JsonElement {
    /**
     * Convenience instance of json "true". Don't use identity comparison (==) on these! Use equals instead.
     */
    public static JsonPrimitive TRUE = new JsonPrimitive(Boolean.TRUE);
    /**
     * Convenience instance of json "false". Don't use identity comparison (==) on these! Use equals instead.
     */
    public static JsonPrimitive FALSE = new JsonPrimitive(Boolean.FALSE);

    @Nonnull
    private final Object value;

    public JsonPrimitive(@Nonnull Object value) {
        this.value = value;
    }

    public static String escape(String s) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            switch (ch) {
                case '\u0008':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                default:
                    result.append(ch);
            }
        }

        return result.toString();
    }

    @Nonnull
    public String asString() {
        if (value == null) return "null";
        return value.toString();
    }

    public boolean asBoolean(boolean defaultValue) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        } else {
            return defaultValue;
        }
    }

    public byte asByte(byte defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        } else {
            return defaultValue;
        }
    }

    public char asChar(char defaultValue) {
        if (value instanceof Number) {
            return (char) ((Number) value).intValue();
        } else if (value instanceof Character) {
            return ((Character) value).charValue();
        } else if (value instanceof String) {
            if (((String) value).length() == 1) {
                return ((String) value).charAt(0);
            } else {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    public short asShort(short defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        } else {
            return defaultValue;
        }
    }

    public int asInt(int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return defaultValue;
        }
    }

    public long asLong(long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            return defaultValue;
        }
    }

    public float asFloat(float defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else {
            return defaultValue;
        }
    }

    public double asDouble(double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            return defaultValue;
        }
    }

    @Nonnull
    public String toString() {
        return toJson();
    }

    @Nonnull
    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other instanceof JsonPrimitive) {
            return Objects.equals(value, ((JsonPrimitive) other).value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toJson(boolean comments, boolean newlines, int depth) {
        return toJson(JsonGrammar.builder().withComments(comments).printWhitespace(newlines).build(), depth);
    }

    @Override
    public String toJson(JsonGrammar grammar, int depth) {

        if (value == null) return "null";

        if (value instanceof Double && grammar.bareSpecialNumerics) {
            double d = ((Double) value).doubleValue();
            if (Double.isNaN(d)) return "NaN";
            if (Double.isInfinite(d)) {
                if (d < 0) {
                    return "-Infinity";
                } else {
                    return "Infinity";
                }
            }
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) return value.toString();

        return '\"' + escape(value.toString()) + '\"';
    }

    //IMPLEMENTATION for Cloneable
    @Override
    public JsonPrimitive clone() {
        return this;
    }
}
