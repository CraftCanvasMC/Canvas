package io.canvasmc.canvas.configuration.validation;

import io.canvasmc.canvas.configuration.Part;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public record StringValidation(StringType type) implements Part.Validation<String> {

    @Override
    public void validate(final @Nullable String str) {
        if (str == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        switch (type) {
            case SINGLE_WORD -> {
                if (str.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Single word must not be empty"
                    );
                }
                if (str.chars().anyMatch(Character::isWhitespace)) {
                    throw new IllegalArgumentException(
                        "Single word must not contain whitespace, got: \"" + str + "\""
                    );
                }
            }
            case GREEDY_PHRASE -> {
                // accepts everything so we don't do anything
            }
            case IDENTIFIER -> {
                // copied and modified from NamespacedKey.fromString
                if (str.isEmpty() || str.length() > Short.MAX_VALUE)
                    throw new IllegalArgumentException("Insufficient bounds");

                final String[] parts = str.split(":", 3);
                if (parts.length > 2) {
                    throw new IllegalArgumentException("Too many colons");
                }

                if (parts.length == 1) {
                    // no colons, only path, namespace is 'minecraft'
                    assertValidPath(parts[0]);
                    // if it didn't throw, this is a valid path! yay!
                    // the namespace would be 'minecraft' by default, so this is fine to pass
                }
                else if (parts.length == 2) {
                    // this has a namespace and path
                    final String namespace = parts[0];
                    final String path = parts[1];
                    assertValidNamespace(namespace);
                    assertValidPath(path);
                    // if it didn't throw the namespace and path are valid
                }
            }
        }
    }

    private static void assertValidPath(final String path) {
        if (path.isEmpty() || !Identifier.isValidPath(path)) {
            throw new IllegalArgumentException(
                // iae copied && modified from Identifier#assertValidPath
                "Non [a-z0-9/._-] character in path of location: " + StringUtils.normalizeSpace(path)
            );
        }
    }

    private static void assertValidNamespace(final String namespace) {
        if (namespace.isEmpty() || !Identifier.isValidNamespace(namespace)) {
            throw new IllegalArgumentException(
                // iae copied from Identifier#assertValidNamespace
                "Non [a-z0-9_.-] character in namespace of identifier: " + namespace
            );
        }
    }

    public enum StringType {
        SINGLE_WORD("word", "words_with_underscores"),
        GREEDY_PHRASE("word", "words with spaces", "\"and symbols\""),
        IDENTIFIER("minecraft:test", "just_the_key", "canvas:example");

        private final Collection<String> examples;

        StringType(final String... examples) {
            this.examples = Arrays.asList(examples);
        }

        public Collection<String> getExamples() {
            return examples;
        }
    }
}
