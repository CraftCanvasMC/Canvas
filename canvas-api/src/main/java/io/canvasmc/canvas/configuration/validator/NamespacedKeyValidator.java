package io.canvasmc.canvas.configuration.validator;

import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.kyori.adventure.key.Key;

public class NamespacedKeyValidator implements AnnotationValidator<NamespacedKeyValidator.NamespacedKey> {
    @Override
    public ValidationResult read(final NamespacedKey annotation, final JsonElement element) {
        if (element == null || !(element instanceof JsonPrimitive primitive)) {
            return ValidationResult.FAIL; // quick escape
        }
        String string = primitive.asString();
        if (string.isEmpty() || string.length() > Short.MAX_VALUE)
            throw new ValidationException("String is empty or exceeds char limit");

        String[] components = string.split(":", 3);
        if (components.length > 2) {
            throw new ValidationException("Contains multiple ':' characters");
        }

        String key = (components.length == 2) ? components[1] : "";
        if (components.length == 1) {
            String comp0 = components[0];
            if (comp0.isEmpty() || !Key.parseableValue(comp0)) {
                throw new ValidationException("Invalid namespaced key, comp0 was empty or unparsable");
            }

            return ValidationResult.PASS; // valid, use minecraft
        } else if (components.length == 2 && !Key.parseableValue(key)) {
            throw new ValidationException("Invalid/unparsable key");
        }

        String namespace = components[0];
        if (namespace.isEmpty()) {
            return ValidationResult.PASS; // valid, use minecraft
        }

        if (!Key.parseableNamespace(namespace)) {
            throw new ValidationException("Invalid/unparsable namespace");
        }

        return ValidationResult.PASS; // passed
    }

    @Override
    public Class<NamespacedKey> typeOf() {
        return NamespacedKey.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NamespacedKey {
    }
}
