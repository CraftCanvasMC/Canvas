package io.canvasmc.canvas.configuration.validator;

import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class PatternValidator implements AnnotationValidator<PatternValidator.Pattern> {
    @Override
    public ValidationResult read(final Pattern annotation, final JsonElement element) {
        if (!(element instanceof JsonPrimitive primitive)) {
            return ValidationResult.FAIL; // quick escape
        }
        String asString = primitive.asString();
        if (asString.matches(annotation.pattern())) {
            return ValidationResult.PASS;
        }
        return ValidationResult.FAIL;
    }

    @Override
    public Class<Pattern> typeOf() {
        return Pattern.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Pattern {
        String pattern();
    }
}
