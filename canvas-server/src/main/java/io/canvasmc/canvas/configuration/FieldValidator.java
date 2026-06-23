package io.canvasmc.canvas.configuration;

import java.lang.annotation.Annotation;

public interface FieldValidator<A extends Annotation, T> {
    void validate(String fieldName, T value, A annotation);
}
