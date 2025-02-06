package io.canvasmc.canvas.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

@FunctionalInterface
public interface AnnotationValidationProvider<T extends Annotation> {
    boolean validate(String fullKey, Field field, T annotation, Object value) throws ValidationException;
}
