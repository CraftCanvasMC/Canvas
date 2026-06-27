package io.canvasmc.canvas.configuration;

import io.canvasmc.canvas.configuration.validation.NumberComparison;
import io.canvasmc.canvas.configuration.validation.StringValidation;
import io.canvasmc.canvas.util.CanonicalReference;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.nodes.Node;

public class Part {

    // whenever the reference is null, the yaml is empty
    final CanonicalReference<Node> node = new CanonicalReference<>();
    final CanonicalReference<Function<String, @Nullable OptionDefinition>> processor = new CanonicalReference<>();
    // we don't need or care about this being linked tbh
    final Object2ObjectOpenHashMap<String, OptionDefinition> definitionOverrides = new Object2ObjectOpenHashMap<>();
    {
        // set the default return value to a blank definition
        definitionOverrides.defaultReturnValue(new OptionDefinition());
    }

    static @NonNull Map<String, OptionDefinition> harvest(Class<? extends Part> clazz) {
        try {

            Part temp = clazz.getDeclaredConstructor().newInstance();
            Function<String, @Nullable OptionDefinition> resolver = temp.processor.valueSafe();

            Object2ObjectOpenHashMap<String, OptionDefinition> result = new Object2ObjectOpenHashMap<>();
            result.defaultReturnValue(new OptionDefinition());

            for (final Field field : clazz.getDeclaredFields()) {
                final String key = field.getName();
                final OptionDefinition value = resolver == null ? temp.definitionOverrides.get(key) : resolver.apply(key);
                result.put(
                    key,
                    // the resolver can return null, so we should ensure to fill with a non-null value
                    value == null ? temp.definitionOverrides.get(key) : value
                );
            }

            return result;
        } catch (Throwable thrown) {
            throw new RuntimeException("Could not instantiate Part subclass " + clazz.getName() + ", ensure it has a public no-arg constructor", thrown);
        }
    }

    public Node getYamlNode() {
        return node.valueSafe();
    }

    public void stream(final Function<String, @Nullable OptionDefinition> processor) {
        this.processor.setValue(processor);
    }

    public OptionDefinition option(String target) {

        // target must exist or we throw

        try {
            getClass().getDeclaredField(target);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Target '" + target + "' doesn't exist");
        }

        final OptionDefinition def = new OptionDefinition();

        // make sure we propagate in definitions
        definitionOverrides.put(target, def);

        return def;
    }

    public String writeToString() {
        if (getYamlNode() == null) throw new IllegalArgumentException("Cannot serialize this instance. Yaml is null");
        final StringWriter stringWriter = new StringWriter();
        ConfigurationProvider.serialize(getYamlNode(), stringWriter);
        return stringWriter.toString();
    }

    public void serializeInternalNode(final Path path) {
        if (getYamlNode() == null) throw new IllegalArgumentException("Cannot serialize this instance. Yaml is null");
        try {
            ConfigurationProvider.write(
                path, getYamlNode(), null, Files.exists(path)
            );
        } catch (IOException ioe) {
            throw new RuntimeException("Couldn't serialize internal node", ioe);
        }
    }

    public interface Validation<T> {
        void validate(T t);
    }

    public static class OptionDefinition {
        protected final List<Validation<?>> validations = new LinkedList<>();
        private Style style;

        public @Nullable Style commentStyle() {
            return this.style;
        }

        public OptionDefinition docs(Style style) {
            this.style = style;
            return this;
        }

        public OptionDefinition docs(String... toBeWrapped) {
            return docs(Style.wrap(toBeWrapped));
        }

        public OptionDefinition docs(String toBeWrapped) {
            return docs(Style.wrap(toBeWrapped));
        }

        public OptionDefinition greaterThan(float val) {
            return validation(new NumberComparison(NumberComparison.Type.GREATER_THAN, val));
        }

        public OptionDefinition greaterThanOrEqualTo(float val) {
            return validation(new NumberComparison(NumberComparison.Type.GREATER_THAN_OR_EQUAL_TO, val));
        }

        public OptionDefinition lessThan(float val) {
            return validation(new NumberComparison(NumberComparison.Type.LESS_THAN, val));
        }

        public OptionDefinition lessThanOrEqualTo(float val) {
            return validation(new NumberComparison(NumberComparison.Type.LESS_THAN_OR_EQUAL_TO, val));
        }

        public OptionDefinition equals(float val) {
            return validation(new NumberComparison(NumberComparison.Type.EQUAL, val));
        }

        public OptionDefinition between(float min, float max) {
            return validation(new NumberComparison(NumberComparison.Type.BETWEEN, min, max));
        }

        public OptionDefinition greedyString() {
            return validation(new StringValidation(StringValidation.StringType.GREEDY_PHRASE));
        }

        public OptionDefinition identifier() {
            return validation(new StringValidation(StringValidation.StringType.IDENTIFIER));
        }

        public OptionDefinition word() {
            return validation(new StringValidation(StringValidation.StringType.SINGLE_WORD));
        }

        public OptionDefinition validation(Validation<?> validation) {
            validations.add(validation);
            return this;
        }
    }
}
