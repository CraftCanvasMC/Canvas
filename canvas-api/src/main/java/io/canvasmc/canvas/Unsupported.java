package io.canvasmc.canvas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a class, method, or constructor as unsupported and to not be used. Usages of the defined classes or methods
 * may result in issues like exceptions, unknown bugs, or may not even be implemented or may be disabled. It is not
 * recommended to use these in production.
 * <p>
 * <b>Do not use anything annotated with this.</b>
 *
 * @author dueris
 * @implNote If a class is annotated with this, it is to be assumed the <b>full</b> class is unsupported including
 *     the methods and functions provided or declared by the class
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface Unsupported {

    /**
     * The reason for the unsupported annotation being placed
     */
    String reason() default "";
}
