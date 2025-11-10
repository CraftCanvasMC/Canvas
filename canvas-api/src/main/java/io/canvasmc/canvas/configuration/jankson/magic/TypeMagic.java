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

package io.canvasmc.canvas.configuration.jankson.magic;

import io.canvasmc.canvas.configuration.jankson.api.DeserializationException;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;

public class TypeMagic {
    private static final Map<Class<?>, Class<?>> concreteClasses = new HashMap<>();

    static {
        concreteClasses.put(Map.class, HashMap.class);
        concreteClasses.put(Set.class, HashSet.class);
        concreteClasses.put(Collection.class, ArrayList.class);
        concreteClasses.put(List.class, ArrayList.class);
        concreteClasses.put(Queue.class, ArrayDeque.class);
        concreteClasses.put(Deque.class, ArrayDeque.class);
    }


    /**
     * This is a surprisingly intractable problem in Java: "Type" pretty much represents all possible states of reified
     * and unreified type information, and each kind of Type has different, mutually exclusive, and often unintended
     * ways of uncovering its (un-reified) class.
     *
     * <p>Generally it's much safer to use this for the type from a *field* than a blind type from an argument.
     */
    @Nullable
    public static Class<?> classForType(Type t) {
        if (t instanceof Class) return (Class<?>) t;

        if (t instanceof ParameterizedType) {
            Type subtype = ((ParameterizedType) t).getRawType();

            /**
             * Testing for kind of a unicorn case here. Because getRawType returns a Type, there's always the nasty
             * possibility we get a recursively parameterized type. Now, that's not supposed to happen, but let's not
             * rely on "supposed to".
             */
            if (subtype instanceof Class) {
                return (Class<?>) subtype;
            } else {
                /**
                 * We're here at the unicorn case, against all odds. Let's take a lexical approach: The typeName will
                 * always start with the FQN of the class, followed by
                 */

                String className = t.getTypeName();
                int typeParamStart = className.indexOf('<');
                if (typeParamStart >= 0) {
                    className = className.substring(0, typeParamStart);
                }

                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException ex) {
                }
            }
        }

        if (t instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) t).getUpperBounds();
            if (upperBounds.length == 0) return Object.class; //Well, we know it's an Object class.....
            return classForType(upperBounds[0]); //I'm skeptical about multiple bounds on this one, but so far it's been okay.
        }

        if (t instanceof TypeVariable) {
            return Object.class;
			/*//This gets us into all kinds of trouble with multiple bounds, it turns out
			Type[] types = ((TypeVariable<?>)t).getBounds();
			if (types.length==0) return Object.class;
			return classForType(types[0]);*/
        }

        if (t instanceof final GenericArrayType arrayType) {
            /* ComponentClass will in practice return a TypeVariable, which will resolve to Object.
             * This is actually okay, because *any time* you try and create a T[], you'll wind up making an Object[]
             * instead and stuffing it into the T[]. And then it'll work.
             *
             * And if Java magically improves their reflection system and/or less-partially reifies generics down the line,
             * we can improve the TypeVariable case and wind up with more correctly-typed classes here.
             */
            Class<?> componentClass = classForType(arrayType.getGenericComponentType());
            try {
                //We can always retrieve the class under a "dots" version of the binary name, as long as componentClass wound up resolving to a valid Object type
                Class<?> arrayClass = Class.forName("[L" + componentClass.getCanonicalName() + ";");

                return arrayClass;
            } catch (ClassNotFoundException ex2) {
                return Object[].class; //This is probably what we're serving up anyway, so we might as well give the known-at-compile-time one out as a last resort.
            }
        }

        return null;
    }

    /**
     * Attempts to create a new instance of type t, and (unsafely) cast it to the target type U. This might work even if
     * the class is private or has a private constructor.
     *
     * @param <U> the target type.
     * @param t   the source type. The object will be created as this type.
     * @return an object of type t, cast to type U. If any part of this process fails, this method silently returns null
     * instead.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <U> U createAndCast(Type t) {
        try {
            return (U) createAndCast(classForType(t), false);
        } catch (Throwable ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static <U> U createAndCastCarefully(Type t) throws DeserializationException {
        return createAndCast(classForType(t));
    }

    /**
     * Attempts to create a new instance of the specified class using its no-arg constructor, if it has one. This might
     * work even if the class is private or the constructor is private/hidden!
     *
     * @param <U> the target type.
     * @param t   the source type. The object will be created as this type.
     * @return a new object of type U. If any part of this process fails, this method silently returns null instead.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <U> U createAndCast(Class<U> t, boolean failFast) throws DeserializationException {
        if (t.isInterface()) {
            Class<?> substitute = concreteClasses.get(t);
            if (substitute != null) try {
                return createAndCast(substitute);
            } catch (Throwable ex) {
                return null;
            }
        }

        /* Using getConstructor instead of class::newInstance takes some errors we can't otherwise detect, and
         * instead wraps them in InvocationTargetExceptions which we *can* catch.
         */
        Constructor<U> noArg = null;
        try {
            noArg = t.getConstructor();
        } catch (Throwable ex2) {
            try {
                noArg = t.getDeclaredConstructor();
            } catch (Throwable ex3) {
                if (failFast) {
                    throw new DeserializationException("Class " + t.getCanonicalName() + " doesn't have a no-arg constructor, so an instance can't be created.");
                }
                return null;
            }
        }

        try {
            boolean available = noArg.isAccessible();
            if (!available) noArg.setAccessible(true);
            U u = noArg.newInstance();
            if (!available) noArg.setAccessible(false); //restore accessibility
            return u;
        } catch (Throwable ex) {
            if (failFast) {
                throw new DeserializationException("An error occurred while creating an object.", ex);
            }
            return null;
        }
    }

    /**
     * Extremely unsafely casts an object into another type. It's possible to mangle a List&lt;Integer&gt; into a
     * List&lt;String&gt; this way, and the JVM might not throw an error until the program attempts to insert a String!
     * So use this method with extreme caution as a last resort.
     *
     * @param <T> the destination type
     * @param o   the source object, of any type
     * @return the source object cast to T.
     */
    @SuppressWarnings("unchecked")
    public static <T> T shoehorn(Object o) {
        return (T) o;
    }
}
