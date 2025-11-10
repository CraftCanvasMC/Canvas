package io.canvasmc.canvas.configuration.validator.numeric;

import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonPrimitive;
import io.canvasmc.canvas.configuration.validator.AnnotationValidator;
import io.canvasmc.canvas.configuration.validator.ValidationException;
import io.canvasmc.canvas.configuration.validator.ValidationResult;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class PositiveNumericValueValidator implements AnnotationValidator<PositiveNumericValueValidator.PositiveNumericValue> {
    @Override
    public ValidationResult read(final PositiveNumericValue annotation, final JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.getValue() instanceof Number number) {
            if (!(number.floatValue() > 0)) {
                throw new ValidationException("Value must be a positive value");
            }
            return ValidationResult.PASS;
        }
        throw new ValidationException("PositiveNumericValue validation applied to a non-numeric object.");
    }

    @Override
    public Class<PositiveNumericValue> typeOf() {
        return PositiveNumericValue.class;
    }

    /**
     * Must be greater than 0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface PositiveNumericValue {
    }
}
