package io.canvasmc.canvas.configuration.markers;

import io.canvasmc.canvas.configuration.ConfigurationValidationException;
import io.canvasmc.canvas.configuration.FieldValidator;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jspecify.annotations.NonNull;

@ValidationType(NumberRange.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NumberRange {
    Type value();

    int from() default Integer.MIN_VALUE;

    int to() default Integer.MAX_VALUE;

    enum Type {
        GREATER_THAN_0,
        LESS_THAN_0,
        GREATER_THAN_OR_EQUAL_TO_0,
        LESS_THAN_OR_EQUAL_TO_0,
        SPECIFIC_NUMBER
    }

    class Validator implements FieldValidator<NumberRange, Integer> {
        @Override
        public void validate(final String fieldName, final Integer value, final @NonNull NumberRange annotation) {
            switch (annotation.value()) {
                case GREATER_THAN_0 -> {
                    if (value <= 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be greater than 0"
                    );
                }
                case LESS_THAN_0 -> {
                    if (value >= 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be less than 0"
                    );
                }
                case GREATER_THAN_OR_EQUAL_TO_0 -> {
                    if (value < 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be greater than or equal to 0"
                    );
                }
                case LESS_THAN_OR_EQUAL_TO_0 -> {
                    if (value > 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be less than or equal to 0"
                    );
                }
                case SPECIFIC_NUMBER -> {
                    if (value < annotation.from() || value > annotation.to())
                        throw new ConfigurationValidationException(
                            fieldName, value, "must be between " + annotation.from() + " and " + annotation.to()
                        );
                }
            }
        }
    }
}
