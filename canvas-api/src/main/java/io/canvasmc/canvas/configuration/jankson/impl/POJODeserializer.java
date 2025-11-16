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
import io.canvasmc.canvas.configuration.jankson.JsonGrammar;
import io.canvasmc.canvas.configuration.jankson.JsonNull;
import io.canvasmc.canvas.configuration.jankson.JsonObject;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import io.canvasmc.canvas.configuration.jankson.annotation.Deserializer;
import io.canvasmc.canvas.configuration.writer.SerializedName;
import io.canvasmc.canvas.configuration.jankson.api.DeserializationException;
import io.canvasmc.canvas.configuration.jankson.api.Marshaller;
import io.canvasmc.canvas.configuration.jankson.impl.serializer.DeserializerFunctionPool;
import io.canvasmc.canvas.configuration.jankson.impl.serializer.InternalDeserializerFunction;
import io.canvasmc.canvas.configuration.jankson.magic.TypeMagic;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class POJODeserializer {


    public static void unpackObject(Object target, JsonObject source) {
        try {
            unpackObject(target, source, false);
        } catch (Throwable ignored) {
        }
    }

    public static void unpackObject(Object target, JsonObject source, boolean failFast) throws DeserializationException {
        //if (o.getClass().getTypeParameters().length>0) throw new DeserializationException("Can't safely deserialize generic types!");
        //well, let's try anyway and see if we run into problems.

        //Create a copy we can redact keys from
        JsonObject work = source.copy();

        //Fill public and private fields declared in the target object's immediate class
        for (Field f : target.getClass().getDeclaredFields()) {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) continue;
            unpackField(target, f, work, failFast);
        }

        //Attempt to fill public, accessible fields declared in the target object's superclass.
        for (Field f : target.getClass().getFields()) {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) continue;
            unpackField(target, f, work, failFast);
        }

        if (!work.isEmpty() && failFast) {
            throw new DeserializationException("There was data that couldn't be applied to the destination object: " + work.toJson(JsonGrammar.STRICT));
        }
    }

    @SuppressWarnings("deprecation")
    public static void unpackField(Object parent, Field f, JsonObject source, boolean failFast) throws DeserializationException {
        String fieldName = f.getName();
        SerializedName nameAnnotation = f.getAnnotation(SerializedName.class);
        if (nameAnnotation != null) fieldName = nameAnnotation.value();

        if (source.containsKey(fieldName)) {
            JsonElement elem = source.get(fieldName);
            source.remove(fieldName); //Prevent it from getting re-unpacked
            if (elem == null || elem == JsonNull.INSTANCE) {
                boolean accessible = f.isAccessible();
                if (!accessible) f.setAccessible(true);
                try {
                    f.set(parent, null);
                    if (!accessible) f.setAccessible(false);
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    if (failFast)
                        throw new DeserializationException("Couldn't set field \"" + f.getName() + "\" of class \"" + parent.getClass().getCanonicalName() + "\"", ex);
                }
            } else {
                try {
                    unpackFieldData(parent, f, elem, source.getMarshaller());
                } catch (Throwable t) {
                    if (failFast)
                        throw new DeserializationException("There was a problem unpacking field " + f.getName() + " of class " + parent.getClass().getCanonicalName(), t);
                }
            }
        }
    }


    /**
     * NOT WORKING YET, HIGHLY EXPERIMENTAL
     */
    @Nullable
    public static Object unpack(Type t, @Nullable JsonElement elem, Marshaller marshaller) {
        Class<?> rawClass = TypeMagic.classForType(t);
        if (rawClass == null) return null;
        if (rawClass.isPrimitive())
            return null; //We can't unpack a primitive into an object of primitive type. Maybe in the future we can return a boxed type?

        if (elem == null) return null;
		/*
		if (type instanceof Class) {
			try {
				return marshaller.marshall((Class<?>) type, elem);
			} catch (ClassCastException t) {
				return null;
			}
		}
		
		if (type instanceof ParameterizedType) {
			try {
				Class<?> clazz = (Class<?>) TypeMagic.classForType(type);
				
				if (List.class.isAssignableFrom(clazz)) {
					Object result = TypeMagic.createAndCast(type);
					
					try {
						unpackList((List<Object>) result, type, elem, marshaller);
						return result;
					} catch (DeserializationException e) {
						e.printStackTrace();
						return result;
					}
				}
				
				return null;
			} catch (ClassCastException t) {
				return null;
			}
		}*/

        return null;
    }

    @SuppressWarnings("unchecked")
    public static boolean unpackFieldData(Object parent, Field field, @Nullable JsonElement elem, Marshaller marshaller) throws Throwable {

        if (elem == null) return true;
        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            return false; //skip this field probably.
        }

        if (elem == JsonNull.INSTANCE) {
            field.set(parent, null);
            return true;
        }

        Class<?> fieldClass = field.getType();

        if (!isCollections(fieldClass)) {
            //Try to directly marshall
            Object result = marshaller.marshallCarefully(fieldClass, elem);
            field.set(parent, result);
            return true;
        }


        if (field.get(parent) == null) {
            Object fieldValue = TypeMagic.createAndCast(field.getGenericType());

            if (fieldValue == null) {
                return false; //Can't deserialize this somehow
            } else {
                field.set(parent, fieldValue);
            }
        }

        if (Map.class.isAssignableFrom(fieldClass)) {
            Type[] parameters = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();

            unpackMap((Map<Object, Object>) field.get(parent), parameters[0], parameters[1], elem, marshaller);

            return true;
        }

        if (Collection.class.isAssignableFrom(fieldClass)) {
            Type elementType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];

            unpackCollection((Collection<Object>) field.get(parent), elementType, elem, marshaller);

            return true;
        }

        return false;
    }

    private static boolean isCollections(Class<?> clazz) {
        return
            Map.class.isAssignableFrom(clazz) ||
                Collection.class.isAssignableFrom(clazz);
    }

    public static void unpackMap(Map<Object, Object> map, Type keyType, Type valueType, JsonElement elem, Marshaller marshaller) throws DeserializationException {
        if (!(elem instanceof final JsonObject object))
            throw new DeserializationException("Cannot deserialize a " + elem.getClass().getSimpleName() + " into a Map - expected a JsonObject!");

        //Class<?> keyClass = TypeMagic.classForType(keyType);
        //Class<?> valueClass = TypeMagic.classForType(valueType);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                Object k = marshaller.marshall(keyType, new JsonPrimitive(entry.getKey()));
                Object v = marshaller.marshall(valueType, entry.getValue());
                if (k != null && v != null) map.put(k, v);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void unpackCollection(Collection<Object> collection, Type elementType, JsonElement elem, Marshaller marshaller) throws DeserializationException {
        if (!(elem instanceof final JsonArray array))
            throw new DeserializationException("Cannot deserialize a " + elem.getClass().getSimpleName() + " into a Set - expected a JsonArray!");

        for (JsonElement arrayElem : array) {

            Object o = marshaller.marshall(elementType, arrayElem);
            if (o != null) collection.add(o);
        }
    }

    protected static <B> DeserializerFunctionPool<B> deserializersFor(Class<B> targetClass) {
        DeserializerFunctionPool<B> pool = new DeserializerFunctionPool<>(targetClass);
        for (Method m : targetClass.getDeclaredMethods()) {
            //System.out.println("Examining "+m.getName());
            if (m.getAnnotation(Deserializer.class) == null) continue; //Must be annotated

            if (!Modifier.isStatic(m.getModifiers())) continue; //Must be static
            if (!m.getReturnType().equals(targetClass)) continue; //Must return an instance of the class
            //System.out.println("    Cleared first screening");
            Parameter[] params = m.getParameters();
            if (params.length >= 1) {
                Class<?> sourceClass = params[0].getType();
                InternalDeserializerFunction<B> deserializer = makeDeserializer(m, sourceClass, targetClass);
                if (deserializer == null) continue;
                pool.registerUnsafe(sourceClass, deserializer);
                //System.out.println("    Registered deserializer");
            }
        }
        return pool;
    }

    /**
     * Assuming the method is a valid deserializer, and matches the type signature required, produces a DeserializerFunction which delegates to the method provided.
     * If the method is not a valid deserializer of this type, returns null instead.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static <A, B> InternalDeserializerFunction<B> makeDeserializer(Method m, Class<A> sourceClass, Class<B> targetClass) {
        if (!m.getReturnType().equals(targetClass)) return null;
        Parameter[] params = m.getParameters();
        if (params.length == 1) {
            //if (params[0].getClass().isAssignableFrom(sourceClass)) {
            return (Object o, Marshaller marshaller) -> {
                try {
                    return (B) m.invoke(null, o);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    throw new DeserializationException(ex);
                }
            };
            //}
            //return null;
        } else if (params.length == 2) {
            //if (params[0].getClass().isAssignableFrom(sourceClass)) {
            /* todo: seemingly always equals to false
            if (params[1].getClass().equals(Marshaller.class)) {
                return (Object o, Marshaller marshaller) -> {
                    try {
                        return (B) m.invoke(null, o, marshaller);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                        throw new DeserializationException(ex);
                    }
                };
            }
            //}
             */
            return null;
        } else {
            return null;
        }
    }
}
