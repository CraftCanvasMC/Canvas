package io.canvasmc.canvas.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public interface ConfigSerializer<T> {
    void serialize(T var1) throws ConfigSerializer.SerializationException;

    T deserialize() throws ConfigSerializer.SerializationException;

    T createDefault();

    default Path getConfigFolder() {
        return Paths.get("./");
    }

    default <V> V constructUnsafely(@NotNull Class<V> cls) {
        try {
            Constructor<V> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    default <V> V getUnsafely(Field field, Object obj) {
        if (obj == null) {
            return null;
        } else {
            try {
                field.setAccessible(true);
                //noinspection unchecked
                return (V) field.get(obj);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    default <V> V getUnsafely(Field field, Object obj, V defaultValue) {
        V ret = getUnsafely(field, obj);
        if (ret == null) {
            ret = defaultValue;
        }

        return ret;
    }

    default void setUnsafely(Field field, Object obj, Object newValue) {
        if (obj != null) {
            try {
                field.setAccessible(true);
                field.set(obj, newValue);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    default <T, K, U> Collector<T, ?, Map<K, U>> toLinkedMap(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper) {
        return Collectors.toMap(keyMapper, valueMapper, (u, _) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        }, LinkedHashMap::new);
    }

    @FunctionalInterface
    interface Factory<T> {
        ConfigSerializer<T> create(Configuration var1, Class<T> var2);
    }

    class SerializationException extends Exception {
        public SerializationException(Throwable cause) {
            super(cause);
        }
    }
}
