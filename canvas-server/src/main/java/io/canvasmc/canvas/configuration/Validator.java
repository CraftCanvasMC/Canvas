package io.canvasmc.canvas.configuration;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Map;
import org.jspecify.annotations.NonNull;

public class Validator {

    @SuppressWarnings("unchecked")
    private static void validateField(
        final @NonNull Field declaredField,
        final @NonNull Class<?> classInsideOf,
        final @NonNull Object obj,
        final @NonNull Map<String, Part.OptionDefinition> partDefinitions
    ) throws IllegalAccessException {
        // skip any non-public or final fields
        if (
            !declaredField.accessFlags().contains(AccessFlag.PUBLIC) ||
                declaredField.accessFlags().contains(AccessFlag.FINAL)
        ) {
            return;
        }

        Class<?> fieldType = declaredField.getType();

        // recurse into nested classes that extend Part
        for (Class<?> nested : classInsideOf.getDeclaredClasses()) {
            if (nested.equals(fieldType) && Part.class.isAssignableFrom(nested)) {
                Object nestedObj = declaredField.get(obj);
                if (nestedObj == null) break;

                Map<String, Part.OptionDefinition> nestedPartDefs =
                    Part.harvest((Class<? extends Part>) nested);

                for (Field nestedField : nested.getDeclaredFields()) {
                    validateField(nestedField, nested, nestedObj, nestedPartDefs);
                }

                break;
            }
        }

        // run Part-based validators for this field
        final Part.OptionDefinition definition = partDefinitions.get(declaredField.getName());
        if (definition.validations.isEmpty()) return;

        if (fieldType.isArray()) {
            final Object arrayObj = declaredField.get(obj);
            if (arrayObj != null) {
                final int length = Array.getLength(arrayObj);
                for (int i = 0; i < length; i++) {
                    runPartValidations(definition, declaredField.getName() + "[" + i + "]", Array.get(arrayObj, i));
                }
            }
        }
        else {
            runPartValidations(definition, declaredField.getName(), declaredField.get(obj));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runPartValidations(
        final Part.@NonNull OptionDefinition definition,
        final @NonNull String fieldName,
        final Object value
    ) {
        for (final Part.Validation validation : definition.validations) {
            try {
                validation.validate(value);
            } catch (Throwable thrown) {
                throw new RuntimeException(
                    "Validation failed for field '" + fieldName + "': " + thrown.getMessage(), thrown
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void validateObject(final @NonNull Object obj) {
        Class<?> oClazz = obj.getClass();

        if (!Part.class.isAssignableFrom(oClazz)) {
            throw new IllegalArgumentException(
                "Object of class '" + oClazz.getName() + "' does not extend Part"
            );
        }

        Map<String, Part.OptionDefinition> partDefinitions =
            Part.harvest((Class<? extends Part>) oClazz);

        for (final Field declaredField : oClazz.getDeclaredFields()) {
            try {
                validateField(declaredField, oClazz, obj, partDefinitions);
            } catch (IllegalAccessException iae) {
                throw new RuntimeException("Unable to access field '" + declaredField.getName() + "'", iae);
            }
        }
    }
}
