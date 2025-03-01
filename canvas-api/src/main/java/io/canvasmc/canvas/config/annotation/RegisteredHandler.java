package io.canvasmc.canvas.config.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.jetbrains.annotations.NotNull;

@Retention(RetentionPolicy.RUNTIME)
public @interface RegisteredHandler {
    @NotNull String value();
}
