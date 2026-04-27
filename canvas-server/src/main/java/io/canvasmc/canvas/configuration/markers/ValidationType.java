package io.canvasmc.canvas.configuration.markers;

import io.canvasmc.canvas.configuration.FieldValidator;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface ValidationType {
    Class<? extends FieldValidator<?, ?>> value();
}
