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
import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonNull;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import io.canvasmc.canvas.configuration.jankson.api.SyntaxError;
import java.util.Locale;

public class ElementParserContext implements ParserContext<AnnotatedElement> {
    String comment = null;
    AnnotatedElement result = null;
    boolean childActive = false;

    @Override
    public boolean consume(int codePoint, Jankson loader) throws SyntaxError {
        //Figure out element type and dispatch down to

        if (Character.isWhitespace(codePoint)) return true; //Whitespace
        switch (codePoint) {
            case '/':
            case '#': //Comment

                loader.push(new CommentParserContext(codePoint), (it) -> comment = it);
                return true;
            case '\'':
            case '"': //String
                loader.push(new StringParserContext(codePoint), this::setResult);
                childActive = true;
                return true;
            case '{': //Object
                loader.push(new ObjectParserContext(), this::setResult);
                childActive = true;
                return false; //Give the opening brace to the object context
            case '[': //Array
                loader.push(new ArrayParserContext(), this::setResult);
                childActive = true;
                return true;

            case '}':
                loader.throwDelayed(new SyntaxError("Found '" + ((char) codePoint) + "' while parsing an element - this shouldn't happen!"));
                return false;
            case ']':
                result = new AnnotatedElement(null, comment);
			/*
			if (this.result==null) {
				System.out.println("Comment parser didn't release its context in time?");
				loader.throwDelayed(new SyntaxError("Found '"+((char)codePoint)+"' while parsing an element - this shouldn't happen!"));
			} else {
				System.out.println("We're being called after we're done");
			}*/
                return false;
            default:
                if (Character.isDigit(codePoint) || codePoint == '-' || codePoint == '+' || codePoint == '.') {
                    loader.push(new NumberParserContext(codePoint), this::setResult);
                    childActive = true;
                    return true;
                }

                loader.push(new TokenParserContext(codePoint), (it) -> {
                    String token = it.asString().toLowerCase(Locale.ROOT);

                    switch (token) {
                        case "null":
                            setResult(JsonNull.INSTANCE);
                            break;
                        case "true":
                            setResult(JsonPrimitive.TRUE);
                            break;
                        case "false":
                            setResult(JsonPrimitive.FALSE);
                            break;
                        case "infinity": //handled by this token context
                        case "+infinity": //handled by number context. here for completeness
                            setResult(new JsonPrimitive(Double.POSITIVE_INFINITY));
                            break;
                        case "-infinity": //number context
                            setResult(new JsonPrimitive(Double.NEGATIVE_INFINITY));
                            break;
                        case "nan": //token context
                            setResult(new JsonPrimitive(Double.NaN));
                            break;
                        default:
                            setResult(it);
                            //loader.throwDelayed(new SyntaxError("Found unrecognized token '"+it.asString()+"' while looking for a json element of any type."));
                            break;
                    }
				/*
				if (it.asString().toLowerCase(Locale.ROOT).equals("null")) {
					setResult(JsonNull.INSTANCE);
				} else if (it.asString().toLowerCase(Locale.ROOT).equals("true")) {
					setResult(JsonPrimitive.TRUE);
				} else if (it.asString().toLowerCase(Locale.ROOT).equals("false")) {
					setResult(JsonPrimitive.FALSE);
				} else if (it.asString().toLowerCase(Locale.ROOT).equals("infinity")) {
					setResult(new JsonPrimitive(Double.POSITIVE_INFINITY));
				} else {
					loader.throwDelayed(new SyntaxError("Found unrecognized token '"+it.asString()+"' while looking for a json element of any type."));
				}*/
                });
                childActive = true;

                return true;
        }

    }

    @Override
    public void eof() throws SyntaxError {
        //We should be fine as long as any child parser has been initiated.
        if (!childActive) throw new SyntaxError("Unexpected end-of-file while looking for a json element!");
    }

    @Override
    public boolean isComplete() {
        return result != null;
    }

    @Override
    public AnnotatedElement getResult() throws SyntaxError {
        return result;
    }

    public void setResult(JsonElement elem) {
        result = new AnnotatedElement(elem, comment);
    }

}
