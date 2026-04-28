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

    float from() default Float.MIN_VALUE;

    float to() default Float.MAX_VALUE;

    enum Type {
        GREATER_THAN_0,
        LESS_THAN_0,
        GREATER_THAN_OR_EQUAL_TO_0,
        LESS_THAN_OR_EQUAL_TO_0,
        SPECIFIC_NUMBER
    }

    // note that we use floats internally here, but any number type can be realistically passed into the validator
    class Validator implements FieldValidator<NumberRange, Number> {
        @Override
        public void validate(final String fieldName, final Number value, final @NonNull NumberRange annotation) {
            switch (annotation.value()) {
                case GREATER_THAN_0 -> {
                    if (value.floatValue() <= 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be greater than 0"
                    );
                }
                case LESS_THAN_0 -> {
                    if (value.floatValue() >= 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be less than 0"
                    );
                }
                case GREATER_THAN_OR_EQUAL_TO_0 -> {
                    if (value.floatValue() < 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be greater than or equal to 0"
                    );
                }
                case LESS_THAN_OR_EQUAL_TO_0 -> {
                    if (value.floatValue() > 0) throw new ConfigurationValidationException(
                        fieldName, value, "must be less than or equal to 0"
                    );
                }
                case SPECIFIC_NUMBER -> {
                    if (value.floatValue() < annotation.from() || value.floatValue() > annotation.to())
                        throw new ConfigurationValidationException(
                            fieldName, value, "must be between " + annotation.from() + " and " + annotation.to()
                        );
                }
            }
        }
    }
}
