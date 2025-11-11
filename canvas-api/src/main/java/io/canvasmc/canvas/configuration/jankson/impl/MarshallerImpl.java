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

import io.canvasmc.canvas.configuration.jankson.JsonArray;
import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonNull;
import io.canvasmc.canvas.configuration.jankson.JsonObject;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import io.canvasmc.canvas.configuration.writer.SerializedName;
import io.canvasmc.canvas.configuration.jankson.api.DeserializationException;
import io.canvasmc.canvas.configuration.jankson.api.DeserializerFunction;
import io.canvasmc.canvas.configuration.jankson.api.Marshaller;
import io.canvasmc.canvas.configuration.jankson.impl.serializer.DeserializerFunctionPool;
import io.canvasmc.canvas.configuration.jankson.impl.serializer.DeserializerFunctionPool.FunctionMatchFailedException;
import io.canvasmc.canvas.configuration.jankson.magic.TypeMagic;
import io.canvasmc.canvas.configuration.writer.Comment;
import io.canvasmc.canvas.configuration.writer.Util;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * @deprecated For removal; please use {@link Marshaller}
 */
@Deprecated
public class MarshallerImpl implements Marshaller {
    private static final MarshallerImpl INSTANCE = new MarshallerImpl();
    private final Map<Class<?>, Function<Object, ?>> primitiveMarshallers = new HashMap<>();
    private final Map<Class<?>, BiFunction<Object, Marshaller, JsonElement>> serializers = new HashMap<>();
    private final Map<Class<?>, DeserializerFunctionPool<?>> deserializers = new HashMap<>();
    private final Map<Class<?>, Supplier<?>> typeFactories = new HashMap<>();
    Map<Class<?>, Function<JsonObject, ?>> typeAdapters = new HashMap<>();

    public MarshallerImpl() {
        register(Void.class, (it) -> null);

        register(String.class, (it) -> (it instanceof String) ? (String) it : it.toString());

        register(Byte.class, (it) -> (it instanceof Number) ? ((Number) it).byteValue() : null);
        register(Character.class, (it) -> (it instanceof Number) ? (char) ((Number) it).shortValue() : it.toString().charAt(0));
        register(Short.class, (it) -> (it instanceof Number) ? ((Number) it).shortValue() : null);
        register(Integer.class, (it) -> (it instanceof Number) ? ((Number) it).intValue() : null);
        register(Long.class, (it) -> (it instanceof Number) ? ((Number) it).longValue() : null);
        register(Float.class, (it) -> (it instanceof Number) ? ((Number) it).floatValue() : null);
        register(Double.class, (it) -> (it instanceof Number) ? ((Number) it).doubleValue() : null);
        register(Boolean.class, (it) -> (it instanceof Boolean) ? (Boolean) it : null);

        register(Void.TYPE, (it) -> null);
        register(Byte.TYPE, (it) -> (it instanceof Number) ? ((Number) it).byteValue() : null);
        register(Character.TYPE, (it) -> (it instanceof Number) ? (char) ((Number) it).shortValue() : it.toString().charAt(0));
        register(Short.TYPE, (it) -> (it instanceof Number) ? ((Number) it).shortValue() : null);
        register(Integer.TYPE, (it) -> (it instanceof Number) ? ((Number) it).intValue() : null);
        register(Long.TYPE, (it) -> (it instanceof Number) ? ((Number) it).longValue() : null);
        register(Float.TYPE, (it) -> (it instanceof Number) ? ((Number) it).floatValue() : null);
        register(Double.TYPE, (it) -> (it instanceof Number) ? ((Number) it).doubleValue() : null);
        register(Boolean.TYPE, (it) -> (it instanceof Boolean) ? (Boolean) it : null);


        registerSerializer(Void.class, (it) -> JsonNull.INSTANCE);
        registerSerializer(Character.class, (it) -> new JsonPrimitive("" + it));
        registerSerializer(String.class, JsonPrimitive::new);
        registerSerializer(Byte.class, (it) -> new JsonPrimitive(Long.valueOf(it)));
        registerSerializer(Short.class, (it) -> new JsonPrimitive(Long.valueOf(it)));
        registerSerializer(Integer.class, (it) -> new JsonPrimitive(Long.valueOf(it)));
        registerSerializer(Long.class, JsonPrimitive::new);
        registerSerializer(Float.class, (it) -> new JsonPrimitive(Double.valueOf(it)));
        registerSerializer(Double.class, JsonPrimitive::new);
        registerSerializer(Boolean.class, JsonPrimitive::new);

        registerSerializer(Void.TYPE, (it) -> JsonNull.INSTANCE);
        registerSerializer(Character.TYPE, (it) -> new JsonPrimitive("" + it));
        registerSerializer(Byte.TYPE, (it) -> new JsonPrimitive(Long.valueOf(it)));
        registerSerializer(Short.TYPE, (it) -> new JsonPrimitive(Long.valueOf(it)));
        registerSerializer(Integer.TYPE, (it) -> new JsonPrimitive(Long.valueOf(it)));
        registerSerializer(Long.TYPE, JsonPrimitive::new);
        registerSerializer(Float.TYPE, (it) -> new JsonPrimitive(Double.valueOf(it)));
        registerSerializer(Double.TYPE, JsonPrimitive::new);
        registerSerializer(Boolean.TYPE, JsonPrimitive::new);
    }

    public static Marshaller getFallback() {
        return INSTANCE;
    }

    public <T> void register(Class<T> clazz, Function<Object, T> marshaller) {
        primitiveMarshallers.put(clazz, marshaller);
    }

    public <T> void registerTypeAdapter(Class<T> clazz, Function<JsonObject, T> adapter) {
        typeAdapters.put(clazz, adapter);
    }

    @SuppressWarnings("unchecked")
    public <T> void registerSerializer(Class<T> clazz, Function<T, JsonElement> serializer) {
        serializers.put(clazz, (it, marshaller) -> serializer.apply((T) it));
    }

    @SuppressWarnings("unchecked")
    public <T> void registerSerializer(Class<T> clazz, BiFunction<T, Marshaller, JsonElement> serializer) {
        serializers.put(clazz, (BiFunction<Object, Marshaller, JsonElement>) serializer);
    }

    public <T> void registerTypeFactory(Class<T> clazz, Supplier<T> supplier) {
        typeFactories.put(clazz, supplier);
    }

    public <A, B> void registerDeserializer(Class<A> sourceClass, Class<B> targetClass, DeserializerFunction<A, B> function) {
        @SuppressWarnings("unchecked")
        DeserializerFunctionPool<B> pool = (DeserializerFunctionPool<B>) deserializers.get(targetClass);
        if (pool == null) {
            pool = new DeserializerFunctionPool<B>(targetClass);
            deserializers.put(targetClass, pool);
        }
        pool.registerUnsafe(sourceClass, function);
    }

    /**
     * EXPERIMENTAL. Marshalls elem into a very specific parameterized type, honoring generic type arguments.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T marshall(Type type, @Nullable JsonElement elem) {
        if (elem == null) return null;
        if (elem == JsonNull.INSTANCE) return null;

        if (type instanceof Class) {
            try {
                return marshall((Class<T>) type, elem);
            } catch (ClassCastException t) {
                return null;
            }
        }

        if (type instanceof ParameterizedType) {
            try {
                Class<T> clazz = (Class<T>) TypeMagic.classForType(type);

                return marshall(clazz, elem);
            } catch (ClassCastException t) {
                return null;
            }
        }

        return null;
    }

    @Nullable
    public <T> T marshall(Class<T> clazz, JsonElement elem) {
        try {
            return marshall(clazz, elem, false);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public <T> T marshallCarefully(Class<T> clazz, JsonElement elem) throws DeserializationException {
        return marshall(clazz, elem, true);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T marshall(Class<T> clazz, @Nullable JsonElement elem, boolean failFast) throws DeserializationException {
        if (elem == null) return null;
        if (elem == JsonNull.INSTANCE) return null;
        if (clazz.isAssignableFrom(elem.getClass())) return (T) elem; //Already the correct type

        //Externally registered deserializers
        DeserializerFunctionPool<T> pool = (DeserializerFunctionPool<T>) deserializers.get(clazz);
        if (pool != null) {
            try {
                return pool.apply(elem, this);
            } catch (FunctionMatchFailedException e) {
                //Don't return the result, but continue
            }
        }

        //Internally annotated deserializers
        pool = POJODeserializer.deserializersFor(clazz);
        T poolResult;
        try {
            poolResult = pool.apply(elem, this);
            return poolResult;
        } catch (FunctionMatchFailedException e) {
            //Don't return the result, but continue
        }


        if (Enum.class.isAssignableFrom(clazz)) {
            if (!(elem instanceof JsonPrimitive)) return null;
            String name = ((JsonPrimitive) elem).getValue().toString();

            T[] constants = clazz.getEnumConstants();
            if (constants == null) return null;
            for (T t : constants) {
                if (((Enum<?>) t).name().equals(name)) return t;
            }
        }

        if (clazz.equals(String.class)) {
            //Almost everything has a String representation
            if (elem instanceof JsonObject) return (T) elem.toJson(false, false);
            if (elem instanceof JsonArray) return (T) elem.toJson(false, false);
            if (elem instanceof JsonPrimitive) {
                ((JsonPrimitive) elem).getValue();
                return (T) ((JsonPrimitive) elem).asString();
            }
            if (elem instanceof JsonNull) return (T) "null";

            if (failFast)
                throw new DeserializationException("Encountered unexpected JsonElement type while deserializing to string: " + elem.getClass().getCanonicalName());
            return null;
        }

        if (elem instanceof JsonPrimitive) {
            Function<Object, ?> func = primitiveMarshallers.get(clazz);
            if (func != null) {
                return (T) func.apply(((JsonPrimitive) elem).getValue());
            } else {
                if (failFast)
                    throw new DeserializationException("Don't know how to unpack value '" + elem + "' into target type '" + clazz.getCanonicalName() + "'");
                return null;
            }
        } else if (elem instanceof final JsonObject obj) {


            if (clazz.isPrimitive())
                throw new DeserializationException("Can't marshall json object into primitive type " + clazz.getCanonicalName());
            if (JsonPrimitive.class.isAssignableFrom(clazz)) {
                if (failFast) throw new DeserializationException("Can't marshall json object into a json primitive");
                return null;
            }

            obj.setMarshaller(this);

            if (typeAdapters.containsKey(clazz)) {
                return (T) typeAdapters.get(clazz).apply(obj);
            }

            if (typeFactories.containsKey(clazz)) {
                T result = (T) typeFactories.get(clazz).get();
                try {
                    POJODeserializer.unpackObject(result, obj, failFast);
                    return result;
                } catch (Throwable t) {
                    if (failFast) throw t;
                    return null;
                }
            } else {

                try {
                    T result = TypeMagic.createAndCast(clazz, failFast);
                    if (result == null) return null;
                    POJODeserializer.unpackObject(result, obj, failFast);
                    return result;
                } catch (Throwable t) {
                    if (failFast) throw t;
                    return null;
                }
            }

        } else if (elem instanceof final JsonArray array) {
            if (clazz.isPrimitive()) return null;
            if (clazz.isArray()) {
                Class<?> componentType = clazz.getComponentType();

                T result = (T) Array.newInstance(componentType, array.size());
                for (int i = 0; i < array.size(); i++) {
                    Array.set(result, i, marshall(componentType, array.get(i)));
                }
                return result;
            }
        }

        return null;
    }

    public JsonElement serialize(@Nullable Object obj) {
        if (obj == null) return JsonNull.INSTANCE;

        //Prefer exact match
        BiFunction<Object, Marshaller, JsonElement> serializer = serializers.get(obj.getClass());
        if (serializer != null) {
            JsonElement result = serializer.apply(obj, this);
            if (result instanceof JsonObject) ((JsonObject) result).setMarshaller(this);
            if (result instanceof JsonArray) ((JsonArray) result).setMarshaller(this);
            return result;
        } else {
            //Detailed match
            for (Map.Entry<Class<?>, BiFunction<Object, Marshaller, JsonElement>> entry : serializers.entrySet()) {
                if (entry.getKey().isAssignableFrom(obj.getClass())) {
                    JsonElement result = entry.getValue().apply(obj, this);
                    if (result instanceof JsonObject) ((JsonObject) result).setMarshaller(this);
                    if (result instanceof JsonArray) ((JsonArray) result).setMarshaller(this);
                    return result;
                }
            }
        }

        if (obj instanceof Enum) {
            return new JsonPrimitive(((Enum<?>) obj).name());
        }

        if (obj.getClass().isArray()) {

            JsonArray array = new JsonArray();
            array.setMarshaller(this);
            //Class<?> component = obj.getClass().getComponentType();
            for (int i = 0; i < Array.getLength(obj); i++) {
                Object elem = Array.get(obj, i);
                JsonElement parsed = serialize(elem);
                array.add(parsed);
            }
            return array;
        }

        if (obj instanceof Collection) {
            JsonArray array = new JsonArray();
            array.setMarshaller(this);
            for (Object elem : (Collection<?>) obj) {
                JsonElement parsed = serialize(elem);
                array.add(parsed);
            }
            return array;
        }

        if (obj instanceof Map) {
            JsonObject result = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                result.put(k, serialize(v));
            }
            return result;
        }

        JsonObject result = new JsonObject();
        //Pull in public fields first
        for (Field f : obj.getClass().getFields()) {
            if (
                (f.getModifiers() & Modifier.PUBLIC) == 0 || // Canvas - ignore non-public
                (f.getModifiers() & Modifier.FINAL) != 0 || // Canvas - ignore final
                Modifier.isStatic(f.getModifiers()) || // Not part of the object
                    Modifier.isTransient(f.getModifiers())) continue; //Never serialize
            f.setAccessible(true);

            try {
                Object child = f.get(obj);
                String name = f.getName();
                SerializedName nameAnnotation = f.getAnnotation(SerializedName.class);
                if (nameAnnotation != null) name = nameAnnotation.value();

                Comment comment = f.getAnnotation(Comment.class);
                if (comment == null) {
                    result.put(name, serialize(child));
                } else {
                    result.put(name, serialize(child), Util.multi(comment.value())); // Canvas - add multi-line
                }
            } catch (IllegalArgumentException | IllegalAccessException ignored) {
            }
        }

        //Add in what private fields we can reach
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (
                Modifier.isPublic(f.getModifiers()) || // Already serialized
                    Modifier.isStatic(f.getModifiers()) || // Not part of the object
                    Modifier.isTransient(f.getModifiers())) continue; //Never serialize
            f.setAccessible(true);

            try {
                Object child = f.get(obj);
                String name = f.getName();
                SerializedName nameAnnotation = f.getAnnotation(SerializedName.class);
                if (nameAnnotation != null) name = nameAnnotation.value();

                Comment comment = f.getAnnotation(Comment.class);
                if (comment == null) {
                    result.put(name, serialize(child));
                } else {
                    result.put(name, serialize(child), Util.multi(comment.value())); // Canvas - add multi-line
                }
            } catch (IllegalArgumentException | IllegalAccessException ignored) {
            }
        }

        return result;
    }
}
