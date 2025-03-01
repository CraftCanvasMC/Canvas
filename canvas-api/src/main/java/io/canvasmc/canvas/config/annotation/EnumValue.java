package io.canvasmc.canvas.config.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {
    @SuppressWarnings("rawtypes") Class<? extends Enum> enumValue();
}
