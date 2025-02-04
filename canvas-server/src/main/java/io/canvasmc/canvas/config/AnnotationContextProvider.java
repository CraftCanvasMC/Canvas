package io.canvasmc.canvas.config;

import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface AnnotationContextProvider<T extends Annotation> {
    void apply(StringWriter yamlWriter, String indent, String fullKey, Field field, @NotNull T annotation);
}
