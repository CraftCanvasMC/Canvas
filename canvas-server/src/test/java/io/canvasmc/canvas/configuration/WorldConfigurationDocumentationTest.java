package io.canvasmc.canvas.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import io.canvasmc.canvas.WorldConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Documentation")
class WorldConfigurationDocumentationTest {

    private static final boolean DEBUG = Boolean.parseBoolean(System.getenv("CANVAS_TEST_DEBUG"));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testAllConfigurationOptionsHaveDocumentation() throws Exception {
        verifyPart(WorldConfig.class);
    }

    private static void verifyPart(final Class<? extends Part> partClass) throws Exception {
        final Part part = partClass.getDeclaredConstructor().newInstance();

        for (final Field field : partClass.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }

            if (Part.class.isAssignableFrom(field.getType())) {
                @SuppressWarnings("unchecked")
                final Class<? extends Part> nested = (Class<? extends Part>) field.getType();
                verifyPart(nested);
                continue;
            }

            if (skipDocumentationCheck(field, partClass)) {
                continue;
            }

            verifyDocumentation(part, partClass, field);
        }
    }

    private static boolean skipDocumentationCheck(final Field field, final Class<?> partClass) {
        final Undocumented undocumented = field.getAnnotation(Undocumented.class);

        if (undocumented == null) {
            return false;
        }

        assertFalse(
            undocumented.because().isBlank(),
            () -> field + " is annotated with @Undocumented but has an empty \"because\" field!"
        );

        if (DEBUG) {
            System.out.printf(
                "Skipping documentation check for %s.%s: %s%n",
                partClass.getSimpleName(),
                field.getName(),
                undocumented.because()
            );
        }

        return true;
    }

    private static void verifyDocumentation(
        final Part part,
        final Class<?> partClass,
        final Field field
    ) {
        final String name = field.getName();

        assertTrue(
            part.definitionOverrides.containsKey(name),
            () -> partClass.getName() + "." + name + " is missing the required option(\"" + name + "\") definition"
        );

        final Part.OptionDefinition definition = part.definitionOverrides.get(name);

        assertNotNull(
            definition.commentStyle(),
            () -> partClass.getName() + "." + name + " is missing the required docs(...) documentation definition"
        );
    }
}
