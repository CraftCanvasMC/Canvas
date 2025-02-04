package io.canvasmc.canvas.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Comment {
    String[] value();

    public boolean breakLineBefore() default false;
}
