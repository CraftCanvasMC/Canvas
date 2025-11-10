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

import io.canvasmc.canvas.configuration.jankson.JsonElement;
import java.lang.reflect.Type;

public interface Marshaller {
    /**
     * Turns a java object into its json intermediate representation.
     */
    JsonElement serialize(Object obj);

    /**
     * Unpacks the provided JsonElement into a new object of type {@code clazz}, making a best
     * effort to unpack all the fields it can. Any fields that cannot be unpacked will be left in
     * the state the initializer and no-arg constructor leaves them in.
     *
     * <p>Note: Consider using {@link #marshallCarefully(Class, JsonElement)} to detect errors first,
     * and then calling this method as a fallback if an error is encountered.
     *
     * @param clazz The class of the object to create and deserialize
     * @param elem  json intermediate representation of the data to be unpacked.
     * @param <E>   The type of the object to create and deserialize
     * @return A new object of the provided class that represents the data in the json provided.
     */
    <E> E marshall(Class<E> clazz, JsonElement elem);

    /**
     * Unpacks the provided JsonElement into an object of the provided Type, and force-casts it to
     * E.
     *
     * @param type The type to deserialize to
     * @param elem json intermediate representation of the data to be unpacked.
     * @param <E>  The type to force-cast to at the end
     * @return A new object of the provided Type that represents the data in the json provided.
     */
    <E> E marshall(Type type, JsonElement elem);

    /**
     * Unpacks the provided JsonElement in fail-fast mode. A detailed exception is thrown for any
     * problem encountered during the unpacking process.
     *
     * @param clazz The class of the object to create and deserialize
     * @param elem  json intermediate representation of the data to be unpacked.
     * @param <E>   The type of the object to create and deserialize
     * @return A new object of the provided class that represents the data in the json provided.
     * @throws DeserializationException if any problems are encountered unpacking the data.
     */
    <E> E marshallCarefully(Class<E> clazz, JsonElement elem) throws DeserializationException;
}
