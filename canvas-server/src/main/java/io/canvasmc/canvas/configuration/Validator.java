package io.canvasmc.canvas.configuration;

import io.canvasmc.canvas.configuration.markers.ValidationType;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import org.jspecify.annotations.NonNull;

public class Validator {

    @SuppressWarnings("unchecked")
    private static void validateField(
        final @NonNull Field declaredField, final @NonNull Class<?> classInsideOf, final @NonNull Object obj
    ) throws IllegalAccessException, InstantiationException {
        // skip any non-public or final fields
        if (
            !declaredField.accessFlags().contains(AccessFlag.PUBLIC) ||
                declaredField.accessFlags().contains(AccessFlag.FINAL)
        ) {
            return;
        }

        // now we check the field if it has annotations and it's children if it's an inner class

        Class<?> fieldType = declaredField.getType();

        // check if class type is nested
        for (Class<?> nested : classInsideOf.getDeclaredClasses()) {
            if (nested.equals(fieldType)) {

                Object nestedObj = declaredField.get(obj);

                // check nested fields
                for (Field nestedField : nested.getDeclaredFields()) {
                    validateField(nestedField, nested, nestedObj);
                }

                break;
            }
        }

        // all nested are validated, check annotations in field
        for (final Annotation declaredAnnotation : declaredField.getDeclaredAnnotations()) {
            if (declaredAnnotation.annotationType().isAnnotationPresent(ValidationType.class)) {
                // this is a validating annotation
                ValidationType validationType = declaredAnnotation.annotationType().getAnnotation(ValidationType.class);
                Class<? extends FieldValidator> fieldValidatorClass = validationType.value();
                fieldValidatorClass.newInstance().validate(
                    declaredField.getName(), declaredField.get(obj), declaredAnnotation
                );
            }
        }
    }

    public static void validateObject(final @NonNull Object obj) {
        Class<?> oClazz = obj.getClass();
        for (final Field declaredField : oClazz.getDeclaredFields()) {
            try {
                validateField(declaredField, oClazz, obj);
            } catch (IllegalAccessException iae) {
                throw new RuntimeException("Unable to access field", iae);
            } catch (InstantiationException ie) {
                throw new RuntimeException("Unable to instantiate class", ie);
            }
        }
    }
}
