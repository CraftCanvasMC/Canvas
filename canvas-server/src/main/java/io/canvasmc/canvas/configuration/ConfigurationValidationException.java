package io.canvasmc.canvas.configuration;

public class ConfigurationValidationException extends RuntimeException {
    public ConfigurationValidationException(String field, Object value, String reason) {
        super("Validation failed for field '" + field + "' with value '" + value + "': " + reason);
    }
}
