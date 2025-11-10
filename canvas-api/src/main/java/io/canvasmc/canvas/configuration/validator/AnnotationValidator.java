package io.canvasmc.canvas.configuration.validator;

import io.canvasmc.canvas.configuration.jankson.JsonElement;
import java.lang.annotation.Annotation;

public interface AnnotationValidator<A extends Annotation> {
    ValidationResult read(A annotation, JsonElement element);

    Class<A> typeOf();
}
