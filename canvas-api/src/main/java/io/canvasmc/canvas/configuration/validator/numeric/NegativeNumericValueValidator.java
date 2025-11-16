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

public class NegativeNumericValueValidator implements AnnotationValidator<NegativeNumericValueValidator.NegativeNumericValue> {
    @Override
    public ValidationResult read(final NegativeNumericValue annotation, final JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.getValue() instanceof Number number) {
            if (!(number.floatValue() < 0)) {
                throw new ValidationException("Value must be a negative value");
            }
            return ValidationResult.PASS;
        }
        throw new ValidationException("NegativeNumericValue validation applied to a non-numeric object.");
    }

    @Override
    public Class<NegativeNumericValue> typeOf() {
        return NegativeNumericValue.class;
    }

    /**
     * Must be less than 0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NegativeNumericValue {
    }
}
