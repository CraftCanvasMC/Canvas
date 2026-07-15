package io.canvasmc.canvas.configuration;

import static org.junit.jupiter.api.Assertions.fail;

import io.canvasmc.canvas.GlobalConfiguration;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Disabled
@Tag("Configuration")
class GlobalConfigurationDocumentationTest {

    private static final boolean DEBUG = Boolean.parseBoolean(System.getenv("CANVAS_TEST_DEBUG"));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testAllConfigurationOptionsHaveDocumentation() throws Exception {
        final List<String> errors = new ArrayList<>();

        verifyPart(GlobalConfiguration.class, errors);

        if (!errors.isEmpty()) {
            errors.sort(String::compareTo);

            final StringBuilder message = new StringBuilder()
                .append("There ")
                .append(errors.size() == 1 ? "is " : "are ")
                .append(errors.size())
                .append(" configuration option")
                .append(errors.size() == 1 ? "" : "s")
                .append(" failing validation:\n");

            for (final String error : errors) {
                message.append("\t").append(error).append("\n");
            }

            fail(message.toString());
        }
    }

    private static void verifyPart(
        final Class<? extends Part> partClass,
        final List<String> errors
    ) throws Exception {
        final Part part = partClass.getDeclaredConstructor().newInstance();

        for (final Field field : partClass.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())
                || Modifier.isStatic(field.getModifiers())
                || field.isSynthetic()) {
                continue;
            }

            if (Part.class.isAssignableFrom(field.getType())) {
                @SuppressWarnings("unchecked")
                final Class<? extends Part> nested = (Class<? extends Part>) field.getType();

                verifyPart(nested, errors);
                continue;
            }

            if (skipDocumentationCheck(field, partClass, errors)) {
                continue;
            }

            final String name = field.getName();

            if (!part.definitionOverrides.containsKey(name)) {
                errors.add(
                    partClass.getName() + "." + name
                        + " is missing the required option(\"" + name + "\") definition"
                );
            } else if (part.definitionOverrides.get(name).commentStyle() == null) {
                errors.add(
                    partClass.getName() + "." + name
                        + " is missing the required docs(...) documentation definition"
                );
            }
        }
    }

    private static boolean skipDocumentationCheck(
        final Field field,
        final Class<?> partClass,
        final List<String> errors
    ) {
        final Undocumented undocumented =
            field.isAnnotationPresent(Undocumented.class)
                ? field.getAnnotation(Undocumented.class)
                : partClass.getAnnotation(Undocumented.class);

        if (undocumented == null) {
            return false;
        }

        if (undocumented.value().isBlank()) {
            errors.add(
                partClass.getName() + "." + field.getName()
                    + " is annotated with @Undocumented but has an empty \"because\" field"
            );
        } else if (DEBUG) {
            System.out.printf(
                "Skipping documentation check for %s.%s: %s%n",
                partClass.getName(),
                field.getName(),
                undocumented.value()
            );
        }

        return true;
    }
}
