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

/**
 * Represents a style of json written out, and a set of quirks to parse going in.
 * Typically you'll want to use a different grammar in than out.
 */
public class JsonGrammar {
    /**
     * A grammar which will accept all supported quirks, and output JSON-with-comments, which is a
     * valid jankson subset. This is the default behavior.
     */
    public static final JsonGrammar JANKSON = builder().bareSpecialNumerics(true).build();

    /**
     * A grammar which will accept JSON5 and output JSON-with-comments with trailing commas.
     */
    public static final JsonGrammar JSON5 = builder()
        .withComments(true)
        .printTrailingCommas(true)
        .bareSpecialNumerics(true)
        .build();

    /**
     * A grammar which will only accept or output strict JSON.
     */
    public static final JsonGrammar STRICT = builder()
        .withComments(false)
        .build();

    /**
     * A grammar which will print compactified JSON readable by almost all vanilla json parsers.
     * (Note: Jackson may read special numerics like NaN in as Strings)
     */
    public static final JsonGrammar COMPACT = builder()
        .withComments(false)
        .printWhitespace(false)
        .bareSpecialNumerics(true)
        .build();

    protected boolean comments = true;
    protected boolean printWhitespace = true;
    protected boolean printCommas = true;
    protected boolean printTrailingCommas = false;
    protected boolean bareSpecialNumerics = false;
    protected boolean bareRootObject = false;
    protected boolean printUnquotedKeys = false;

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasComments() {
        return comments;
    }

    public boolean shouldOutputWhitespace() {
        return printWhitespace;
    }

    public static class Builder {
        private final JsonGrammar grammar = new JsonGrammar();

        /**
         * Indicates that comments should be accepted as input, and preserved in output.
         * Defaults to true.
         */
        public Builder withComments(boolean comments) {
            grammar.comments = comments;
            return this;
        }

        /**
         * Indicates whether additional tabs and newlines should be printed to make json more
         * readable for humans. If false, output will be somewhat minified to save space. Defaults
         * to true.
         */
        public Builder printWhitespace(boolean whitespace) {
            grammar.printWhitespace = whitespace;
            return this;
        }

        /**
         * Indicates whether commas should be output to make the result more readable. Setting this
         * to false will cause the output to be invalid JSON5! (but still correct Jankson). Defaults
         * to true.
         */
        public Builder printCommas(boolean commas) {
            grammar.printCommas = commas;
            return this;
        }

        /**
         * If true, JSON5 trailing commas will be printed in all objects and lists. Has no affect on
         * parsing, and has no effect on output if {@link #printCommas(boolean)} is false. Defaults
         * to false.
         */
        public Builder printTrailingCommas(boolean trailing) {
            grammar.printTrailingCommas = trailing;
            return this;
        }

        /**
         * When printing output, print numeric values like NaN and Infinity without quotes. These
         * will get picked up by Gson and HJSON as-is, and apparently Jackson will read them as unquoted strings.
         */
        public Builder bareSpecialNumerics(boolean bare) {
            grammar.bareSpecialNumerics = bare;
            return this;
        }

        /**
         * When printing out a root object, omit the opening and closing braces ( "{}" )
         */
        public Builder bareRootObject(boolean bare) {
            grammar.bareRootObject = bare;
            return this;
        }

        public Builder printUnquotedKeys(boolean unquoted) {
            grammar.printUnquotedKeys = unquoted;
            return this;
        }

        /**
         * Finalizes this JsonGrammar and returns it.
         */
        public JsonGrammar build() {
            return grammar;
        }
    }
}
