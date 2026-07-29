package io.canvasmc.canvas.configuration;

import io.canvasmc.canvas.configuration.validation.NumberComparison;
import io.canvasmc.canvas.configuration.validation.StringValidation;
import io.canvasmc.canvas.util.CanonicalReference;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.util.FileUtil;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

public abstract class Part {

    final CanonicalReference<BiConsumer<String, OptionDefinition>> processor = new CanonicalReference<>();
    // we don't need or care about this being linked tbh
    final Object2ObjectOpenHashMap<String, OptionDefinition> definitionOverrides = new Object2ObjectOpenHashMap<>();
    {
        // set the default return value to a blank definition
        definitionOverrides.defaultReturnValue(new OptionDefinition());
    }

    static Map<String, OptionDefinition> harvest(final Class<? extends Part> clazz) {
        try {
            final Part temp = clazz.getDeclaredConstructor().newInstance();
            final BiConsumer<String, OptionDefinition> resolver = temp.processor.valueSafe();

            final Object2ObjectOpenHashMap<String, OptionDefinition> result = new Object2ObjectOpenHashMap<>();
            result.defaultReturnValue(new OptionDefinition());

            for (final Field field : clazz.getDeclaredFields()) {
                final String key = field.getName();
                OptionDefinition current =
                    temp.definitionOverrides.computeIfAbsent(key, _ -> new OptionDefinition());
                if (resolver != null) {
                    resolver.accept(key, current);
                }
                result.put(key, current);
            }

            return result;
        } catch (final Throwable thrown) {
            throw new RuntimeException("Could not instantiate Part subclass " + clazz.getName() + ", ensure it has a public no-arg constructor", thrown);
        }
    }

    public void stream(final BiConsumer<String, OptionDefinition> processor) {
        this.processor.setValue(processor);
    }

    public OptionDefinition option(final String target) {

        // target must exist or we throw

        try {
            getClass().getDeclaredField(target);
        } catch (final NoSuchFieldException nsfe) {
            throw new IllegalArgumentException("Target '" + target + "' doesn't exist");
        }

        final OptionDefinition def = new OptionDefinition();

        // make sure we propagate in definitions
        definitionOverrides.put(target, def);

        return def;
    }

    protected void save(final Path path) {
        try {
            if (path.getParent() != null) {
                FileUtil.createDirectoriesSafe(path.getParent());
            }
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Unable to save because save target doesn't exist. Deleted?");
            }

            // shouldn't be null... realistically
            final Node fileNode = ConfigurationProvider.composeFromFile(path.toAbsolutePath());
            Objects.requireNonNull(fileNode, "Composed file cannot be null. Did someone empty the config contents?");

            // represent the live object so we have active values in node form
            // we don't care about comments, we don't change those
            final Node freshNode = ConfigurationProvider.represent(this);

            // we just need to push the values in memory to the file node, that's it
            // so we don't need to modify ANYTHING else
            if (fileNode instanceof MappingNode fileMapping
                && freshNode instanceof MappingNode freshMapping) {
                ConfigurationProvider.mergeNodes(fileMapping, freshMapping, "");
            }

            ConfigurationProvider.write(path, fileNode, true);
        } catch (final IOException ioe) {
            throw new RuntimeException("Could not save Part to " + path, ioe);
        }
    }

    public interface Validation<T> {
        void validate(@Nullable T t);
    }

    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public static class OptionDefinition {

        @Nullable
        private Style style = null;
        protected final List<Validation<?>> validations = new LinkedList<>();

        @Nullable
        public Style commentStyle() {
            return this.style;
        }

        public OptionDefinition docs(final Style style) {
            if (this.style == null) {
                this.style = style;
            } else {
                this.style.append(style);
            }
            return this;
        }

        public OptionDefinition docs(final String... toBeWrapped) {
            return docs(Style.wrap(toBeWrapped));
        }

        public OptionDefinition docs(final String toBeWrapped) {
            return docs(Style.wrap(toBeWrapped));
        }

        public OptionDefinition greaterThan(final float val) {
            return validation(new NumberComparison(NumberComparison.Type.GREATER_THAN, val));
        }

        public OptionDefinition greaterThanOrEqualTo(final float val) {
            return validation(new NumberComparison(NumberComparison.Type.GREATER_THAN_OR_EQUAL_TO, val));
        }

        public OptionDefinition lessThan(final float val) {
            return validation(new NumberComparison(NumberComparison.Type.LESS_THAN, val));
        }

        public OptionDefinition lessThanOrEqualTo(final float val) {
            return validation(new NumberComparison(NumberComparison.Type.LESS_THAN_OR_EQUAL_TO, val));
        }

        public OptionDefinition equals(final float val) {
            return validation(new NumberComparison(NumberComparison.Type.EQUAL, val));
        }

        public OptionDefinition between(final float min, final float max) {
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

        public OptionDefinition validation(final Validation<?> validation) {
            validations.add(validation);
            return this;
        }
    }
}
