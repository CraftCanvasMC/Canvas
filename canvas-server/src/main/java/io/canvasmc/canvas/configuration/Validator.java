package io.canvasmc.canvas.configuration;

import io.canvasmc.canvas.configuration.markers.ValidationType;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Array;
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
                final ValidationType validationType = declaredAnnotation.annotationType().getAnnotation(ValidationType.class);
                final Class<? extends FieldValidator> fieldValidatorClass = validationType.value();

                // we need to test if this is an array type or if this is a generic type

                if (fieldType.isArray()) {
                    // run it as an array
                    final Object arrayObj = declaredField.get(obj);
                    if (arrayObj != null) {
                        final int length = Array.getLength(arrayObj);
                        for (int i = 0; i < length; i++) {
                            // the reason we do it like this is that if we try and cast the array to an Object[]
                            // we get a class cast exception which nobody likes
                            fieldValidatorClass.newInstance().validate(
                                declaredField.getName() + "[" + i + "]", Array.get(arrayObj, i), declaredAnnotation
                            );
                        }
                    }
                }
                else {
                    // not an array, just get the declared field value and pass it through the validator
                    fieldValidatorClass.newInstance().validate(
                        declaredField.getName(), declaredField.get(obj), declaredAnnotation
                    );
                }
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
