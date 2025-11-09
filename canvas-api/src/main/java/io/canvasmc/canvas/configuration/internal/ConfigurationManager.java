package io.canvasmc.canvas.configuration.internal;

import io.canvasmc.canvas.configuration.ConfigSerializer;
import io.canvasmc.canvas.configuration.Configuration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public class ConfigurationManager {
    private static final Map<Class<?>, ConfigHolder<?>> holders = new ConcurrentHashMap<>();

    private ConfigurationManager() {
    }

    public static <T> @NotNull ConfigHolder<T> register(Class<T> configClass, ConfigSerializer.Factory<T> serializerFactory) {
        Objects.requireNonNull(configClass);
        Objects.requireNonNull(serializerFactory);
        if (holders.containsKey(configClass)) {
            holders.remove(configClass);
        }
        Configuration definition = configClass.getAnnotation(Configuration.class);
        if (definition == null) {
            throw new RuntimeException(String.format("No @Configuration annotation on %s!", configClass));
        } else {
            ConfigSerializer<T> serializer = serializerFactory.create(definition, configClass);
            InternalConfigManager<T> manager = new InternalConfigManager<>(definition, configClass, serializer);
            holders.put(configClass, manager);
            return manager;
        }
    }

    public static <T> ConfigHolder<T> getConfigHolder(Class<T> configClass) {
        Objects.requireNonNull(configClass);
        if (holders.containsKey(configClass)) {
            //noinspection unchecked
            return (ConfigHolder<T>) holders.get(configClass);
        } else {
            throw new RuntimeException(String.format("Config '%s' has not been registered", configClass));
        }
    }
}
