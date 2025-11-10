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

public class RangeValidator implements AnnotationValidator<RangeValidator.Range> {
    @Override
    public ValidationResult read(final Range range, final JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.getValue() instanceof Number number) {
            float floatValue = number.floatValue();
            int from = range.from();
            int to = range.to();
            if (!(range.inclusive() ? (floatValue >= from && floatValue <= to) : floatValue > from && floatValue < to)) {
                throw new ValidationException("Number was out of bounds! Value must be between " + from + " and " + to);
            }
            return ValidationResult.PASS;
        } else {
            throw new ValidationException("Range validation was applied to a non-numeric object.");
        }
    }

    @Override
    public Class<Range> typeOf() {
        return Range.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Range {
        int from();

        int to();

        boolean inclusive() default false;
    }
}
