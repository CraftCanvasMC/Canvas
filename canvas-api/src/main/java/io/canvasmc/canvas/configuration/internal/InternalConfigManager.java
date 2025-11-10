package io.canvasmc.canvas.configuration.internal;

import io.canvasmc.canvas.configuration.ConfigSerializer;
import io.canvasmc.canvas.configuration.Configuration;
import io.canvasmc.canvas.configuration.validator.ValidationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public class InternalConfigManager<T> implements ConfigHolder<T> {
    private final Logger logger = LoggerFactory.getLogger("InternalConfigurationManager");
    private final Configuration definition;
    private final Class<T> configClass;
    private final ConfigSerializer<T> serializer;
    private T config;

    InternalConfigManager(Configuration definition, Class<T> configClass, ConfigSerializer<T> serializer) {
        this.definition = definition;
        this.configClass = configClass;
        this.serializer = serializer;
        if (this.load()) {
            this.save();
        } else throw new RuntimeException("Couldn't load config '" + definition.value() + "'");

    }

    public Configuration getDefinition() {
        return this.definition;
    }

    public @NotNull Class<T> getConfigClass() {
        return this.configClass;
    }

    public ConfigSerializer<T> getSerializer() {
        return this.serializer;
    }

    public void save() {
        try {
            this.serializer.write(this.config);
        } catch (ConfigSerializer.SerializationException e) {
            this.logger.error("Failed to save config '{}'", this.configClass, e);
        }

    }

    public boolean load() {
        try {

            this.config = this.serializer.read();
            return true;
        } catch (ValidationException | ConfigSerializer.SerializationException e) {
            this.logger.error("Failed to load config '{}', using default!", this.configClass, e);
            this.resetToDefault();
            return false;
        }
    }

    public T getConfig() {
        return this.config;
    }

    public void setConfig(T config) {
        this.config = config;
    }

    public void resetToDefault() {
        this.config = this.serializer.createDefault();
    }

}
