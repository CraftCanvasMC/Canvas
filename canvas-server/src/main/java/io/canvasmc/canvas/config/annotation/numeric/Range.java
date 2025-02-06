package io.canvasmc.canvas.config.annotation.numeric;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Range {
    int from();
    int to();
    boolean inclusive() default false;
}
