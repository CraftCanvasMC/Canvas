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
import java.util.function.Function;
import net.minecraft.util.FileUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

public abstract class Part {

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

    protected void save(final @NonNull Path path) {
        try {
            if (path.getParent() != null) {
                FileUtil.createDirectoriesSafe(path.getParent());
            }
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Unable to save because save target doesn't exist. Deleted?");
            }

            final Node fileNode = ConfigurationProvider.composeFileNode(path.toAbsolutePath());
            if (fileNode == null) {
                throw new IllegalStateException("Cannot save: no file node is present. Was this Part loaded via buildSolidConfiguration?");
            }

            // represent the live object so we have active values in node form
            // we don't care about comments, we don't change those
            final Node freshNode = ConfigurationProvider.represent(this);

            // we just need to push the values in memory to the file node, that's it
            // so we don't need to modify ANYTHING else
            if (fileNode instanceof MappingNode fileMapping
                && freshNode instanceof MappingNode freshMapping) {
                ConfigurationProvider.mergeNodes(fileMapping, freshMapping, "");
            }

            ConfigurationProvider.write(path, fileNode, null, true);
        } catch (IOException ioe) {
            throw new RuntimeException("Could not save Part to " + path, ioe);
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
