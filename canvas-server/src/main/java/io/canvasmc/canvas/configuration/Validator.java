package io.canvasmc.canvas.configuration;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Map;

public class Validator {

    @SuppressWarnings("unchecked")
    public static void validateObject(final Object object) {
        final Class<?> objectClass = object.getClass();

        if (!Part.class.isAssignableFrom(objectClass)) {
            throw new IllegalArgumentException(
                "Object of class '" + objectClass.getName() + "' does not extend Part"
            );
        }

        final Map<String, Part.OptionDefinition> harvested =
            Part.harvest((Class<? extends Part>) objectClass);

        for (final Field declaredField : objectClass.getDeclaredFields()) {
            try {
                validateField(declaredField, objectClass, object, harvested);
            } catch (final IllegalAccessException iae) {
                throw new RuntimeException("Unable to access field '" + declaredField.getName() + "'", iae);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateField(
        final Field declaredField,
        final Class<?> classInsideOf,
        final Object obj,
        final Map<String, Part.OptionDefinition> partDefinitions
    ) throws IllegalAccessException {
        // skip any final fields
        if (declaredField.accessFlags().contains(AccessFlag.FINAL)) {
            return;
        }

        // make accessible just in case
        declaredField.setAccessible(true);

        final Class<?> fieldType = declaredField.getType();

        // recurse into nested classes that extend Part
        for (final Class<?> nested : classInsideOf.getDeclaredClasses()) {
            if (nested.equals(fieldType) && Part.class.isAssignableFrom(nested)) {
                final Object nestedObj = declaredField.get(obj);
                if (nestedObj == null) break;

                final Map<String, Part.OptionDefinition> harvested =
                    Part.harvest((Class<? extends Part>) nested);

                for (final Field nestedField : nested.getDeclaredFields()) {
                    validateField(nestedField, nested, nestedObj, harvested);
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
        final Part.OptionDefinition definition,
        final String fieldName,
        final Object value
    ) {
        for (final Part.Validation validation : definition.validations) {
            try {
                validation.validate(value);
            } catch (final Throwable thrown) {
                throw new RuntimeException(
                    "Validation failed for field '" + fieldName + "': " + thrown.getMessage(), thrown
                );
            }
        }
    }
}
