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

package io.canvasmc.canvas.configuration.jankson.impl.serializer;

import io.canvasmc.canvas.configuration.jankson.JsonArray;
import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonGrammar;
import io.canvasmc.canvas.configuration.jankson.JsonObject;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import io.canvasmc.canvas.configuration.jankson.api.DeserializationException;
import io.canvasmc.canvas.configuration.jankson.api.Marshaller;
import java.util.HashMap;

/**
 * Stores deserializer functions that produce objects of type &lt;B&gt;
 */
public class DeserializerFunctionPool<B> {
    private final Class<B> targetClass;
    private final HashMap<Class<?>, InternalDeserializerFunction<B>> values = new HashMap<>();

    public DeserializerFunctionPool(Class<B> targetClass) {
        this.targetClass = targetClass;
    }

    public void registerUnsafe(Class<?> sourceClass, InternalDeserializerFunction<B> function) {
        values.put(sourceClass, function);
    }

    public InternalDeserializerFunction<B> getFunction(Class<?> sourceClass) {
        return values.get(sourceClass);
    }

    public B apply(JsonElement elem, Marshaller marshaller) throws DeserializationException, FunctionMatchFailedException {
        InternalDeserializerFunction<B> selected = null;

        //This whole block is pretty ugly but there's a very particular selection order
        if (elem instanceof JsonPrimitive) {
            //1. Unwrapped primitive class
            Object obj = ((JsonPrimitive) elem).getValue();
            selected = values.get(obj.getClass());
            if (selected != null) return selected.deserialize(obj, marshaller);

            //2. JsonPrimitive
            selected = values.get(JsonPrimitive.class);
            if (selected != null) return selected.deserialize(elem, marshaller);
        } else if (elem instanceof JsonObject) {
            //2. JsonObject
            selected = values.get(JsonObject.class);
            if (selected != null) return selected.deserialize(elem, marshaller);
        } else if (elem instanceof JsonArray) {
            //2. JsonArray
            selected = values.get(JsonArray.class);
            if (selected != null) return selected.deserialize(elem, marshaller);
        }

        //3. JsonElement
        selected = values.get(JsonElement.class);
        if (selected != null) return selected.deserialize(elem, marshaller);

        //We can't just return null, *because null might be the intended output of one of the functions above!*
        throw new FunctionMatchFailedException("Couldn't find a deserializer in class '" + targetClass.getCanonicalName() + "' to unpack element '" + elem.toJson(JsonGrammar.JSON5) + "'.");
    }

    public static class FunctionMatchFailedException extends Exception {
        private static final long serialVersionUID = -7909332778483440658L;

        public FunctionMatchFailedException(String message) {
            super(message);
        }
    }
}
