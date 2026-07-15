package io.canvasmc.canvas.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Undocumented {

    /**
     * Why this option is exempt from the documentation requirement.
     */
    String value();
}
